package com.zilin233.resolve;

import com.zilin233.resolve.compat.ModConflicts;
import com.zilin233.resolve.config.ResolveConfigManager;
import com.zilin233.resolve.debug.ResolveBenchmark;
import com.zilin233.resolve.modmenu.ResolveConfigScreen;
import com.zilin233.resolve.render.DynamicResolution;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Client entrypoint: loads the config, registers key bindings and wires up shutdown.
 *
 * <p>Both keys ship unbound on purpose &mdash; Resolve is likely to be one of a hundred mods, and
 * inventing default shortcuts is a good way to collide with somebody else's.</p>
 */
public class ResolveClient implements ClientModInitializer {

	public static final String KEY_CATEGORY = "category.resolve";
	public static final String KEY_OPEN_CONFIG = "key.resolve.open_config";
	public static final String KEY_BENCHMARK = "key.resolve.benchmark";

	private static KeyBinding openConfigKey;
	private static KeyBinding benchmarkKey;

	@Override
	public void onInitializeClient() {
		ResolveConfigManager.load();

		openConfigKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				KEY_OPEN_CONFIG,
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_UNKNOWN,
				KEY_CATEGORY));

		benchmarkKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				KEY_BENCHMARK,
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_UNKNOWN,
				KEY_CATEGORY));

		ClientTickEvents.END_CLIENT_TICK.register(this::onEndClientTick);
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> shutdown());

		Resolve.LOGGER.info("Resolve ready (Iris: {}, Sodium: {}, Zoomify: {}, RenderScale: {})",
				ModConflicts.irisActive() ? "yes" : "no",
				ModConflicts.sodiumActive() ? "yes" : "no",
				ModConflicts.zoomifyActive() ? "yes" : "no",
				ModConflicts.renderScaleActive() ? "yes" : "no");

		if (ModConflicts.renderScaleActive()) {
			Resolve.LOGGER.warn("RenderScale is installed. It performs the same render-target swap as Resolve, "
					+ "so Resolve has disabled itself. Remove RenderScale to use Resolve.");
		}
	}

	private void onEndClientTick(MinecraftClient client) {
		if (openConfigKey != null) {
			// Drain the queue: a key can be pressed more than once between ticks.
			while (openConfigKey.wasPressed()) {
				client.setScreen(new ResolveConfigScreen(client.currentScreen));
			}
		}

		if (benchmarkKey != null) {
			while (benchmarkKey.wasPressed()) {
				ResolveBenchmark.start(client);
			}
		}
	}

	/** Called by Fabric on client shutdown. */
	public static void shutdown() {
		DynamicResolution.close();
	}
}
