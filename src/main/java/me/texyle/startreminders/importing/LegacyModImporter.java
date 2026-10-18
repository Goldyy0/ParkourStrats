package me.texyle.startreminders.importing;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;

import me.texyle.startreminders.StratReminders;
import me.texyle.startreminders.data.Jump;
import me.texyle.startreminders.data.ParkourMap;
import me.texyle.startreminders.persistence.BuiltinContainers;
import me.texyle.startreminders.persistence.DataStoreRepository;
import me.texyle.startreminders.reminders.Reminder;
import me.texyle.startreminders.utils.ChatUtils;

/** Imports strategies of the original StratReminders mod (StratReminders/reminders.json) into RestoredStrats. */
public final class LegacyModImporter {

	private LegacyModImporter() {}

	// -------------------------
	// Legacy (old mod) import file
	// -------------------------

	private static final String LEGACY_FOLDER = "StratReminders";
	private static final String LEGACY_FILE_NAME = "reminders.json";

	// Re-entrancy guard + anti-spam for missing legacy file
	private static boolean legacyImportInProgress = false;
	private static long lastLegacyMissingChatMs = 0L;
	private static final long LEGACY_MISSING_CHAT_COOLDOWN_MS = 2500L;
	private static long lastLegacyImportRunMs = 0L;
	private static final long LEGACY_IMPORT_DOUBLE_TRIGGER_GUARD_MS = 600L;

	// -------------------------
	// Legacy import (Insert)
	// -------------------------

	static class LegacyEntry {
		ArrayList<String> lines;
		int posX;
		int posY;
		int posZ;
	}

	private static File getLegacyRemindersFile() {
		Minecraft mc = Minecraft.getMinecraft();
		if (mc == null) {
			return new File(LEGACY_FOLDER + "/" + LEGACY_FILE_NAME);
		}
		File mcDir = mc.mcDataDir;
		return new File(new File(mcDir, LEGACY_FOLDER), LEGACY_FILE_NAME);
	}

	private static void sendLegacyErrorOnce(String msg) {
		long now = System.currentTimeMillis();
		if (now - lastLegacyMissingChatMs < LEGACY_MISSING_CHAT_COOLDOWN_MS) {
			return;
		}
		lastLegacyMissingChatMs = now;
		ChatUtils.sendChat(Minecraft.getMinecraft(),
				ChatUtils.ERROR_PREFIX + msg);
	}

	private static void sendLegacyInfo(String msg) {
		ChatUtils.sendChat(Minecraft.getMinecraft(),
				ChatUtils.INFO_PREFIX + msg);
	}

	private static boolean legacyFileExistsOrReport() {
		File f = getLegacyRemindersFile();
		if (!f.exists() || !f.isFile()) {
			sendLegacyErrorOnce("Legacy file not found: StratReminders/reminders.json");
			return false;
		}
		return true;
	}

	private static String nextRestoredJumpName(ParkourMap restoredMap) {
		int n = 1;
		if (restoredMap != null && restoredMap.getJumps() != null) {
			n = restoredMap.getJumps().size() + 1;
		}
		return "Jump" + n;
	}

	private static boolean looksAlreadyImported(ParkourMap restoredMap, LegacyEntry e) {
		if (restoredMap == null || restoredMap.getJumps() == null || e == null) return false;

		for (Jump j : restoredMap.getJumps()) {
			if (j == null) continue;
			if (j.getX() != e.posX || j.getY() != e.posY || j.getZ() != e.posZ) continue;

			// If same coords already exist in RestoredStrats, treat as already imported
			return true;
		}
		return false;
	}

