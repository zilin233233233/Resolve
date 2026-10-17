package com.zilin233.resolve.mixin;

import com.zilin233.resolve.config.ResolveConfigManager;
import com.zilin233.resolve.render.ResolutionController;
import net.minecraft.client.gui.hud.DebugHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/** Adds a Resolve status line to the F3 screen, so users can see what the mod is actually doing. */
@Mixin(DebugHud.class)
public abstract class DebugHudMixin {

	// cancellable = true is required: without it Mixin hands us a CallbackInfo that throws
	// CancellationException the moment setReturnValue() is called, which crashes the client
	// the first time F3 is pressed.
	@Inject(method = "getLeftText", at = @At("RETURN"), cancellable = true)
	private void resolve$appendStatusLine(CallbackInfoReturnable<List<String>> cir) {
		if (!ResolveConfigManager.get().debugHud) {
			return;
		}

		List<String> original = cir.getReturnValue();
		if (original == null) {
			return;
		}

		// The returned list may be immutable, so build a fresh one rather than mutating in place.
		List<String> extended = new ArrayList<>(original);
		extended.add(ResolutionController.debugLine());
		cir.setReturnValue(extended);
	}
}
