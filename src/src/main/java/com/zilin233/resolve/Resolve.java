package com.zilin233.resolve;

import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared constants for Resolve.
 *
 * <p>Resolve is a client-only mod, so there is no {@code ModInitializer} here; the client entrypoint
 * lives in {@link ResolveClient}.</p>
 */
public final class Resolve {
	public static final String MOD_ID = "resolve";
	public static final String MOD_NAME = "Resolve";

	/** Tag used on every log line so the mod is easy to grep for in a crowded log file. */
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

	private Resolve() {
	}

	public static Identifier id(String path) {
		return Identifier.of(MOD_ID, path);
	}
}
