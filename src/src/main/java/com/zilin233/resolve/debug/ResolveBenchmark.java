package com.zilin233.resolve.debug;

import com.zilin233.resolve.Resolve;
import com.zilin233.resolve.config.ResolveConfig;
import com.zilin233.resolve.config.ResolveConfigManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A/B frame-time benchmark.
 *
 * <p>Answers "how much does Resolve actually buy me on <em>this</em> machine". It alternates between
 * full resolution (Resolve standing down) and the configured reduced resolution, several times, and
 * measures the wall-clock interval between rendered frames in each phase. The alternation matters:
 * scene, time of day and world state drift over a session, so an interleaved A/B/A/B sampling is
 * far more trustworthy than switching once and comparing.</p>
 *
 * <p>The measured interval is the real end-to-end frame time — CPU work included. That is
 * deliberate: if the result shows no difference, the honest conclusion is "this machine is not
 * GPU-bound here", which is exactly the thing worth knowing.</p>
 *
 * <p>Frames are sampled from the head of the world render, and the first
 * {@link #WARMUP_MS} of every phase is discarded so a render-target reallocation never lands in the
 * measurements.</p>
 */
public final class ResolveBenchmark {

	private static final long WARMUP_MS = 1500L;
	private static final long PHASE_MS = 6000L;
	private static final int DEFAULT_CYCLES = 3;

	private static boolean running;
	private static int cycles;
	private static int phaseIndex;
	private static long phaseStartMs;
	private static long lastFrameAtMs;
	private static boolean warm;

	private static final List<Float> baselineMs = new ArrayList<>();
	private static final List<Float> scaledMs = new ArrayList<>();

	private ResolveBenchmark() {
	}

	/** Starts the benchmark. Returns false when the config makes it meaningless. */
	public static boolean start(MinecraftClient client) {
		ResolveConfig config = ResolveConfigManager.get();
		if (config.farScalePercent >= 99.9f) {
			message(client, "Cannot benchmark: the normal render resolution is already 100%, so there is nothing to compare against."
					+ "Set it to around 60% first.", Formatting.RED);
			return false;
		}

		reset();
		running = true;
		cycles = DEFAULT_CYCLES;
		phaseIndex = 0;
		phaseStartMs = Util.getMeasuringTimeMs();
		lastFrameAtMs = 0;

		Resolve.LOGGER.info("[RESOLVE-TEST] Benchmark started: {} cycles, {}s per phase ({}s warmup), baseline 100% vs {}%",
				cycles, PHASE_MS / 1000, WARMUP_MS / 1000, (int) config.farScalePercent);
		message(client, String.format("Benchmark started: 100%% vs %.0f%% measured %d times each, about %d seconds total."
						+ "Stand still, face open terrain, and keep your inventory and other screens closed.",
				config.farScalePercent, cycles, cycles * 2 * PHASE_MS / 1000),
				Formatting.GOLD);
		return true;
	}

	public static boolean isRunning() {
		return running;
	}

	/** The scale this frame must use, or a negative value when the benchmark is not running. */
	public static float forcedScale() {
		if (!running) {
			return -1.0f;
		}
		ResolveConfig config = ResolveConfigManager.get();
		// Even phases are the baseline (Resolve out of the way), odd phases are the reduced resolution.
		return phaseIndex % 2 == 0 ? 1.0f : config.farScalePercent / 100.0f;
	}

	/** Called once per rendered frame while the benchmark is running. */
	public static void onFrame(MinecraftClient client) {
		if (!running) {
			return;
		}

		long now = Util.getMeasuringTimeMs();

		if (lastFrameAtMs != 0 && warm) {
			float interval = now - lastFrameAtMs;
			// Ignore absurd outliers: a window drag or a garbage collection pause is not frame cost.
			if (interval > 0.0f && interval < 1000.0f) {
				(phaseIndex % 2 == 0 ? baselineMs : scaledMs).add(interval);
			}
		}
		lastFrameAtMs = now;

		if (now - phaseStartMs < WARMUP_MS) {
			warm = false;
			return;
		}
		warm = true;

		if (now - phaseStartMs >= WARMUP_MS + PHASE_MS) {
			phaseIndex++;
			phaseStartMs = now;
			lastFrameAtMs = 0;
			warm = false;

			if (phaseIndex >= cycles * 2) {
				finish(client);
			} else {
				message(client, String.format("Phase %d/%d: %s (%d s)",
						phaseIndex + 1, cycles * 2, phaseLabel(), PHASE_MS / 1000), Formatting.GRAY);
			}
		}
	}

	private static String phaseLabel() {
		ResolveConfig config = ResolveConfigManager.get();
		return phaseIndex % 2 == 0
				? "Baseline 100% (Resolve off)"
				: String.format("Resolve active (%.0f%%)", config.farScalePercent);
	}

	public static void cancel() {
		running = false;
	}

	// ---------------------------------------------------------------------------------------

	private static void finish(MinecraftClient client) {
		running = false;

		StringBuilder report = new StringBuilder();
		report.append("===== Resolve benchmark results =====\n");

		if (baselineMs.isEmpty() || scaledMs.isEmpty()) {
			report.append("Not enough samples to draw a conclusion (too few world render frames).\n");
			broadcast(client, report.toString(), Formatting.RED);
			return;
		}

		double baseFps = averageFps(baselineMs);
		double scaledFps = averageFps(scaledMs);
		double baseLow = lowFps(baselineMs);
		double scaledLow = lowFps(scaledMs);

		ResolveConfig config = ResolveConfigManager.get();
		double ratio = baseFps > 0 ? scaledFps / baseFps : 1.0;
		double saved = baseFps > 0 ? (1.0 - 1.0 / ratio) * 100.0 : 0.0;

		report.append(String.format("Baseline 100%     : avg %.1f FPS   1%% low %.1f   (%d frames)%n",
				baseFps, baseLow, baselineMs.size()));
		report.append(String.format("Resolve %.0f%%      : avg %.1f FPS   1%% low %.1f   (%d frames)%n",
				config.farScalePercent, scaledFps, scaledLow, scaledMs.size()));
		report.append(String.format("Improvement     : %+.1f FPS  (x%.2f, frame time -%.0f%%)%n",
				scaledFps - baseFps, ratio, saved));

		if (ratio < 1.03) {
			report.append("Reading: almost no difference — this machine is not GPU-bound in this scene, "
					+ "so lower resolution saves nothing (the bottleneck is CPU / draw calls / chunk loading).");
		} else if (ratio < 1.15) {
			report.append("Reading: a small improvement; the GPU is partially the limit.");
		} else {
			report.append("Reading: a clear improvement; GPU fill rate is the main bottleneck, which is exactly what Resolve targets.");
		}

		broadcast(client, report.toString(), Formatting.GOLD);
	}

	private static double averageFps(List<Float> samples) {
		double total = 0;
		for (float ms : samples) {
			total += ms;
		}
		double meanMs = total / samples.size();
		return meanMs > 0 ? 1000.0 / meanMs : 0.0;
	}

	/** 1% low: the frame time that only 1% of frames exceed. */
	private static double lowFps(List<Float> samples) {
		List<Float> sorted = new ArrayList<>(samples);
		Collections.sort(sorted);
		int index = Math.min(sorted.size() - 1, (int) (sorted.size() * 0.99));
		float worst = sorted.get(index);
		return worst > 0 ? 1000.0 / worst : 0.0;
	}

	private static void broadcast(MinecraftClient client, String text, Formatting color) {
		for (String line : text.split("\n")) {
			Resolve.LOGGER.info("[RESOLVE-TEST] {}", line);
			message(client, line, color);
		}
	}

	private static void message(MinecraftClient client, String text, Formatting color) {
		if (client == null || client.inGameHud == null) {
			return;
		}
		try {
			client.inGameHud.getChatHud().addMessage(
					Text.literal("[Resolve] " + text).formatted(color));
		} catch (Throwable t) {
			// Best effort; the log line carries the same information.
		}
	}

	private static void reset() {
		baselineMs.clear();
		scaledMs.clear();
		lastFrameAtMs = 0;
		warm = false;
	}
}
