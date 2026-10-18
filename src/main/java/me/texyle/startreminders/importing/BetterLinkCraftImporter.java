package me.texyle.startreminders.importing;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import me.texyle.startreminders.StratReminders;
import me.texyle.startreminders.data.Jump;
import me.texyle.startreminders.data.ParkourMap;
import me.texyle.startreminders.persistence.BuiltinContainers;
import me.texyle.startreminders.persistence.DataStoreRepository;
import me.texyle.startreminders.reminders.Reminder;
import me.texyle.startreminders.utils.ChatUtils;

/** Imports strategies of BetterLinkCraft (BetterLinkCraft/StratReminders.json) into RestoredStrats. */
public final class BetterLinkCraftImporter {

	private BetterLinkCraftImporter() {}

	// -------------------------
	// BetterLinkCraft import guards
	// -------------------------

	private static boolean blcImportInProgress = false;
	private static long lastBlcImportRunMs = 0L;
	private static final long BLC_IMPORT_DOUBLE_TRIGGER_GUARD_MS = 600L;

	private static long lastBlcMissingChatMs = 0L;
	private static final long BLC_MISSING_CHAT_COOLDOWN_MS = 2500L;

	public static boolean importIntoRestoredStrats() {
		DataStoreRepository.ensureLoaded();

		long nowMs = System.currentTimeMillis();
		if (nowMs - lastBlcImportRunMs < BLC_IMPORT_DOUBLE_TRIGGER_GUARD_MS) {
			// Ignore fast duplicate calls (prevents double triggering).
			return false;
		}
		lastBlcImportRunMs = nowMs;

		if (blcImportInProgress) {
			return false;
		}

		blcImportInProgress = true;
		try {
			File f = getBetterLinkCraftFile();
			if (f == null || !f.exists() || !f.isFile()) {
				sendBlcErrorOnce("BetterLinkCraft file not found: BetterLinkCraft/StratReminders.json");
				return false;
			}

			String jsonText;
			try {
				jsonText = readFileUtf8(f);
			} catch (Exception ex) {
				sendBlcErrorOnce("Failed to read BetterLinkCraft file: BetterLinkCraft/StratReminders.json");
				return false;
			}

			if (jsonText == null || jsonText.trim().isEmpty()) {
				sendBlcErrorOnce("BetterLinkCraft file is empty: BetterLinkCraft/StratReminders.json");
				return false;
			}

			JsonElement rootEl;
			try {
				rootEl = new JsonParser().parse(jsonText);
			} catch (Exception ex) {
				sendBlcErrorOnce("BetterLinkCraft file is not valid JSON: BetterLinkCraft/StratReminders.json");
				return false;
			}

			if (rootEl == null || !rootEl.isJsonObject()) {
				sendBlcErrorOnce("BetterLinkCraft JSON must be an object: BetterLinkCraft/StratReminders.json");
				return false;
			}

			ParkourMap restoredMap = BuiltinContainers.getRestoredMap();
			if (restoredMap == null) {
				sendBlcErrorOnce("RestoredStrats map is not available.");
				return false;
			}
			if (restoredMap.getJumps() == null) {
				restoredMap.setJumps(new ArrayList<Jump>());
			}

			JsonObject root = rootEl.getAsJsonObject();

			int imported = 0;
			int skipped = 0;

			for (Map.Entry<String, JsonElement> groupEntry : root.entrySet()) {
				String groupName = (groupEntry.getKey() != null) ? groupEntry.getKey().trim() : "";
				if (groupName.isEmpty()) {
					continue;
				}

				JsonElement arrEl = groupEntry.getValue();
				if (arrEl == null || !arrEl.isJsonArray()) {
					continue;
				}

				int ordinal = 0;

				for (JsonElement itemEl : arrEl.getAsJsonArray()) {
					if (itemEl == null || !itemEl.isJsonObject()) {
						skipped++;
						continue;
					}

					JsonObject obj = itemEl.getAsJsonObject();
					ordinal++;

					String jumpName = groupName + ordinal;

					int x = getIntSafe(obj, "x", 0);
					int y = getIntSafe(obj, "y", 0);
					int z = getIntSafe(obj, "z", 0);

					// Same coords rule as legacy import: if coords already exist in RestoredStrats, treat as already imported.
					if (looksAlreadyImportedByCoords(restoredMap, x, y, z)) {
						skipped++;
						continue;
					}

					String position = getStringSafe(obj, "position");
					String facing = getStringSafe(obj, "facing");
					String setup = getStringSafe(obj, "setup");
					String input = getStringSafe(obj, "input");
					String comment = getStringSafe(obj, "comment");

					Jump j = new Jump();
					j.setShowJumpNameInWorld(false);
					j.setId(jumpName);
					j.setX(x);
					j.setY(y);
					j.setZ(z);

					ArrayList<String> lines = new ArrayList<String>(8);
					for (int i = 0; i < 8; i++) lines.add("");

					lines.set(0, position);
					lines.set(1, facing);
					lines.set(2, setup);
					lines.set(3, input);     // Strategy column
					lines.set(7, comment);   // Tips column

					ArrayList<Reminder> rs = new ArrayList<Reminder>();
					rs.add(new Reminder(lines));
					j.setReminders(rs);
					j.setActiveReminderIndex(0);

					restoredMap.getJumps().add(j);
					imported++;
				}
			}

			if (imported > 0) {
				DataStoreRepository.save();
			}

			sendBlcInfo("BetterLinkCraft import completed. Imported: " + imported + ", skipped: " + skipped + ".");
			return imported > 0;

		} catch (Exception ex) {
			sendBlcErrorOnce("Unexpected error during BetterLinkCraft import. See logs for details.");
			ex.printStackTrace();
			return false;
		} finally {
			blcImportInProgress = false;
		}
	}

