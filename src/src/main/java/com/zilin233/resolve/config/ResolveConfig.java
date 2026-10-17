package com.zilin233.resolve.config;

import com.google.gson.annotations.SerializedName;

/**
 * Plain data holder for everything the player can tune. Serialised to
 * {@code .minecraft/config/resolve.json} with Gson.
 *
 * <p>Field names are stable: renaming one silently resets that option for existing users, so treat
 * them as a file format rather than as Java identifiers.</p>
 */
public class ResolveConfig {

	// ---------------------------------------------------------------------------------------
	// Master switch
	// ---------------------------------------------------------------------------------------

	/** When false Resolve does nothing at all and the vanilla pipeline is untouched. */
	@SerializedName("enabled")
	public boolean enabled = true;

	// ---------------------------------------------------------------------------------------
	// Which chunks are considered "distant"
	// ---------------------------------------------------------------------------------------

	/**
	 * First chunk ring (measured from the player, in chunks) that counts as "distant".
	 *
	 * <p>The reduced resolution is applied to the whole world render target, so this value does not
	 * change how many pixels are shaded. It is used to decide whether reducing the resolution is
	 * worth it at all: if the configured render distance is not further away than this ring, there
	 * is nothing "distant" on screen and Resolve leaves the frame at full resolution.</p>
	 */
	@SerializedName("far_start_chunk")
	public int farStartChunk = 6;

	// ---------------------------------------------------------------------------------------
	// Resolution
	// ---------------------------------------------------------------------------------------

	/** Render resolution used while <em>not</em> zooming, as a percentage of the window size. */
	@SerializedName("far_scale_percent")
	public float farScalePercent = 80.0f;

	/** Render resolution used while zooming, as a percentage of the window size. */
	@SerializedName("zoom_scale_percent")
	public float zoomScalePercent = 100.0f;

	/** Hard lower bound. Guards against a typo turning the screen into a handful of pixels. */
	@SerializedName("min_scale_percent")
	public float minScalePercent = 25.0f;

	// ---------------------------------------------------------------------------------------
	// Zoom detection
	// ---------------------------------------------------------------------------------------

	/** When false the scale never changes with zoom; {@code far_scale_percent} always applies. */
	@SerializedName("zoom_enabled")
	public boolean zoomEnabled = true;

	/** Detect the vanilla spyglass ({@code isUsingItem()} + {@code Items.SPYGLASS}). */
	@SerializedName("detect_spyglass")
	public boolean detectSpyglass = true;

	/** Detect Zoomify through its own API (reflection, no hard dependency). */
	@SerializedName("detect_zoomify")
	public boolean detectZoomify = true;

	/**
	 * Generic detection: any key binding whose id mentions "zoom" counts as a zoom key. This is what
	 * makes other zoom mods work without Resolve knowing about them.
	 */
	@SerializedName("detect_keybind")
	public boolean detectKeybind = true;

	/**
	 * Universal fallback: compare the FOV actually used for the world against the FOV in the video
	 * settings. Anything that narrows the view is treated as zoom, including mods Resolve has never
	 * heard of.
	 */
	@SerializedName("detect_fov")
	public boolean detectFov = true;

	/** World FOV must drop below {@code baseFov * threshold} to count as zooming. */
	@SerializedName("fov_zoom_threshold")
	public float fovZoomThreshold = 0.90f;

	// ---------------------------------------------------------------------------------------
	// Timing
	// ---------------------------------------------------------------------------------------

	/**
	 * When true the scale eases between the two values instead of snapping. Smoothing costs a few
	 * render-target reallocations per transition, so it is off by default.
	 */
	@SerializedName("smooth_transition")
	public boolean smoothTransition = false;

	/** Duration of a smoothed transition, in milliseconds. */
	@SerializedName("transition_ms")
	public int transitionMs = 200;

	/**
	 * Number of discrete steps a smoothed transition is quantised into. Lower means fewer
	 * reallocations but a more visible staircase.
	 */
	@SerializedName("transition_steps")
	public int transitionSteps = 4;

