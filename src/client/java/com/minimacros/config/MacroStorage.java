package com.minimacros.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.minimacros.MiniMacrosClient;
import com.minimacros.macro.MacroDefinition;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Loads and saves every macro to {@code config/mini-macros/macros.json}. */
public final class MacroStorage {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final int FORMAT_VERSION = 2;

	private static final Path FILE = FabricLoader.getInstance()
			.getConfigDir()
			.resolve("mini-macros")
			.resolve("macros.json");

	private static List<MacroDefinition> macros = new ArrayList<>();

	private MacroStorage() {
	}

	/** The live list. Mutating it is fine; call {@link #save()} to persist. */
	public static List<MacroDefinition> macros() {
		return macros;
	}

	public static void replaceAll(List<MacroDefinition> replacement) {
		macros = new ArrayList<>(replacement);
	}

	/** Clipboard export for sharing a single macro. */
	public static String toJson(MacroDefinition macro) {
		JsonObject object = GSON.toJsonTree(macro).getAsJsonObject();
		object.addProperty("keyFormat", "sdl-scancode");
		return GSON.toJson(object);
	}

	/** @return the parsed macro, or null if the text was not a macro. */
	public static MacroDefinition fromJson(String json) {
		try {
			// Checked as a tree first: any JSON object at all deserialises into a
			// MacroDefinition full of defaults, so the block list is what says the
			// clipboard actually held a macro. "script" is the pre-stacks key.
			JsonObject object = GSON.fromJson(json, JsonObject.class);
			if (object == null || !(object.has("stacks") || object.has("script"))) {
				return null;
			}
			MacroDefinition macro = GSON.fromJson(object, MacroDefinition.class);
			if (macro == null) {
				return null;
			}
			macro.normalize();
			if (!object.has("keyFormat")) {
				macro.migrateLegacyKeybind();
			}
			return macro;
		} catch (JsonSyntaxException | NullPointerException e) {
			return null;
		}
	}

	public static void load() {
		if (!Files.exists(FILE)) {
			macros = new ArrayList<>();
			return;
		}
		try (Reader reader = Files.newBufferedReader(FILE)) {
			SaveFile parsed = GSON.fromJson(reader, SaveFile.class);
			macros = parsed == null || parsed.macros == null ? new ArrayList<>() : parsed.macros;
			macros.removeIf(macro -> macro == null);
			for (MacroDefinition macro : macros) {
				macro.normalize();
				if (parsed.version < 2) {
					macro.migrateLegacyKeybind();
				}
			}
		} catch (IOException | JsonSyntaxException e) {
			MiniMacrosClient.LOGGER.error("Could not read {} — starting with no macros", FILE, e);
			macros = new ArrayList<>();
		}
	}

	public static void save() {
		try {
			Files.createDirectories(FILE.getParent());
			try (Writer writer = Files.newBufferedWriter(FILE)) {
				GSON.toJson(new SaveFile(FORMAT_VERSION, macros), writer);
			}
		} catch (IOException e) {
			MiniMacrosClient.LOGGER.error("Could not write {}", FILE, e);
		}
	}

	/** Wrapper so the file has somewhere to record its format if it ever changes. */
	private record SaveFile(int version, List<MacroDefinition> macros) {
	}
}
