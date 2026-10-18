package me.texyle.startreminders.persistence;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import me.texyle.startreminders.reminders.ModSettings;

/** Loads and saves {@link ModSettings} from {@code settings.json}. */
public final class SettingsRepository {

	private static ModSettings settings;
	private static boolean loaded = false;

	private SettingsRepository() {}

	/** Returns the settings, loading them from disk on first use. Never null. */
	public static ModSettings get() {
		ensureLoaded();
		return settings;
	}

	public static void save() {
		if (settings == null) return;

		String json = new GsonBuilder().setPrettyPrinting().create().toJson(settings);

		try {
			File file = new File(StoragePaths.SETTINGS_FILE);
			File parent = file.getParentFile();
			if (parent != null && !parent.exists()) parent.mkdirs();
			file.createNewFile();

			FileWriter fw = new FileWriter(file);
			try {
				fw.write(json);
			} finally {
				fw.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static void ensureLoaded() {
		if (loaded && settings != null) {
			return;
		}

		boolean needsSave = false;

		try {
			File file = new File(StoragePaths.SETTINGS_FILE);
			File parent = file.getParentFile();
			if (parent != null && !parent.exists()) parent.mkdirs();

			boolean created = file.createNewFile();
			if (created) {
				settings = new ModSettings();
				needsSave = true;
			} else {
				ModSettings read = readSettings(file);

				if (read == null) {
					settings = new ModSettings();
					needsSave = true;
				} else {
					settings = read;
				}
			}
		} catch (IOException e) {
			settings = new ModSettings();
			needsSave = true;
		}

		if (settings == null) {
			settings = new ModSettings();
			needsSave = true;
		}

		if (needsSave) {
			save();
		}

		loaded = true;
	}

	private static ModSettings readSettings(File file) throws IOException {
		Reader reader = new FileReader(file);
		try {
			return new Gson().fromJson(reader, ModSettings.class);
		} catch (JsonSyntaxException ignored) {
			return null;
		} finally {
			reader.close();
		}
	}
}