	/**
	 * Insert legacy reminders.json (old mod) into RestoredStrats.
	 * Returns true if at least one entry was imported.
	 * Must never crash and must not loop when file is missing.
	 */
	public static boolean importIntoRestoredStrats() {
		DataStoreRepository.ensureLoaded();

		long nowMs = System.currentTimeMillis();
		if (nowMs - lastLegacyImportRunMs < LEGACY_IMPORT_DOUBLE_TRIGGER_GUARD_MS) {
			// Ignore fast duplicate calls (prevents double chat spam).
			return false;
		}
		lastLegacyImportRunMs = nowMs;

		if (legacyImportInProgress) {
			return false;
		}

		legacyImportInProgress = true;
		try {
			if (!legacyFileExistsOrReport()) {
				return false;
			}

			File f = getLegacyRemindersFile();

			Gson gson = new Gson();
			Type listType = new TypeToken<ArrayList<LegacyEntry>>() {}.getType();

			ArrayList<LegacyEntry> entries = null;
			try {
				entries = gson.fromJson(new FileReader(f), listType);
			} catch (JsonSyntaxException ex) {
				sendLegacyErrorOnce("Legacy file is not valid JSON: StratReminders/reminders.json");
				return false;
			} catch (IOException ex) {
				sendLegacyErrorOnce("Failed to read legacy file: StratReminders/reminders.json");
				return false;
			}

			if (entries == null || entries.isEmpty()) {
				sendLegacyErrorOnce("Legacy file is empty or contains no entries: StratReminders/reminders.json");
				return false;
			}

			ParkourMap restoredMap = BuiltinContainers.getRestoredMap();
			if (restoredMap == null) {
				sendLegacyErrorOnce("RestoredStrats map is not available.");
				return false;
			}
			if (restoredMap.getJumps() == null) {
				restoredMap.setJumps(new ArrayList<Jump>());
			}

			int imported = 0;
			int skipped = 0;

			for (LegacyEntry e : entries) {
				if (e == null || e.lines == null || e.lines.isEmpty()) {
					skipped++;
					continue;
				}

				if (looksAlreadyImported(restoredMap, e)) {
					skipped++;
					continue;
				}

				Jump j = new Jump();
				j.setShowJumpNameInWorld(false);
				j.setId(nextRestoredJumpName(restoredMap));
				j.setX(e.posX);
				j.setY(e.posY);
				j.setZ(e.posZ);

				ArrayList<String> normalized = normalizeLegacyLinesToNewFormat(e.lines);

				ArrayList<Reminder> rs = new ArrayList<Reminder>();
				rs.add(new Reminder(normalized));
				j.setReminders(rs);
				j.setActiveReminderIndex(0);

				restoredMap.getJumps().add(j);
				imported++;
			}

			if (imported > 0) {
				DataStoreRepository.save();
			}

			sendLegacyInfo("Legacy import completed. Imported: " + imported + ", skipped: " + skipped + ".");
			return imported > 0;

		} catch (Exception ex) {
			sendLegacyErrorOnce("Unexpected error during legacy import. See logs for details.");
			ex.printStackTrace();
			return false;
		} finally {
			legacyImportInProgress = false;
		}
	}

	private static ArrayList<String> normalizeLegacyLinesToNewFormat(ArrayList<String> legacy) {
		// New format (8): [Position, Facing, Setup, Strategy, Strafe, Turn, Author, Tips]
		ArrayList<String> out = new ArrayList<String>(8);

		String l0 = (legacy != null && legacy.size() > 0 && legacy.get(0) != null) ? legacy.get(0) : "";
		String l1 = (legacy != null && legacy.size() > 1 && legacy.get(1) != null) ? legacy.get(1) : "";

		// If it already looks like "new" or "prev" format, keep as-is (avoid double conversion).
		if (legacy != null && legacy.size() >= 6) {
			return new ArrayList<String>(legacy);
		}

		// Position, Facing
		out.add("");   // Position
		out.add("");   // Facing

		// Setup, Strategy
		out.add(l0);   // Setup
		out.add(l1);   // Strategy

		// Strafe, Turn, Author, Tips
		out.add("");   // Strafe
		out.add("");   // Turn
		out.add("");   // Author
		out.add("");   // Tips

		return out;
	}
}
