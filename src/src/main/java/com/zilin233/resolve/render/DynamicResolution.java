package com.zilin233.resolve.render;

import com.zilin233.resolve.Resolve;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;

/**
 * Owns the offscreen render target the world is drawn into at a reduced resolution.
 *
 * <p>Minecraft 1.21.5+ renders the world through a frame graph whose passes all resolve their colour
 * attachment from {@code MinecraftClient.getFramebuffer()}. Resolve exploits exactly that single seam:
 * for the duration of {@code GameRenderer.renderWorld} it points that getter at a smaller
 * {@link SimpleFramebuffer}, then puts the real window framebuffer back and blits the small image up
 * over it. Everything after the world &mdash; the hand is inside the world pass, but the HUD, chat
 * and every GUI screen are outside it &mdash; is therefore still drawn at native resolution.</p>
 *
 * <p>The upscale uses {@link Framebuffer#drawBlit}, the same call vanilla uses for the entity
 * outline pass. That is a plain full-screen quad through the shared render pipeline, so unlike a raw
 * {@code glBlitFramebuffer} it stays valid on any backend and needs no GL-specific accessor.</p>
 *
 * <p>All methods must be called from the render thread.</p>
 */
public final class DynamicResolution {

	/** The real window framebuffer, captured once. Never replaced by us. */
	private static Framebuffer windowTarget;

	/** Resolve's reduced-resolution target. Created lazily, reused across frames. */
	private static SimpleFramebuffer scaledTarget;

	private static boolean engaged;
	private static boolean broken;

	private static int scaledWidth;
	private static int scaledHeight;

	private DynamicResolution() {
	}

	/**
	 * Swaps the world render target for a reduced-resolution one.
	 *
	 * @param scale multiplier in {@code (0, 1]}; values at or above 1 leave vanilla untouched
	 */
	public static void begin(MinecraftClient client, float scale) {
		// Self-heal: if the previous frame never reached end() (an exception unwound the render),
		// restore the window framebuffer before doing anything else.
		if (engaged) {
			end(client);
		}

		scaledWidth = 0;
		scaledHeight = 0;

		if (broken || client == null) {
			return;
		}

		Framebuffer mainTarget = client.getFramebuffer();
		if (mainTarget == null) {
			return;
		}

		if (windowTarget == null) {
			windowTarget = mainTarget;
		} else if (mainTarget != windowTarget) {
			// Somebody else owns the main framebuffer right now (typically RenderScale). Backing off
			// is the only safe option: two swaps in one frame produce garbage, not a compromise.
			return;
		}

		if (scale >= 0.995f) {
			// Full resolution was requested, so the vanilla path is already what we want.
			return;
		}

		int windowWidth = client.getWindow().getFramebufferWidth();
		int windowHeight = client.getWindow().getFramebufferHeight();
		if (windowWidth <= 0 || windowHeight <= 0) {
			return;
		}

		int width = Math.max(1, Math.round(windowWidth * scale));
		int height = Math.max(1, Math.round(windowHeight * scale));
		if (width >= windowWidth && height >= windowHeight) {
			return;
		}

		try {
			ensureTarget(width, height);
			client.framebuffer = scaledTarget;
		} catch (Throwable t) {
			Resolve.LOGGER.error("Could not create the {}x{} scaled render target; "
					+ "dynamic resolution is disabled for this session", width, height, t);
			broken = true;
			releaseTarget();
			return;
		}

		scaledWidth = width;
		scaledHeight = height;
		engaged = true;
	}

	/** Restores the window framebuffer and upscales the reduced-resolution image over it. */
	public static void end(MinecraftClient client) {
		if (!engaged) {
			return;
		}
		engaged = false;

		if (client == null || windowTarget == null || scaledTarget == null) {
			return;
		}

		client.framebuffer = windowTarget;

		try {
			scaledTarget.drawBlit(windowTarget.getColorAttachmentView());
		} catch (Throwable t) {
			Resolve.LOGGER.error("Upscale blit failed; disabling dynamic resolution for this session", t);
			broken = true;
		}
	}

	/** Releases GPU resources. Called when the mod is shut down. */
	public static void close() {
		engaged = false;
		windowTarget = null;
		releaseTarget();
		broken = false;
	}

	public static boolean isEngaged() {
		return engaged;
	}

	public static boolean isBroken() {
		return broken;
	}

	public static int getScaledWidth() {
		return scaledWidth;
	}

	public static int getScaledHeight() {
		return scaledHeight;
	}

	private static void ensureTarget(int width, int height) {
		if (scaledTarget == null) {
			scaledTarget = new SimpleFramebuffer("Resolve world", width, height, true);
			applyFilter();
			return;
		}

		if (scaledTarget.textureWidth != width || scaledTarget.textureHeight != height) {
			scaledTarget.resize(width, height);
		}
		applyFilter();
	}

	private static void applyFilter() {
		if (scaledTarget == null) {
			return;
		}
		FilterMode mode = com.zilin233.resolve.config.ResolveConfigManager.get().linearFilter
				? FilterMode.LINEAR
				: FilterMode.NEAREST;
		scaledTarget.setFilter(mode);
	}

	private static void releaseTarget() {
		if (scaledTarget != null) {
			try {
				scaledTarget.delete();
			} catch (Throwable t) {
				Resolve.LOGGER.debug("Ignoring failure while deleting the scaled render target", t);
			}
			scaledTarget = null;
		}
	}
}
