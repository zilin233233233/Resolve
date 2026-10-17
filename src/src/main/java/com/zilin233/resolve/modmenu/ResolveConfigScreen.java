package com.zilin233.resolve.modmenu;

import com.zilin233.resolve.compat.ModConflicts;
import com.zilin233.resolve.config.ResolveConfig;
import com.zilin233.resolve.config.ResolveConfigManager;
import com.zilin233.resolve.config.ShaderMode;
import com.zilin233.resolve.debug.ResolveBenchmark;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

/**
 * Resolve's configuration screen.
 *
 * <p>Written against vanilla widgets only. Cloth Config and YACL are both extremely common, but
 * neither is guaranteed to be present, and a config screen that cannot open is worse than a plain
 * one. The layout is a hand-rolled scrolling list because the option count comfortably exceeds a
 * screenful even at GUI scale 2.</p>
 */
public class ResolveConfigScreen extends Screen {

	private static final int ROW_HEIGHT = 20;
	private static final int ROW_GAP = 4;
	private static final int ROW_STEP = ROW_HEIGHT + ROW_GAP;
	private static final int CONTENT_WIDTH = 320;

	private final Screen parent;

	/** Widgets inside the scrolling area. Positioned by {@link #layoutRows()}. */
	private final List<ClickableWidget> rows = new ArrayList<>();
	/** Widgets pinned to the bottom, outside the scrolling area. */
	private final List<ClickableWidget> fixed = new ArrayList<>();

	private int listTop;
	private int listBottom;
	private int contentHeight;
	private double scroll;

	private int rowX;
	private int rowWidth;

	private String notice;
	private int noticeColor = 0xFFFF8080;

