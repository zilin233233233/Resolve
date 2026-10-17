package com.zilin233.resolve.compat;

import net.fabricmc.loader.api.FabricLoader;

/** Detects other mods that would fight Resolve over Minecraft's main render target. */
public final class ModConflicts {

	private ModConflicts() {
	}

	/**
	 * RenderScale (mod id {@code renderscale}) implements the same framebuffer swap Resolve does. Two
	 * mods swapping {@code MinecraftClient.framebuffer} in the same frame corrupt each other's
	 * output, so Resolve stands down completely when it sees RenderScale.
	 */
	public static boolean renderScaleActive() {
		try {
			return FabricLoader.getInstance().isModLoaded("renderscale");
		} catch (Throwable t) {
			return false;
		}
	}

	public static boolean sodiumActive() {
		return isLoaded("sodium");
	}

	public static boolean irisActive() {
		// Iris still uses the "iris" mod id in 1.21.8.
		return isLoaded("iris");
	}

	public static boolean zoomifyActive() {
		return isLoaded("zoomify");
	}

	public static boolean modMenuActive() {
		return isLoaded("modmenu");
	}

	private static boolean isLoaded(String id) {
		try {
			return FabricLoader.getInstance().isModLoaded(id);
		} catch (Throwable t) {
			return false;
		}
	}
}
