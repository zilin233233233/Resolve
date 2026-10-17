package com.zilin233.resolve.config;

import com.zilin233.resolve.Resolve;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Loads, saves and hands out the single {@link ResolveConfig} instance. */
public final class ResolveConfigManager {

	private static final Gson GSON = new GsonBuilder()
			.setPrettyPrinting()
			.disableHtmlEscaping()
			.create();

	private static final Path CONFIG_PATH =
			FabricLoader.getInstance().getConfigDir().resolve(Resolve.MOD_ID + ".json");

	/**
	 * Config file name used before the mod was renamed from "foxy" to "resolve". Read once, on the
	 * first launch after the rename, so an already-tuned setup carries over instead of resetting.
	 */
	private static final String LEGACY_FILE_NAME = "foxy.json";

	private static ResolveConfig config = new ResolveConfig();
	private static final List<Runnable> LISTENERS = new ArrayList<>();

	private ResolveConfigManager() {
	}

	public static ResolveConfig get() {
		return config;
	}

	/** Registers a callback fired after the config was changed and saved. */
	public static void addListener(Runnable listener) {
		LISTENERS.add(listener);
	}

	public static Path path() {
		return CONFIG_PATH;
	}

	public static void load() {
		if (!Files.exists(CONFIG_PATH)) {
			if (migrateLegacy()) {
				return;
			}
			Resolve.LOGGER.info("No config at {}, writing defaults", CONFIG_PATH);
			config = new ResolveConfig();
			save();
			return;
		}

		try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
			ResolveConfig loaded = GSON.fromJson(reader, ResolveConfig.class);
			config = loaded != null ? loaded : new ResolveConfig();
		} catch (Exception e) {
			Resolve.LOGGER.error("Could not read {}, falling back to defaults", CONFIG_PATH, e);
			config = new ResolveConfig();
		}

		// A hand-edited file can contain anything; never trust it.
		config.sanitize();
	}

	/**
	 * Reads the pre-rename config, if there is one, and writes it out under the new name. The old
	 * file is deliberately left in place: deleting a user's file during a rename is not this mod's
	 * call to make, and it costs nothing to leave behind.
	 *
	 * @return true when a legacy config was found and taken over
	 */
	private static boolean migrateLegacy() {
		Path legacy = CONFIG_PATH.getParent().resolve(LEGACY_FILE_NAME);
		if (!Files.exists(legacy)) {
			return false;
		}

		try (Reader reader = Files.newBufferedReader(legacy, StandardCharsets.UTF_8)) {
			ResolveConfig loaded = GSON.fromJson(reader, ResolveConfig.class);
			config = loaded != null ? loaded : new ResolveConfig();
			config.sanitize();
			save();
			Resolve.LOGGER.info("Migrated settings from {} to {}", LEGACY_FILE_NAME, CONFIG_PATH.getFileName());
			return true;
		} catch (Exception e) {
			Resolve.LOGGER.error("Could not migrate {}", legacy, e);
			return false;
		}
	}

	public static void save() {
		config.sanitize();
		try {
			Files.createDirectories(CONFIG_PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(CONFIG_PATH, StandardCharsets.UTF_8)) {
				GSON.toJson(config, writer);
			}
		} catch (IOException e) {
			Resolve.LOGGER.error("Could not write {}", CONFIG_PATH, e);
		}
	}

	/** Saves the config and notifies listeners. Call this after any edit. */
	public static void saveAndNotify() {
		save();
		for (Runnable listener : LISTENERS) {
			try {
				listener.run();
			} catch (Throwable t) {
				Resolve.LOGGER.error("Config listener failed", t);
			}
		}
	}
}