	public ResolveConfigScreen(Screen parent) {
		super(Text.literal("Resolve Dynamic Resolution"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		rows.clear();
		fixed.clear();
		scroll = 0;

		ResolveConfig config = ResolveConfigManager.get();

		rowWidth = Math.min(CONTENT_WIDTH, this.width - 40);
		rowX = (this.width - rowWidth) / 2;

		listTop = 46;
		listBottom = this.height - 36;

		// ---- master switch -------------------------------------------------------------------
		addToggle("Master switch", "When off, the vanilla pipeline is used unchanged with zero overhead.",
				() -> config.enabled, v -> config.enabled = v);

		// ---- which chunks count as distant ---------------------------------------------------
		addSlider("Distant chunks start", "The chunk ring (counted from the player) at which chunks count as distant. Used to decide whether anything distant is worth downscaling at the current view distance.",
				0, 32, 1, config.farStartChunk, v -> config.farStartChunk = (int) v, " chunks");

		// ---- resolution ----------------------------------------------------------------------
		addSlider("Normal render resolution", "While not zoomed, the world is rendered at this percentage of the window and then upscaled. 80% means only 64% of the pixels are shaded.",
				25, 100, 1, config.farScalePercent, v -> config.farScalePercent = (float) v, "%");
		addSlider("Zoom render resolution", "The percentage used while zoom is detected. Set it to 100% to snap back to native sharpness the moment you zoom.",
				25, 100, 1, config.zoomScalePercent, v -> config.zoomScalePercent = (float) v, "%");
		addSlider("Resolution floor", "A hard safety floor so a typo cannot turn the screen into a handful of pixels.",
				10, 100, 1, config.minScalePercent, v -> config.minScalePercent = (float) v, "%");

		// ---- zoom detection ------------------------------------------------------------------
		addToggle("Zoom follows", "When off the resolution is constant and never changes with zoom.",
				() -> config.zoomEnabled, v -> config.zoomEnabled = v);
		addToggle("Detect · Spyglass", "Detects the vanilla spyglass (an item in use that is the spyglass).",
				() -> config.detectSpyglass, v -> config.detectSpyglass = v);
		addToggle("Detect · Zoomify", "Reads Zoomify's own zoom state directly (via reflection, no hard dependency).",
				() -> config.detectZoomify, v -> config.detectZoomify = v);
		addToggle("Detect · Zoom key", "Scans every key binding whose id mentions zoom, covering zoom mods without dedicated support.",
				() -> config.detectKeybind, v -> config.detectKeybind = v);
		addToggle("Detect · FOV change", "Universal fallback: any narrowed field of view counts as zoom, so no zoom mod can escape it.",
				() -> config.detectFov, v -> config.detectFov = v);
		addSlider("FOV zoom threshold", "The FOV multiplier must drop below this to count as zooming. 0.90 is about 1.11x zoom or more.",
				0.05, 0.99, 0.01, config.fovZoomThreshold, v -> config.fovZoomThreshold = (float) v, "");

		// ---- timing --------------------------------------------------------------------------
		addToggle("Smooth transition", "Eases between the two resolutions. Each step reallocates the render target, so it is off by default.",
				() -> config.smoothTransition, v -> config.smoothTransition = v);
		addSlider("Transition duration", "How long a smoothed transition lasts.",
				0, 1000, 10, config.transitionMs, v -> config.transitionMs = (int) v, " ms");
		addSlider("Transition steps", "How many discrete steps a smoothed transition is quantised into. Fewer steps means fewer render-target reallocations.",
				1, 16, 1, config.transitionSteps, v -> config.transitionSteps = (int) v, " steps");
		addSlider("Release delay", "How long after releasing zoom the scale waits before dropping back, so a twitchy key cannot thrash the target.",
				0, 1000, 10, config.releaseDelayMs, v -> config.releaseDelayMs = (int) v, " ms");

		// ---- output --------------------------------------------------------------------------
		addToggle("Linear filter", "Uses linear interpolation when upscaling (smoother). Off uses nearest neighbour: crisper but blocky.",
				() -> config.linearFilter, v -> config.linearFilter = v);
		addCycler("Shader mode", "Behaviour while a shader pack (Iris) is active. Iris follows Resolve's resolution automatically, so Auto is usually right.",
				config.shaderMode.label(),
				() -> {
					config.shaderMode = config.shaderMode.next();
					return config.shaderMode.label();
				});
		addToggle("F3 debug info", "Shows Resolve's current state on the F3 debug screen.",
				() -> config.debugHud, v -> config.debugHud = v);

		// ---- footer --------------------------------------------------------------------------
		int buttonWidth = 98;
		fixed.add(ButtonWidget.builder(Text.literal("Done"), b -> this.close())
				.dimensions(this.width / 2 - buttonWidth * 3 / 2 - 2, this.height - 28, buttonWidth, ROW_HEIGHT)
				.build());
		fixed.add(ButtonWidget.builder(Text.literal("Benchmark"), b -> startBenchmark())
				.dimensions(this.width / 2 - buttonWidth / 2, this.height - 28, buttonWidth, ROW_HEIGHT)
				.tooltip(Tooltip.of(Text.literal("Automatically switches between 100% and your configured resolution and measures the frame rate, "
						+ "sending the results to chat and the log. Stand still and keep other screens closed during the test.")))
				.build());
		fixed.add(ButtonWidget.builder(Text.literal("Reset defaults"), b -> resetToDefaults())
				.dimensions(this.width / 2 + buttonWidth / 2 + 2, this.height - 28, buttonWidth, ROW_HEIGHT)
				.tooltip(Tooltip.of(Text.literal("Restore every option to its factory default")))
				.build());

		for (ClickableWidget widget : rows) {
			addDrawableChild(widget);
		}
		for (ClickableWidget widget : fixed) {
			addDrawableChild(widget);
		}

		updateNotice();
		layoutRows();
	}

	// ---------------------------------------------------------------------------------------
	// Row construction helpers
	// ---------------------------------------------------------------------------------------

	private void addToggle(String label, String tooltip, BooleanSupplier getter, Consumer<Boolean> setter) {
		ButtonWidget button = ButtonWidget.builder(Text.empty(), b -> {
			boolean value = !getter.getAsBoolean();
			setter.accept(value);
			b.setMessage(toggleLabel(label, value));
		}).dimensions(rowX, 0, rowWidth, ROW_HEIGHT).tooltip(Tooltip.of(Text.literal(tooltip))).build();

		button.setMessage(toggleLabel(label, getter.getAsBoolean()));
		rows.add(button);
	}

	private void addCycler(String label, String tooltip, String initial, java.util.function.Supplier<String> advance) {
		ButtonWidget button = ButtonWidget.builder(Text.empty(), b -> b.setMessage(Text.literal(label + ": " + advance.get())))
				.dimensions(rowX, 0, rowWidth, ROW_HEIGHT)
				.tooltip(Tooltip.of(Text.literal(tooltip)))
				.build();

		button.setMessage(Text.literal(label + ": " + initial));
		rows.add(button);
	}

	private void addSlider(String label, String tooltip, double min, double max, double step,
						   double initial, DoubleConsumer setter, String unit) {
		ValueSlider slider = new ValueSlider(rowX, 0, rowWidth, ROW_HEIGHT, label, min, max, step, unit, initial, setter);
		slider.setTooltip(Tooltip.of(Text.literal(tooltip)));
		rows.add(slider);
	}

	private static Text toggleLabel(String label, boolean value) {
		return Text.literal(label + ": " + (value ? "On" : "Off"));
	}

	private void startBenchmark() {
		MinecraftClient client = MinecraftClient.getInstance();
		// Save first so the benchmark measures the values currently on screen.
		ResolveConfigManager.save();
		if (ResolveBenchmark.start(client)) {
			this.close();
		}
	}

	private void resetToDefaults() {
		ResolveConfigManager.get().copyFrom(new ResolveConfig());
		ResolveConfigManager.save();
		if (this.client != null) {
			// Rebuild every widget so the new values are reflected.
			this.clearAndInit();
		}
	}

	private void updateNotice() {
		if (ModConflicts.renderScaleActive()) {
			noticeColor = 0xFFFF8080;
			notice = "RenderScale detected: both mods rewrite the main render target, so Resolve has disabled itself. Remove RenderScale and restart the game.";
			return;
		}
		ResolveConfig config = ResolveConfigManager.get();
		if (!config.enabled) {
			noticeColor = 0xFFFFD070;
			notice = "Resolve is currently disabled.";
			return;
		}
		if (config.zoomEnabled && config.zoomScalePercent >= 99.9f && config.farScalePercent >= 99.9f) {
			noticeColor = 0xFFFFD070;
			notice = "Both resolutions are 100%, so Resolve will not do anything.";
			return;
		}
		notice = null;
	}

	// ---------------------------------------------------------------------------------------
	// Layout and rendering
	// ---------------------------------------------------------------------------------------

	private void layoutRows() {
		contentHeight = rows.size() * ROW_STEP;
		int visibleHeight = listBottom - listTop;
		int maxScroll = Math.max(0, contentHeight - visibleHeight);
		scroll = Math.max(0, Math.min(maxScroll, scroll));

		for (int i = 0; i < rows.size(); i++) {
			ClickableWidget widget = rows.get(i);
			int y = listTop - (int) scroll + i * ROW_STEP;
			if (y + ROW_HEIGHT < listTop || y > listBottom) {
				// Parked far off-screen so it cannot be hovered or clicked.
				widget.setY(-1000);
			} else {
				widget.setY(y);
			}
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		context.fill(0, 0, this.width, this.height, 0xE0101010);

		context.enableScissor(0, listTop, this.width, listBottom);
		for (ClickableWidget widget : rows) {
			widget.render(context, mouseX, mouseY, delta);
		}
		context.disableScissor();

		context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 12, 0xFFFFFFFF);

		if (notice != null) {
			context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(notice), this.width / 2, 28, noticeColor);
		} else {
			context.drawCenteredTextWithShadow(this.textRenderer,
					Text.literal("Raises distant chunk resolution while zoomed, lowers it otherwise to save performance"), this.width / 2, 28, 0xFFA0A0A0);
		}

		for (ClickableWidget widget : fixed) {
			widget.render(context, mouseX, mouseY, delta);
		}

		renderScrollbar(context);
	}

	private void renderScrollbar(DrawContext context) {
		int visibleHeight = listBottom - listTop;
		if (contentHeight <= visibleHeight || contentHeight <= 0) {
			return;
		}
		int barHeight = Math.max(16, visibleHeight * visibleHeight / contentHeight);
		int maxScroll = contentHeight - visibleHeight;
		int barY = listTop + (int) ((visibleHeight - barHeight) * (scroll / maxScroll));

		int barX = rowX + rowWidth + 6;
		context.fill(barX, listTop, barX + 3, listBottom, 0x40FFFFFF);
		context.fill(barX, barY, barX + 3, barY + barHeight, 0xC0FFFFFF);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (mouseX >= 0 && mouseX <= this.width && mouseY >= listTop && mouseY <= listBottom) {
			scroll -= verticalAmount * ROW_STEP * 1.5;
			layoutRows();
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public void close() {
		ResolveConfigManager.saveAndNotify();
		if (this.client != null) {
			this.client.setScreen(this.parent);
		}
	}

	@Override
	public void removed() {
		// Also covers the Alt-F4 / screen-replaced paths that never reach close().
		ResolveConfigManager.saveAndNotify();
		super.removed();
	}

	// ---------------------------------------------------------------------------------------

	/** A slider that formats itself and reports changes through a callback. */
	private static final class ValueSlider extends SliderWidget {

		private final String label;
		private final double min;
		private final double max;
		private final double step;
		private final String unit;
		private final DoubleConsumer onChange;

		ValueSlider(int x, int y, int width, int height, String label,
					double min, double max, double step, String unit,
					double initial, DoubleConsumer onChange) {
			super(x, y, width, height, Text.empty(), (initial - min) / (max - min));
			this.label = label;
			this.min = min;
			this.max = max;
			this.step = step;
			this.unit = unit;
			this.onChange = onChange;
			// SliderWidget's own constructor calls updateMessage() before the fields above exist,
			// so the first meaningful refresh has to happen here.
			updateMessage();
		}

		@Override
		protected void updateMessage() {
			if (label == null) {
				// Called from the super constructor, before our fields are assigned.
				return;
			}
			setMessage(Text.literal(label + ": " + format(currentValue()) + unit));
		}

		@Override
		protected void applyValue() {
			if (onChange == null) {
				return;
			}
			onChange.accept(currentValue());
			updateMessage();
		}

		private double currentValue() {
			double raw = min + value * (max - min);
			if (step > 0) {
				raw = Math.round(raw / step) * step;
			}
			return Math.max(min, Math.min(max, raw));
		}

		private String format(double value) {
			if (step >= 1.0) {
				return String.valueOf((int) Math.round(value));
			}
			return String.format("%.2f", value);
		}
	}
}
