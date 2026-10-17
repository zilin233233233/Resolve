package com.zilin233.resolve.config;

/**
 * How Resolve should behave while an Iris shader pack is active.
 *
 * <p>Shader packs replace Minecraft's world render targets with their own, so the obvious worry is
 * that scaling the main framebuffer would desynchronise them. It does not: Iris sizes its targets
 * from {@code MinecraftClient.getFramebuffer().textureWidth/textureHeight} and re-checks that size
 * once per frame at the head of the world render, which Resolve's swap happens before. Iris therefore
 * follows the reduced resolution on its own, without any shader reload.</p>
 */
public enum ShaderMode {
	/**
	 * Always apply the resolution scale and let Iris follow along (it reallocates its own buffers
	 * automatically). This is the recommended mode and costs nothing extra.
	 */
	AUTO("Auto (recommended)"),

	/**
	 * Like {@link #AUTO}, but additionally force a full {@code Iris.reload()} whenever the applied
	 * scale settles somewhere new. Only useful as a workaround for a pack that caches its target
	 * size; it recompiles the whole pack each time, so it is rate limited to once every few seconds.
	 */
	RELOAD_ON_CHANGE("Reload on change"),

	/**
	 * Do not scale at all while a shader pack is in use. Use this if a specific pack misbehaves.
	 */
	OFF_WITH_SHADERS("Off with shaders");

	private final String label;

	ShaderMode(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}

	public ShaderMode next() {
		ShaderMode[] values = values();
		return values[(ordinal() + 1) % values.length];
	}
}