	/**
	 * How long zoom must stay released before the scale drops back down. Stops a shaky mouse button
	 * from thrashing the render target.
	 */
	@SerializedName("release_delay_ms")
	public int releaseDelayMs = 150;

	// ---------------------------------------------------------------------------------------
	// Output
	// ---------------------------------------------------------------------------------------

	/** LINEAR gives a smooth upscale; NEAREST is crisper but shows blocky pixels. */
	@SerializedName("linear_filter")
	public boolean linearFilter = true;

	/** Behaviour while an Iris shader pack is active. */
	@SerializedName("shader_mode")
	public ShaderMode shaderMode = ShaderMode.AUTO;

	/** Adds a line to the F3 debug screen showing what Resolve is currently doing. */
	@SerializedName("debug_hud")
	public boolean debugHud = false;

	// ---------------------------------------------------------------------------------------

	/** Clamps every value into a sane range. Called after loading and after every edit. */
	public void sanitize() {
		minScalePercent = clamp(minScalePercent, 10.0f, 100.0f);
		farScalePercent = clamp(farScalePercent, minScalePercent, 100.0f);
		zoomScalePercent = clamp(zoomScalePercent, minScalePercent, 100.0f);
		fovZoomThreshold = clamp(fovZoomThreshold, 0.05f, 0.99f);

		farStartChunk = clamp(farStartChunk, 0, 64);
		transitionMs = clamp(transitionMs, 0, 2000);
		transitionSteps = clamp(transitionSteps, 1, 32);
		releaseDelayMs = clamp(releaseDelayMs, 0, 2000);

		if (shaderMode == null) {
			shaderMode = ShaderMode.AUTO;
		}
	}

	/** Scale used while not zooming, as a 0..1 multiplier. */
	public float farScale() {
		return farScalePercent / 100.0f;
	}

	/** Scale used while zooming, as a 0..1 multiplier. */
	public float zoomScale() {
		return zoomScalePercent / 100.0f;
	}

	/** True when both configured scales are full resolution, i.e. there is nothing to do. */
	public boolean isNoOp() {
		return !enabled
				|| (farScalePercent >= 99.9f && (!zoomEnabled || zoomScalePercent >= 99.9f));
	}

	public ResolveConfig copy() {
		ResolveConfig c = new ResolveConfig();
		c.enabled = enabled;
		c.farStartChunk = farStartChunk;
		c.farScalePercent = farScalePercent;
		c.zoomScalePercent = zoomScalePercent;
		c.minScalePercent = minScalePercent;
		c.zoomEnabled = zoomEnabled;
		c.detectSpyglass = detectSpyglass;
		c.detectZoomify = detectZoomify;
		c.detectKeybind = detectKeybind;
		c.detectFov = detectFov;
		c.fovZoomThreshold = fovZoomThreshold;
		c.smoothTransition = smoothTransition;
		c.transitionMs = transitionMs;
		c.transitionSteps = transitionSteps;
		c.releaseDelayMs = releaseDelayMs;
		c.linearFilter = linearFilter;
		c.shaderMode = shaderMode;
		c.debugHud = debugHud;
		return c;
	}

	public void copyFrom(ResolveConfig other) {
		enabled = other.enabled;
		farStartChunk = other.farStartChunk;
		farScalePercent = other.farScalePercent;
		zoomScalePercent = other.zoomScalePercent;
		minScalePercent = other.minScalePercent;
		zoomEnabled = other.zoomEnabled;
		detectSpyglass = other.detectSpyglass;
		detectZoomify = other.detectZoomify;
		detectKeybind = other.detectKeybind;
		detectFov = other.detectFov;
		fovZoomThreshold = other.fovZoomThreshold;
		smoothTransition = other.smoothTransition;
		transitionMs = other.transitionMs;
		transitionSteps = other.transitionSteps;
		releaseDelayMs = other.releaseDelayMs;
		linearFilter = other.linearFilter;
		shaderMode = other.shaderMode;
		debugHud = other.debugHud;
		sanitize();
	}

	private static float clamp(float value, float min, float max) {
		return Math.max(min, Math.min(max, value));
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
