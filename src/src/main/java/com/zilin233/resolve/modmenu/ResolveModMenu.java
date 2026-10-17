package com.zilin233.resolve.modmenu;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Mod Menu integration.
 *
 * <p>Mod Menu is compile-only, so this class is never touched unless Mod Menu itself asks for the
 * {@code modmenu} entrypoint. Without Mod Menu installed the class is simply never loaded and Resolve
 * still works, with the config screen reachable through its key binding.</p>
 */
public class ResolveModMenu implements ModMenuApi {

	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return parent -> new ResolveConfigScreen(parent);
	}
}
