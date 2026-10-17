package com.zilin233.resolve.zoom;

import com.zilin233.resolve.Resolve;
import com.zilin233.resolve.config.ResolveConfig;
import com.zilin233.resolve.config.ResolveConfigManager;
import com.zilin233.resolve.mixin.GameRendererAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Decides whether the player is currently looking through something that magnifies the view.
 *
 * <p>Four independent sources are polled every frame and OR-ed together. The important one is
 * {@link Source#FOV_MULTIPLIER}: every zoom implementation that reaches the screen eventually has
 * to narrow Minecraft's field of view, and vanilla funnels all of that through the single
 * {@code GameRenderer.fovMultiplier} field (clamped to {@code 0.1 .. 1.5}). Reading it therefore
 * covers the vanilla spyglass and essentially every "zoom mod" without Resolve having to know about
 * them, including ones written after this mod.</p>
 */
public final class ZoomDetector {

	/** Which check decided that zoom is active. Purely informational (debug HUD / logging). */
	public enum Source {
		NONE("Not zooming"),
		SPYGLASS("Spyglass"),
		ZOOMIFY("Zoomify"),
		KEYBIND("Zoom key"),
		FOV_MULTIPLIER("FOV change");

		private final String label;

		Source(String label) {
			this.label = label;
		}

		public String label() {
			return label;
		}
	}

	private static boolean accessorBroken;

	private static KeyBinding[] zoomKeybindings;
	private static boolean keybindingsResolved;

	private static Source lastSource = Source.NONE;

	private ZoomDetector() {
	}

	/** Polls every enabled detector. Cheap; intended to be called once per rendered frame. */
	public static Source detect(MinecraftClient client) {
		ResolveConfig config = ResolveConfigManager.get();

		if (client == null || client.player == null || !config.zoomEnabled) {
			lastSource = Source.NONE;
			return Source.NONE;
		}

		Source source = Source.NONE;

		if (source == Source.NONE && config.detectSpyglass && isUsingSpyglass(client)) {
			source = Source.SPYGLASS;
		}
		if (source == Source.NONE && config.detectZoomify && ZoomifyHook.isZooming()) {
			source = Source.ZOOMIFY;
		}
		if (source == Source.NONE && config.detectKeybind && isZoomKeyHeld(client)) {
			source = Source.KEYBIND;
		}
		if (source == Source.NONE && config.detectFov && isFovNarrowed(client)) {
			source = Source.FOV_MULTIPLIER;
		}

		lastSource = source;
		return source;
	}

	public static Source lastSource() {
		return lastSource;
	}

	/** How many key bindings the generic scanner picked up. Shown by the self-check overlay. */
	public static int zoomKeybindingCount(MinecraftClient client) {
		if (client == null) {
			return 0;
		}
		return zoomKeybindings(client).length;
	}

	/** The raw zoom factor, where {@code 1.0} means "not zoomed". {@code NaN} when unavailable. */
	public static float fovMultiplier(MinecraftClient client) {
		if (client == null || client.gameRenderer == null) {
			return Float.NaN;
		}
		if (accessorBroken) {
			return Float.NaN;
		}
		try {
			return ((GameRendererAccessor) (Object) client.gameRenderer).resolve$getFovMultiplier();
		} catch (Throwable t) {
			accessorBroken = true;
			Resolve.LOGGER.warn("Could not read GameRenderer.fovMultiplier; "
					+ "zoom detection falls back to key bindings and the spyglass check", t);
			return Float.NaN;
		}
	}

	// -------------------------------------------------------------------------------------------
	// Individual detectors
	// -------------------------------------------------------------------------------------------

	private static boolean isUsingSpyglass(MinecraftClient client) {
		if (!client.player.isUsingItem()) {
			return false;
		}
		return client.player.getActiveItem().isOf(Items.SPYGLASS);
	}

	private static boolean isZoomKeyHeld(MinecraftClient client) {
		for (KeyBinding binding : zoomKeybindings(client)) {
			if (binding.isPressed()) {
				return true;
			}
		}
		return false;
	}

	private static boolean isFovNarrowed(MinecraftClient client) {
		float multiplier = fovMultiplier(client);
		if (Float.isNaN(multiplier)) {
			return false;
		}
		return multiplier < ResolveConfigManager.get().fovZoomThreshold;
	}

	/**
	 * Collects every registered key binding whose translation key mentions "zoom".
	 *
	 * <p>Zoom mods advertise their key in the controls screen, so the id almost always contains the
	 * word regardless of author or language. This is what lets Resolve pick up a zoom mod it has no
	 * specific support for.</p>
	 */
	private static KeyBinding[] zoomKeybindings(MinecraftClient client) {
		if (keybindingsResolved) {
			return zoomKeybindings;
		}
		keybindingsResolved = true;

		List<KeyBinding> matches = new ArrayList<>();
		KeyBinding[] all = client.options.allKeys;
		if (all != null) {
			for (KeyBinding binding : all) {
				String key;
				try {
					key = binding.getTranslationKey();
				} catch (Throwable t) {
					continue;
				}
				if (key != null && key.toLowerCase(Locale.ROOT).contains("zoom")) {
					matches.add(binding);
				}
			}
		}
		zoomKeybindings = matches.toArray(new KeyBinding[0]);
		Resolve.LOGGER.info("Zoom-capable key bindings found: {}", matches.size());
		return zoomKeybindings;
	}
}
