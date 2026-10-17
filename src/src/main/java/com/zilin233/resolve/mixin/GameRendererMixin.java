package com.zilin233.resolve.mixin;

import com.zilin233.resolve.Resolve;
import com.zilin233.resolve.render.DynamicResolution;
import com.zilin233.resolve.render.ResolutionController;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Brackets the world render with the reduced-resolution render target.
 *
 * <p>{@code GameRenderer.renderWorld} is the exact window in which Minecraft draws the terrain, the
 * sky, the entities and the first-person hand, and nothing else: the HUD and every GUI screen are
 * rendered by the caller afterwards. Swapping the target here therefore scales the world without
 * touching the interface.</p>
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

	private static boolean resolve$updateFailed;

	@Inject(method = "renderWorld", at = @At("HEAD"))
	private void resolve$beginWorldRender(RenderTickCounter tickCounter, CallbackInfo ci) {
		MinecraftClient client = MinecraftClient.getInstance();

		if (!resolve$updateFailed) {
			try {
				ResolutionController.update(client);
			} catch (Throwable t) {
				// A failure here must never take the frame down with it.
				resolve$updateFailed = true;
				ResolutionController.reset();
				Resolve.LOGGER.error("Resolution controller failed; Resolve is now inert until the game restarts", t);
			}
		}

		DynamicResolution.begin(client, ResolutionController.appliedScale());
	}

	@Inject(method = "renderWorld", at = @At("RETURN"))
	private void resolve$endWorldRender(RenderTickCounter tickCounter, CallbackInfo ci) {
		DynamicResolution.end(MinecraftClient.getInstance());
	}
}