	private static File getBetterLinkCraftFile() {
		Minecraft mc = Minecraft.getMinecraft();
		if (mc == null) {
			return new File("BetterLinkCraft/StratReminders.json");
		}
		File mcDir = mc.mcDataDir;
		return new File(new File(mcDir, "BetterLinkCraft"), "StratReminders.json");
	}

	private static void sendBlcErrorOnce(String msg) {
		long now = System.currentTimeMillis();
		if (now - lastBlcMissingChatMs < BLC_MISSING_CHAT_COOLDOWN_MS) {
			return;
		}
		lastBlcMissingChatMs = now;
		ChatUtils.sendChat(Minecraft.getMinecraft(),
				ChatUtils.ERROR_PREFIX + msg);
	}

	private static void sendBlcInfo(String msg) {
		ChatUtils.sendChat(Minecraft.getMinecraft(),
				ChatUtils.INFO_PREFIX + msg);
	}

	private static boolean looksAlreadyImportedByCoords(ParkourMap restoredMap, int x, int y, int z) {
		if (restoredMap == null || restoredMap.getJumps() == null) return false;

		for (Jump j : restoredMap.getJumps()) {
			if (j == null) continue;
			if (j.getX() == x && j.getY() == y && j.getZ() == z) {
				return true;
			}
		}
		return false;
	}

	private static String readFileUtf8(File f) throws Exception {
		FileInputStream fis = null;
		BufferedInputStream bis = null;

		try {
			fis = new FileInputStream(f);
			bis = new BufferedInputStream(fis);

			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buf = new byte[4096];
			int n;

			while ((n = bis.read(buf)) > 0) {
				out.write(buf, 0, n);
			}

			return new String(out.toByteArray(), "UTF-8");
		} finally {
			try { if (bis != null) bis.close(); } catch (Exception ignored) {}
			try { if (fis != null) fis.close(); } catch (Exception ignored) {}
		}
	}

	private static String getStringSafe(JsonObject obj, String key) {
		try {
			if (obj == null || key == null) return "";
			JsonElement el = obj.get(key);
			if (el == null || el.isJsonNull()) return "";
			if (!el.isJsonPrimitive()) return "";
			String s = el.getAsString();
			return s != null ? s : "";
		} catch (Exception ignored) {
			return "";
		}
	}

	private static int getIntSafe(JsonObject obj, String key, int def) {
		try {
			if (obj == null || key == null) return def;
			JsonElement el = obj.get(key);
			if (el == null || el.isJsonNull()) return def;
			if (!el.isJsonPrimitive()) return def;
			return el.getAsInt();
		} catch (Exception ignored) {
			return def;
		}
	}

}
