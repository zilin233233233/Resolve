package com.zilin233.resolve.mixin;

import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reads vanilla's zoom multiplier.
 *
 * <p>This has to be an accessor mixin rather than plain reflection. At runtime Minecraft runs with
 * intermediary names ({@code field_xxxx}), so {@code GameRenderer.class.getDeclaredField("fovMultiplier")}
 * throws {@link NoSuchFieldException} &mdash; the Yarn name only exists at compile time. An
 * {@code @Accessor} is rewritten to the intermediary name during the build, so it keeps working.</p>
 *
 * <p>See {@link com.zilin233.resolve.zoom.ZoomDetector} for why this one field is the universal zoom
 * signal.</p>
 */
@Mixin(GameRenderer.class)
public interface GameRendererAccessor {

	@Accessor("fovMultiplier")
	float resolve$getFovMultiplier();
}
