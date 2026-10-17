package com.zilin233.resolve.render;

import com.zilin233.resolve.compat.IrisCompat;
import com.zilin233.resolve.compat.ModConflicts;
import com.zilin233.resolve.config.ResolveConfig;
import com.zilin233.resolve.config.ResolveConfigManager;
import com.zilin233.resolve.config.ShaderMode;
import com.zilin233.resolve.debug.ResolveBenchmark;
import com.zilin233.resolve.zoom.ZoomDetector;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Util;

/**
 * Works out which resolution the world should be rendered at this frame.
 *
 * <p>Everything decision-shaped lives here so the mixins stay one-liners and the logic can be
 * reasoned about (and unit tested) without a running game.</p>
 */
public final class ResolutionController {

	/** Minimum gap between two Iris shader reloads, so we never spam an expensive operation. */
	private static final long IRIS_RELOAD_COOLDOWN_MS = 3000L;

	private static float displayedScale = 1.0f;
	private static float appliedScale = 1.0f;

	// Explicit transition bookkeeping: interpolating from the scale we were at when the target
	// changed, so a mid-flight reversal does not jump.
	private static float transitionFrom = 1.0f;
	private static float transitionTo = 1.0f;
	private static long transitionStartMs;

	private static long lastZoomSeenMs;
	private static boolean zoomLatched;

	private static long lastUpdateMs;

	private static float lastIrisScale = -1.0f;
	private static long lastIrisReloadMs;

	private static String status = "";

	private ResolutionController() {
	}

	/** Recomputes the target scale. Call once per frame, before the world is rendered. */
	public static void update(MinecraftClient client) {
		ResolveConfig config = ResolveConfigManager.get();
		long now = Util.getMeasuringTimeMs();
		lastUpdateMs = now;

		if (client == null || client.player == null || client.world == null) {
			setInactive("Not in a world");
			return;
		}

		// TEMPORARY: while the A/B benchmark runs it dictates the scale outright, so that both
		// phases are measured at a known, stable resolution regardless of zoom input.
		if (ResolveBenchmark.isRunning()) {
			float forced = Math.max(0.05f, ResolveBenchmark.forcedScale());
			displayedScale = forced;
			appliedScale = forced;
			transitionFrom = forced;
			transitionTo = forced;
			status = "Benchmark running";
			ResolveBenchmark.onFrame(client);
			return;
		}

		if (config.isNoOp()) {
			setInactive("Disabled");
			return;
		}

		if (ModConflicts.renderScaleActive()) {
			setInactive("Conflicts with RenderScale");
			return;
		}

		boolean shadersActive = IrisCompat.shadersInUse();
		if (shadersActive && config.shaderMode == ShaderMode.OFF_WITH_SHADERS) {
			setInactive("Disabled with shaders");
			return;
		}

		// "Distant chunks" means chunks past far_start_chunk. If the player's render distance does
		// not reach that far there is nothing distant on screen, so the reduced resolution would
		// only cost sharpness and buy nothing.
		int renderDistance = client.options.getViewDistance().getValue();
		if (renderDistance <= config.farStartChunk) {
			setInactive("View distance does not exceed " + config.farStartChunk + " chunks");
			return;
		}

		if (DynamicResolution.isBroken()) {
			setInactive("Render target creation failed");
			return;
		}

		// ---- zoom state, with a release debounce so a twitchy key does not thrash the target ----
		ZoomDetector.Source source = ZoomDetector.detect(client);
		boolean rawZoom = source != ZoomDetector.Source.NONE;

		if (rawZoom) {
			zoomLatched = true;
			lastZoomSeenMs = now;
		} else if (zoomLatched && now - lastZoomSeenMs >= config.releaseDelayMs) {
			zoomLatched = false;
		}

		float floor = config.minScalePercent / 100.0f;
		float target = Math.max(floor, zoomLatched ? config.zoomScale() : config.farScale());

		if (target != transitionTo) {
			transitionFrom = displayedScale;
			transitionTo = target;
			transitionStartMs = now;
		}

		if (!config.smoothTransition || config.transitionMs <= 0) {
			displayedScale = target;
		} else {
			float progress = Math.min(1.0f, (now - transitionStartMs) / (float) config.transitionMs);
			float interpolated = transitionFrom + (transitionTo - transitionFrom) * progress;
			if (progress < 1.0f && config.transitionSteps > 1) {
				// Quantising bounds how often the render target gets reallocated. A fractional step
				// count would defeat the point, so round to whole levels in scale space.
				interpolated = Math.round(interpolated * config.transitionSteps) / (float) config.transitionSteps;
			}
			displayedScale = progress >= 1.0f ? target : interpolated;
		}

		appliedScale = Math.max(floor, Math.min(1.0f, displayedScale));

		maybeReloadIris(config, shadersActive, now);

		status = String.format("Scale %.0f%% (distant %d+ chunks, %s)",
				appliedScale * 100.0f,
				config.farStartChunk,
				rawZoom ? source.label() : (zoomLatched ? "settling" : "not zoomed"));
	}

	/** The scale the world should be rendered at this frame. */
	public static float appliedScale() {
		return appliedScale;
	}

	/** Human readable description of the current state, for the debug HUD. */
	public static String status() {
		return status;
	}

	public static String debugLine() {
		if (status == null || status.isEmpty()) {
			return "[Resolve] inactive";
		}
		String line = "[Resolve] " + status;
		if (DynamicResolution.isEngaged()) {
			line += String.format(" -> %dx%d", DynamicResolution.getScaledWidth(), DynamicResolution.getScaledHeight());
		}
		return line;
	}

	public static void reset() {
		displayedScale = 1.0f;
		appliedScale = 1.0f;
		transitionFrom = 1.0f;
		transitionTo = 1.0f;
		zoomLatched = false;
		status = "";
	}

	private static void setInactive(String reason) {
		displayedScale = 1.0f;
		appliedScale = 1.0f;
		transitionFrom = 1.0f;
		transitionTo = 1.0f;
		zoomLatched = false;
		status = reason;
	}

	/**
	 * In {@link ShaderMode#RELOAD_ON_CHANGE} mode, ask Iris to rebuild its buffers once the applied
	 * scale has settled somewhere new. Rate limited, because a reload recompiles the whole pack.
	 */
	private static void maybeReloadIris(ResolveConfig config, boolean shadersActive, long now) {
		if (!shadersActive || config.shaderMode != ShaderMode.RELOAD_ON_CHANGE) {
			lastIrisScale = appliedScale;
			return;
		}
		if (Math.abs(appliedScale - lastIrisScale) < 0.02f) {
			return;
		}
		if (lastIrisReloadMs != 0 && now - lastIrisReloadMs < IRIS_RELOAD_COOLDOWN_MS) {
			return;
		}
		lastIrisScale = appliedScale;
		lastIrisReloadMs = now;
		IrisCompat.reloadShaders();
	}
}
