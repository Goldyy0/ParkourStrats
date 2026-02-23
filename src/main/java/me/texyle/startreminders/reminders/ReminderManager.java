package me.texyle.startreminders.reminders;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.util.Map;
import java.util.HashMap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonArray;

import me.texyle.startreminders.StratReminders;
import me.texyle.startreminders.data.DataStore;
import me.texyle.startreminders.data.Jump;
import me.texyle.startreminders.data.ParkourMap;
import me.texyle.startreminders.data.ServerProfile;
import me.texyle.startreminders.data.MapSection;
import me.texyle.startreminders.gui.hierarchy.GuiServerList;
import me.texyle.startreminders.utils.DrawUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import java.util.List;

import me.texyle.startreminders.sheets.CsvUtils;
import me.texyle.startreminders.sheets.SheetHeaderMatcher;
import me.texyle.startreminders.sheets.SheetSyncManager;
import me.texyle.startreminders.sheets.SheetJumpNameExtractor;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.util.Vec3;

public class ReminderManager {

	// Mod settings
	private static final String DATASET_DIR = "StratReminders/datasets";
	private static final String SETTINGS_FILE_PATH = DATASET_DIR + "/settings.json";

	public static boolean isEditPickModeInCrosshair() {
		ensureSettingsLoaded();
		if (settings == null) settings = new ModSettings();
		return settings.editPickModeInCrosshair;
	}

	public static void setEditPickModeInCrosshair(boolean enabled) {
		ensureSettingsLoaded();
		if (settings == null) settings = new ModSettings();
		settings.editPickModeInCrosshair = enabled;
		saveSettingsToFile();
	}

	// -------------------------
	// Global + Restored + Shared stores (ALWAYS loaded)
	// -------------------------

	private static DataStore globalStore;
	private static DataStore restoredStore;
	private static DataStore sharedStore;

	private static ServerProfile selectedServer;
	private static ParkourMap selectedMap;
	private static Jump selectedJump;

	private static boolean toggled = false;
	private static ModSettings settings;
	private static boolean isSettingsLoaded = false;

	// Global persistence file
	private static final String GLOBAL_SERVER_ID = "Global";
	private static final String GLOBAL_MAP_ID = "Global";
	private static final String GLOBAL_FILE_PATH = DATASET_DIR + "/global.json";

	// RestoredStrats persistence file (GUI container now; legacy conversion later)
	private static final String RESTORED_SERVER_ID = "RestoredStrats";
	private static final String RESTORED_MAP_ID = "RestoredStrats";
	private static final String RESTORED_FILE_PATH = DATASET_DIR + "/restored_strats.json";

	// Shared persistence file (replaces per-world/per-server datasets; always loaded)
	private static final String SHARED_FILE_PATH = DATASET_DIR + "/shared.json";

	// Prevent re-entrant ensure/load behavior from doing expensive work repeatedly
	private static boolean isGlobalLoaded = false;
	private static boolean isRestoredLoaded = false;
	private static boolean isSharedLoaded = false;

	// Nearby rendering radius (blocks)
	private static final double NEARBY_RADIUS_BLOCKS = 24.0D;
	private static final double NEARBY_RADIUS_SQ = NEARBY_RADIUS_BLOCKS * NEARBY_RADIUS_BLOCKS;

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
	// BetterLinkCraft import guards
	// -------------------------

	private static boolean blcImportInProgress = false;
	private static long lastBlcImportRunMs = 0L;
	private static final long BLC_IMPORT_DOUBLE_TRIGGER_GUARD_MS = 600L;

	private static long lastBlcMissingChatMs = 0L;
	private static final long BLC_MISSING_CHAT_COOLDOWN_MS = 2500L;

	// -------------------------
	// Section Sync
	// -------------------------

	private static final String SECTION_SYNC_ENDPOINT =
			"https://script.google.com/macros/s/AKfycbyTqmi25lLkfqAqfmcNGDYK7IRwBU-40XWaAwIBNFfkX45LuH-cTjwi2OzMCAw5ElYk/exec";
	private static String lastSectionSyncError = "";

	public static String getLastSectionSyncError() {
		return lastSectionSyncError != null ? lastSectionSyncError : "";
	}

	private static void setSectionSyncError_(String msg) {
		lastSectionSyncError = (msg != null) ? msg : "";
	}

	public static boolean syncSectionsFromAppsScript(ParkourMap map, String sheetUrl) {
		ensureStoresInitialized();
		setSectionSyncError_("");

		if (map == null) {
			setSectionSyncError_("Map is null.");
			postSectionSyncErrorToChat_();
			return false;
		}

		String u = (sheetUrl != null) ? sheetUrl.trim() : "";
		if (u.isEmpty()) {
			setSectionSyncError_("Spreadsheet URL is empty.");
			postSectionSyncErrorToChat_();
			return false;
		}

		try {
			String url = SECTION_SYNC_ENDPOINT + "?sheetUrl=" + urlEncode_(u);

			String json = httpGetUtf8_(url);
			if (json == null || json.trim().isEmpty()) {
				setSectionSyncError_("Empty response from Apps Script.");
				postSectionSyncErrorToChat_();
				return false;
			}

			boolean ok = applySectionsFromJson_(map, json);
			if (!ok) {
				// Prefer explicit "error" returned by Apps Script
				String err = tryReadErrorFromJson_(json);
				if (err != null && err.trim().length() > 0) {
					setSectionSyncError_(err.trim());
				} else {
					setSectionSyncError_("Failed to apply sections from JSON.");
				}

				postSectionSyncErrorToChat_();
				return false;
			}

			map.setLastSheetSyncMs(System.currentTimeMillis());
			saveToFile();
			return true;

		} catch (Throwable t) {
			t.printStackTrace();
			setSectionSyncError_(String.valueOf(t));
			postSectionSyncErrorToChat_();
			return false;
		}
	}

	private static String httpGetUtf8_(String urlStr) throws Exception {
		java.net.URL url = new java.net.URL(urlStr);
		java.net.HttpURLConnection con = (java.net.HttpURLConnection) url.openConnection();
		con.setRequestMethod("GET");
		con.setConnectTimeout(8000);
		con.setReadTimeout(15000);
		con.setUseCaches(false);

		java.io.InputStream in = con.getInputStream();
		try {
			java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(in, "UTF-8"));
			StringBuilder sb = new StringBuilder();
			String line;
			while ((line = br.readLine()) != null) {
				sb.append(line);
			}
			return sb.toString();
		} finally {
			try { in.close(); } catch (Throwable ignored) {}
		}
	}

	private static String urlEncode_(String s) throws Exception {
		return java.net.URLEncoder.encode(s != null ? s : "", "UTF-8");
	}

	private static class JumpCellView {
		final String v;
		final int bg;

		JumpCellView(String v, int bg) {
			this.v = v != null ? v : "";
			this.bg = bg;
		}
	}

	private static boolean applySectionsFromJson_(ParkourMap map, String json) {
		if (map == null) return false;

		JsonObject root = new JsonParser().parse(json).getAsJsonObject();
		if (root == null) return false;

		if (!root.has("ok") || !root.get("ok").getAsBoolean()) {
			return false;
		}

		JsonArray header = root.getAsJsonArray("header");
		JsonArray rows = root.getAsJsonArray("rows");
		if (header == null || rows == null) return false;

		int jumpsCount = (map.getJumps() != null) ? map.getJumps().size() : 0;
		if (jumpsCount <= 0) return false;

		int maxLevels = map.getMaxSectionLevelsAllowed();
		if (maxLevels < 1) maxLevels = 1;
		if (maxLevels > 4) maxLevels = 4;

		// Build header->column index lookup
		HashMap<String, Integer> colByName = new HashMap<String, Integer>();
		for (int c = 0; c < header.size(); c++) {
			String h = header.get(c).getAsString();
			if (h != null) {
				String key = h.trim();
				if (!key.isEmpty()) {
					colByName.put(key, Integer.valueOf(c));
				}
			}
		}

		// ------------------------------------------------------------
		// CRITICAL FIX:
		// Reduce sheet rows (strategies) to jump-level blocks (1 jump = 1 entry)
		// We detect new jump block by non-empty cell in "Jump" column.
		// ------------------------------------------------------------
		int jumpColIdx = -1;
		// Try common names (case-sensitive in map, but we try typical sheet naming)
		if (colByName.containsKey("Jump")) jumpColIdx = colByName.get("Jump").intValue();
		else if (colByName.containsKey("JUMP")) jumpColIdx = colByName.get("JUMP").intValue();
		else if (colByName.containsKey("jump")) jumpColIdx = colByName.get("jump").intValue();

		// Fallback: first column is usually Jump in your CSV export
		if (jumpColIdx < 0) jumpColIdx = 0;

		// Build jump blocks as [startRowInclusive, endRowInclusive]
		java.util.ArrayList<int[]> jumpBlocks = new java.util.ArrayList<int[]>();
		int blockStart = -1;

		for (int r = 0; r < rows.size(); r++) {
			JsonArray row = rows.get(r).getAsJsonArray();
			JsonObject jumpCell = (row != null && jumpColIdx >= 0 && jumpColIdx < row.size())
					? row.get(jumpColIdx).getAsJsonObject()
					: null;

			String jumpName = (jumpCell != null && jumpCell.has("v")) ? jumpCell.get("v").getAsString() : "";
			jumpName = (jumpName != null) ? jumpName.trim() : "";

			boolean startsNewBlock = !jumpName.isEmpty();

			if (startsNewBlock) {
				if (blockStart >= 0) {
					jumpBlocks.add(new int[] { blockStart, r - 1 });
				}
				blockStart = r;
			}
		}
		if (blockStart >= 0) {
			jumpBlocks.add(new int[] { blockStart, rows.size() - 1 });
		}

		// If the sheet starts with merged empty Jump cells and never starts a block, treat as one block.
		if (jumpBlocks.isEmpty() && rows.size() > 0) {
			jumpBlocks.add(new int[] { 0, rows.size() - 1 });
		}

		// Now we have jumpBlocks in sheet order. We only use up to jumpsCount (template size).
		int usableJumps = Math.min(jumpsCount, jumpBlocks.size());

		// For each section level, locate matching column by map's configured name.
		for (int levelOneBased = 1; levelOneBased <= maxLevels; levelOneBased++) {
			String colName = map.getSectionName(levelOneBased - 1);
			colName = (colName != null) ? colName.trim() : "";
			if (colName.isEmpty()) continue;

			Integer colIdxObj = colByName.get(colName);
			if (colIdxObj == null) {
				// Column missing -> do not touch existing sections on this level.
				continue;
			}
			int colIdx = colIdxObj.intValue();

			ArrayList<MapSection> target = map.getSectionsForLevel(levelOneBased);
			if (target == null) continue;
			target.clear();

			// Build jump-level cell view for this section column
			java.util.ArrayList<JumpCellView> jumpView = new java.util.ArrayList<JumpCellView>(usableJumps);

			for (int j = 0; j < usableJumps; j++) {
				int[] b = jumpBlocks.get(j);
				int r0 = b[0];
				int r1 = b[1];

				JumpCellView view = reduceBlockToJumpCell_(rows, r0, r1, colIdx);
				jumpView.add(view);
			}

			buildSectionsFromJumpView_(target, jumpView, usableJumps);
		}

		return true;
	}

	private static JumpCellView reduceBlockToJumpCell_(JsonArray rows, int r0, int r1, int colIdx) {
		if (rows == null) return new JumpCellView("", 0xFFFFFFFF);
		if (r0 < 0) r0 = 0;
		if (r1 < r0) r1 = r0;
		if (r1 >= rows.size()) r1 = rows.size() - 1;

		// 1) If any row in the block has a non-empty label, use THAT row's bg.
		for (int r = r0; r <= r1; r++) {
			JsonArray row = rows.get(r).getAsJsonArray();
			JsonObject cell = (row != null && colIdx >= 0 && colIdx < row.size()) ? row.get(colIdx).getAsJsonObject() : null;

			String v = (cell != null && cell.has("v")) ? cell.get("v").getAsString() : "";
			v = (v != null) ? v.trim() : "";

			if (!v.isEmpty()) {
				String bgHex = (cell != null && cell.has("bg")) ? cell.get("bg").getAsString() : "FFFFFFFF";
				int bg = parseArgbHex_(bgHex);
				return new JumpCellView(v, bg);
			}
		}

		// 2) No label in this jump block -> choose a stable bg for the block.
		// Use the most frequent bg (mode). Prefer non-white if present.
		java.util.HashMap<Integer, Integer> counts = new java.util.HashMap<Integer, Integer>();
		int bestBg = 0xFFFFFFFF;
		int bestCount = -1;

		int bestNonWhiteBg = 0xFFFFFFFF;
		int bestNonWhiteCount = -1;

		for (int r = r0; r <= r1; r++) {
			JsonArray row = rows.get(r).getAsJsonArray();
			JsonObject cell = (row != null && colIdx >= 0 && colIdx < row.size()) ? row.get(colIdx).getAsJsonObject() : null;

			String bgHex = (cell != null && cell.has("bg")) ? cell.get("bg").getAsString() : "FFFFFFFF";
			int bg = parseArgbHex_(bgHex);

			Integer prev = counts.get(bg);
			int n = (prev != null ? prev.intValue() : 0) + 1;
			counts.put(bg, Integer.valueOf(n));

			if (n > bestCount) {
				bestCount = n;
				bestBg = bg;
			}

			// "Non-white" heuristic: treat pure white as default background.
			// (We ignore alpha and compare RGB only.)
			if ( (bg & 0xFFFFFF) != 0xFFFFFF ) {
				if (n > bestNonWhiteCount) {
					bestNonWhiteCount = n;
					bestNonWhiteBg = bg;
				}
			}
		}

		int chosen = (bestNonWhiteCount >= 0) ? bestNonWhiteBg : bestBg;
		return new JumpCellView("", chosen);
	}

	private static void buildSectionsFromJumpView_(
			ArrayList<MapSection> out,
			java.util.ArrayList<JumpCellView> jumpView,
			int jumpsCount
	) {
		if (out == null || jumpView == null) return;
		if (jumpsCount <= 0) return;

		String activeName = null;
		int activeColor = 0xFFFFFFFF;
		int activeStart = -1;

		int usable = Math.min(jumpsCount, jumpView.size());

		for (int j = 0; j < usable; j++) {
			JumpCellView cell = jumpView.get(j);
			String v = (cell != null) ? cell.v : "";
			int bg = (cell != null) ? cell.bg : 0xFFFFFFFF;

			v = (v != null) ? v.trim() : "";
			boolean hasLabel = !v.isEmpty();

			if (activeName == null) {
				if (hasLabel) {
					activeName = v;
					activeColor = bg;
					activeStart = j;
				}
				continue;
			}

			// We are inside a section
			if (hasLabel) {
				// New section starts when either name or color changes
				if (!v.equals(activeName) || (bg & 0xFFFFFF) != (activeColor & 0xFFFFFF)) {
					addSectionIfValid_(out, activeName, activeColor, activeStart, j - 1, jumpsCount);

					activeName = v;
					activeColor = bg;
					activeStart = j;
				}
				continue;
			}

			// Empty text cell: keep section only if background matches
			if ((bg & 0xFFFFFF) != (activeColor & 0xFFFFFF)) {
				addSectionIfValid_(out, activeName, activeColor, activeStart, j - 1, jumpsCount);

				activeName = null;
				activeColor = 0xFFFFFFFF;
				activeStart = -1;
			}
		}

		// Close trailing section
		if (activeName != null && activeStart >= 0) {
			addSectionIfValid_(out, activeName, activeColor, activeStart, usable - 1, jumpsCount);
		}
	}

	private static void addSectionIfValid_(
			ArrayList<MapSection> out,
			String name,
			int colorArgb,
			int start,
			int end,
			int jumpsCount
	) {
		if (out == null) return;

		String n = (name != null) ? name.trim() : "";
		if (n.isEmpty()) return;

		if (start < 0 || end < 0) return;
		if (end < start) return;

		// Safety: do not create if range exceeds available jumps
		if (start >= jumpsCount) return;
		if (end >= jumpsCount) return;

		MapSection s = new MapSection();
		s.setId(makeSectionId_());
		s.setName(n);

		// Ensure opaque
		s.setColorArgb(colorArgb);

		// Auto text color (black/white)
		s.setTextColorArgb(pickTextColorForBg_(s.getColorArgb()));

		s.setStartJumpIndex(start);
		s.setEndJumpIndex(end);

		out.add(s);
	}

	private static String makeSectionId_() {
		// Simple unique id that is stable enough for JSON persistence
		return "sec_" + System.currentTimeMillis() + "_" + (int) (Math.random() * 1000000.0);
	}

	private static int parseArgbHex_(String hex) {
		try {
			String h = (hex != null) ? hex.trim() : "";
			if (h.startsWith("#")) h = h.substring(1);

			if (h.length() == 8) {
				return (int) Long.parseLong(h, 16);
			}
			if (h.length() == 6) {
				return (int) Long.parseLong("FF" + h, 16);
			}
		} catch (Throwable ignored) {}

		return 0xFFFFFFFF;
	}

	private static int pickTextColorForBg_(int argb) {
		int r = (argb >> 16) & 0xFF;
		int g = (argb >> 8) & 0xFF;
		int b = (argb) & 0xFF;

		int y = (int) (0.2126 * r + 0.7152 * g + 0.0722 * b);

		return (y >= 140) ? 0xFF000000 : 0xFFFFFFFF;
	}

	private static String tryReadErrorFromJson_(String json) {
		try {
			JsonElement el = new JsonParser().parse(json);
			if (el == null || !el.isJsonObject()) return "";
			JsonObject obj = el.getAsJsonObject();

			if (!obj.has("error")) return "";
			JsonElement errEl = obj.get("error");
			if (errEl == null) return "";
			String err = errEl.getAsString();
			return err != null ? err.trim() : "";
		} catch (Throwable ignored) {
			return "";
		}
	}

	// -------------------------
	// Google Sheets sync
	// -------------------------

	// Tracks which (server,map) already announced the first successful sync in chat.
	private static final Set<String> sheetSyncAnnouncedKeys = new HashSet<String>();

	private static final String SHEETS_CACHE_DIR = "StratReminders/sheets_cache";

	private static class ResolvedJump {
		final Jump jump;
		final boolean allowBind; // true = safe to persist sheetRowKey, false = "approx/guess" mapping

		ResolvedJump(Jump jump, boolean allowBind) {
			this.jump = jump;
			this.allowBind = allowBind;
		}
	}

	// -------------------------
	// World / network lifecycle
	// -------------------------

	@SubscribeEvent
	public void onWorldLoad(WorldEvent.Load event) {
		ensureStoresInitialized();
		autoSelectDefaultContext();
	}

	@SubscribeEvent
	public void onWorldUnload(WorldEvent.Unload event) {
		saveCurrentStoresBestEffort();

		selectedServer = null;
		selectedMap = null;
		selectedJump = null;
	}

	@SubscribeEvent
	public void onClientConnected(FMLNetworkEvent.ClientConnectedToServerEvent event) {
		ensureStoresInitialized();
		autoSelectDefaultContext();
	}

	@SubscribeEvent
	public void onClientDisconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
		saveCurrentStoresBestEffort();

		selectedServer = null;
		selectedMap = null;
		selectedJump = null;
	}

	private static void saveCurrentStoresBestEffort() {
		try {
			saveToFile();
		} catch (Exception ignored) {
		}
	}

	public static void setSheetUrlForMap(ParkourMap map, String url) {
		ensureStoresInitialized();
		if (map == null) return;

		String trimmed = url != null ? url.trim() : "";
		map.setSheetUrl(trimmed);
		map.setSheetGid(-1); // gid is parsed during download, unless user provides URL with gid
		saveToFile();
	}

	public static void stopSheetSyncForMap(ServerProfile server, ParkourMap map) {
		ensureStoresInitialized();
		if (map == null) return;

		map.setSheetUrl("");
		map.setSheetGid(-1);
		map.setLastSheetSyncMs(0L);

		// Reset "first sync announced" so that if user re-enables sync later,
		// the first successful auto-sync can announce again.
		sheetSyncAnnouncedKeys.remove(buildSheetAnnounceKey(server, map));

		saveToFile();
	}

	private static void ensureSettingsLoaded() {
		if (isSettingsLoaded && settings != null) {
			return;
		}

		Gson gson = new Gson();
		boolean needsSave = false;

		try {
			File file = new File(SETTINGS_FILE_PATH);
			File parent = file.getParentFile();
			if (parent != null && !parent.exists()) parent.mkdirs();

			boolean created = file.createNewFile();
			if (created) {
				settings = new ModSettings();
				needsSave = true;
			} else {
				ModSettings loaded = null;
				try {
					loaded = gson.fromJson(new FileReader(file), ModSettings.class);
				} catch (JsonSyntaxException ignored) {
					loaded = null;
				}

				if (loaded == null) {
					settings = new ModSettings();
					needsSave = true;
				} else {
					settings = loaded;
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
			saveSettingsToFile();
		}

		isSettingsLoaded = true;
	}

	private static void saveSettingsToFile() {
		if (settings == null) return;

		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		String json = gson.toJson(settings);

		try {
			File file = new File(SETTINGS_FILE_PATH);
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

	/**
	 * Fixes header matching issues by applying strict, index-based fallback lookups.
	 * This prevents cases where "by" is not recognized as Author and ends up in Tips.
	 */
	private static void applyHeaderFallbacks(List<String> header, SheetHeaderMatcher.Columns cols) {
		if (header == null || cols == null) return;

		// Jump is mandatory; do not "guess" it too aggressively.
		if (cols.jump < 0) {
			int j = findHeaderIndex(header, "jump");
			cols.jump = j;
		}

		// Common synonyms / variants (case-insensitive, trim)
		if (cols.position < 0) cols.position = findHeaderIndex(header, "position", "pos");
		if (cols.facing < 0) cols.facing = findHeaderIndex(header, "facing", "face");
		if (cols.setup < 0) cols.setup = findHeaderIndex(header, "setup");
		if (cols.strategy < 0) cols.strategy = findHeaderIndex(header, "strategy", "strat");
		if (cols.strafe < 0) cols.strafe = findHeaderIndex(header, "strafe");
		if (cols.turn < 0) cols.turn = findHeaderIndex(header, "turn");
		// Critical: accept "by" as Author
		if (cols.author < 0) cols.author = findHeaderIndex(header, "by", "author", "creator");
		if (cols.tips < 0) cols.tips = findHeaderIndex(header, "tips", "notes", "note");
	}

	private static int findHeaderIndex(List<String> header, String... candidates) {
		if (header == null || candidates == null) return -1;

		for (int i = 0; i < header.size(); i++) {
			String h = header.get(i);
			String norm = normalizeHeader(h);
			if (norm.isEmpty()) continue;

			for (String c : candidates) {
				if (c == null) continue;
				String cn = normalizeHeader(c);
				if (cn.isEmpty()) continue;

				if (norm.equals(cn)) {
					return i;
				}
			}
		}
		return -1;
	}

	private static String normalizeHeader(String s) {
		if (s == null) return "";
		return s.trim().toLowerCase();
	}

	private static int findHeaderRowIndex(List<List<String>> rows) {
		if (rows == null || rows.isEmpty()) return -1;

		int maxScan = Math.min(10, rows.size());
		int bestRow = -1;
		int bestScore = 0;

		for (int r = 0; r < maxScan; r++) {
			List<String> row = rows.get(r);
			if (row == null || row.isEmpty()) continue;

			int score = scoreHeaderRow(row);

			if (score > bestScore) {
				bestScore = score;
				bestRow = r;
			}
		}

		// Require at least a minimal confidence
		return (bestScore >= 4) ? bestRow : -1;
	}

	private static int scoreHeaderRow(List<String> row) {
		int score = 0;

		for (String cell : row) {
			if (cell == null) continue;
			String k = CsvUtils.normalizeKey(cell);

			if (k.isEmpty()) continue;

			// Strong indicators
			if (k.equals("jump") || k.equals("jumpname") || k.equals("jumptitle")) {
				score += 2;
				continue;
			}
			if (k.equals("strategy") || k.equals("strat")) {
				score += 2;
				continue;
			}

			// Medium indicators
			if (k.equals("position") || k.equals("pos")) score += 1;
			else if (k.equals("facing") || k.equals("face")) score += 1;
			else if (k.equals("setup")) score += 1;
			else if (k.equals("strafe")) score += 1;
			else if (k.equals("turn")) score += 1;
			else if (k.equals("author") || k.equals("by")) score += 1;
			else if (k.equals("tips") || k.equals("notes") || k.equals("comment")) score += 1;

				// Section-related (generic, works with dynamic names too)
			else if (k.contains("section")) score += 1;
			else if (k.contains("sub")) score += 1;
			else if (k.equals("area")) score += 1;
			else if (k.equals("cp") || k.equals("checkpoint")) score += 1;
			else if (k.equals("sp") || k.equals("stagepoint")) score += 1;
		}

		return score;
	}

	private static void applyCsvToMap(ServerProfile server, ParkourMap map, String csvText, boolean announceInChat) {

		if (map == null || csvText == null) {
			return;
		}

		// If user stopped sync while the request was in-flight, do not apply.
		if (!map.hasSheetConfigured()) {
			return;
		}

		List<List<String>> rows = CsvUtils.parseCsv(csvText);
		if (rows == null || rows.size() < 2) {
			if (announceInChat) {
				sendChat(Minecraft.getMinecraft(),
						EnumChatFormatting.DARK_AQUA + "[ParkourStrats] " + EnumChatFormatting.RED + "CSV has no data rows.");
			}
			return;
		}

		int headerRowIndex = findHeaderRowIndex(rows);
		if (headerRowIndex < 0) {
			if (announceInChat) {
				sendChat(Minecraft.getMinecraft(),
						EnumChatFormatting.DARK_AQUA + "[ParkourStrats] " + EnumChatFormatting.RED
								+ "CSV header row not found (expected columns like 'jump', 'strategy').");
			}
			return;
		}

		int dataStartRow = headerRowIndex + 1;
		if (dataStartRow >= rows.size()) {
			if (announceInChat) {
				sendChat(Minecraft.getMinecraft(),
						EnumChatFormatting.DARK_AQUA + "[ParkourStrats] " + EnumChatFormatting.RED
								+ "CSV has header but no data rows.");
			}
			return;
		}

		List<String> header = rows.get(headerRowIndex);
		SheetHeaderMatcher.Columns cols = SheetHeaderMatcher.match(header);

		// Strengthen matching with strict, index-based fallbacks (supports "by" etc.)
		applyHeaderFallbacks(header, cols);

		if (cols.jump < 0) {
			if (announceInChat) {
				sendChat(Minecraft.getMinecraft(),
						EnumChatFormatting.DARK_AQUA + "[ParkourStrats] " + EnumChatFormatting.RED + "CSV missing 'Jump' column.");
			}
			return;
		}

		// Section columns are derived from map config (robust: supports any names, and all levels 1...max)
		int maxLevels = 0;
		try { maxLevels = map.getMaxSectionLevelsAllowed(); } catch (Throwable ignored) {}
		if (maxLevels < 0) maxLevels = 0;
		if (maxLevels > 4) maxLevels = 4;

		int[] sectionCols = new int[maxLevels];
		for (int i = 0; i < maxLevels; i++) sectionCols[i] = -1;

		for (int lvl = 1; lvl <= maxLevels; lvl++) {
			String colName = map.getSectionName(lvl - 1);
			colName = (colName != null) ? colName.trim() : "";
			if (colName.isEmpty()) continue;
			sectionCols[lvl - 1] = findHeaderCol(header, colName);
		}

		// 1) Build template index by (sectionPathKey -> (normJumpName -> list<Jump> in template order))
		TemplateIndexContext templateIndex = buildTemplateIndexContextBySections(map);

		// 2) Build sheet blocks (1 jump block -> N strategy rows) with carry-down for merged cells
		// IMPORTANT: start from dataStartRow, not from row 1
		ArrayList<SheetJumpBlock> blocks = buildSheetJumpBlocks(rows, dataStartRow, cols.jump, sectionCols, maxLevels);

		int imported = 0;
		int skipped = 0;

		// Used to avoid mapping the same template jump multiple times in a single sync run (duplicates)
		java.util.HashSet<Jump> assignedThisRun = new java.util.HashSet<Jump>();

		for (int b = 0; b < blocks.size(); b++) {
			SheetJumpBlock block = blocks.get(b);
			if (block == null) continue;

			String jumpName = (block.jumpName != null) ? block.jumpName.trim() : "";
			if (jumpName.isEmpty()) {
				skipped += Math.max(1, block.rows.size());
				continue;
			}

			// Resolve jump deterministically within same section path
			ResolvedJump resolved = resolveJumpSectionAware(
					jumpName,
					block.sectionKey,
					block.sheetRowKey,
					templateIndex,
					assignedThisRun
			);

			Jump target = (resolved != null) ? resolved.jump : null;

			// Bind only when we are confident and only if the jump is not already bound to a different key.
			if (target != null && resolved.allowBind && block.sheetRowKey != null && block.sheetRowKey.trim().length() > 0) {
				String existingKey = target.getSheetRowKey();
				if (existingKey == null) existingKey = "";
				existingKey = existingKey.trim();

				String incomingKey = block.sheetRowKey.trim();

				if (existingKey.isEmpty()) {
					target.setSheetRowKey(incomingKey); // bind only once
				} else if (existingKey.equals(incomingKey)) {
					// ok, already bound
				} else {
					// do not rebind (prevents accidental flips)
				}
			}

			if (target == null) {
				// Requirement: do not create new jumps without coords
				skipped += Math.max(1, block.rows.size());
				continue;
			}

			// Mark assigned so duplicates map deterministically
			assignedThisRun.add(target);

			// Apply all strategy rows of this block
			for (int r = 0; r < block.rows.size(); r++) {
				List<String> row = block.rows.get(r);
				if (row == null) {
					skipped++;
					continue;
				}

				// Always map by column index (never by "non-empty count"), so empty Strafe/Turn do not shift Author/Tips.
				ArrayList<String> lines = new ArrayList<String>(8);
				lines.add(getByCol(row, cols.position));
				lines.add(getByCol(row, cols.facing));
				lines.add(getByCol(row, cols.setup));
				lines.add(getByCol(row, cols.strategy));
				lines.add(getByCol(row, cols.strafe));
				lines.add(getByCol(row, cols.turn));
				lines.add(getByCol(row, cols.author));
				lines.add(getByCol(row, cols.tips));

				lines = ensureEightLines(lines);

				// Skip empty strategies (all fields empty)
				boolean anyText = false;
				for (String s : lines) {
					if (s != null && s.trim().length() > 0) {
						anyText = true;
						break;
					}
				}
				if (!anyText) {
					skipped++;
					continue;
				}

				// Remove placeholders before first real import into that jump
				removePlaceholderReminders(target);

				// Strategy identity is core 0..5. Author/Tips (6/7) are metadata that can change without creating a new strategy.
				Reminder existing = findReminderByCore6(target, lines);
				if (existing != null) {
					updateAuthorAndTipsIfChanged(existing, lines);
					imported++;
					continue;
				}

				if (target.getReminders() == null) {
					target.setReminders(new ArrayList<Reminder>());
				}

				target.getReminders().add(new Reminder(lines));
				if (target.getActiveReminderIndex() < 0) {
					target.setActiveReminderIndex(0);
				}

				imported++;
			}
		}

		map.setLastSheetSyncMs(System.currentTimeMillis());
		saveToFile();

		if (announceInChat) {
			sendChat(Minecraft.getMinecraft(),
					EnumChatFormatting.DARK_AQUA + "[ParkourStrats] " + EnumChatFormatting.AQUA +
							"Sheet sync completed. Imported: " + imported + ", skipped: " + skipped + ".");
		}

		// Mark that we already announced the first sync.
		if (announceInChat) {
			sheetSyncAnnouncedKeys.add(buildSheetAnnounceKey(server, map));
		}
	}

	// -------------------------
// Section-aware CSV blocks + matching
// -------------------------

	private static final class SheetJumpBlock {
		String jumpName;       // carried down
		String sectionKey;     // normalized section path key
		String sheetRowKey;    // section-aware persistent identity: (jump+sectionPath)+#occurrence
		final ArrayList<List<String>> rows = new ArrayList<List<String>>();
	}

	private static String buildSectionKeyFromValues(String section, String subSection, String area, String cp, String sp) {
		StringBuilder sb = new StringBuilder();

		// Keep the same labels you already use in buildOccurrenceKey(...)
		if (section != null && !section.trim().isEmpty()) {
			sb.append("|S=").append(CsvUtils.normalizeKey(section));
		}
		if (subSection != null && !subSection.trim().isEmpty()) {
			sb.append("|SS=").append(CsvUtils.normalizeKey(subSection));
		}
		if (area != null && !area.trim().isEmpty()) {
			sb.append("|A=").append(CsvUtils.normalizeKey(area));
		}
		if (cp != null && !cp.trim().isEmpty()) {
			sb.append("|CP=").append(CsvUtils.normalizeKey(cp));
		}
		if (sp != null && !sp.trim().isEmpty()) {
			sb.append("|SP=").append(CsvUtils.normalizeKey(sp));
		}

		return sb.toString();
	}

	private static ArrayList<SheetJumpBlock> buildSheetJumpBlocks(
			List<List<String>> rows,
			int startRow,
			int colJump,
			int[] sectionCols,
			int maxLevels
	) {
		ArrayList<SheetJumpBlock> out = new ArrayList<SheetJumpBlock>();
		if (rows == null || rows.isEmpty()) return out;

		// Safety: never start before row 1 (row 0 can be random, and header may be above startRow anyway)
		int r0 = startRow;
		if (r0 < 1) r0 = 1;
		if (r0 >= rows.size()) return out;

		String lastJumpName = "";

		// Carry-down for section columns (merged cells)
		String[] lastLevelVals = new String[Math.max(0, maxLevels)];
		for (int i = 0; i < lastLevelVals.length; i++) lastLevelVals[i] = "";

		// Occurrence counter per (jumpName + sectionPathKey), increment only at new jump blocks
		java.util.HashMap<String, Integer> occurrenceByKey = new java.util.HashMap<String, Integer>();

		SheetJumpBlock current = null;

		for (int r = r0; r < rows.size(); r++) {
			List<String> row = rows.get(r);
			if (row == null) continue;

			String rawJumpCell = (colJump >= 0) ? CsvUtils.getCell(row, colJump).trim() : "";
			boolean isNewJumpBlock = rawJumpCell.length() > 0;

			String jumpName = rawJumpCell;
			if (jumpName.isEmpty()) {
				jumpName = lastJumpName; // carry-down for merged cells
			} else {
				lastJumpName = jumpName;
			}

			if (jumpName == null || jumpName.trim().isEmpty()) {
				// If sheet starts with empties, we cannot create a valid block yet.
				continue;
			}

			// Read section path values (L1...Ln) with carry-down
			String[] pathVals = new String[Math.max(0, maxLevels)];
			boolean hasAnyPathVal = false;

			for (int i = 0; i < maxLevels; i++) {
				String v = "";
				int col = (sectionCols != null && i < sectionCols.length) ? sectionCols[i] : -1;

				if (col >= 0) {
					String raw = CsvUtils.getCell(row, col);
					raw = (raw != null) ? raw.trim() : "";

					if (raw.isEmpty()) {
						// Carry-down (merged cells)
						v = lastLevelVals[i];
					} else {
						// New value on this level
						v = raw;
						lastLevelVals[i] = raw;

						// IMPORTANT: changing a higher level invalidates all lower levels
						for (int j = i + 1; j < maxLevels; j++) {
							lastLevelVals[j] = "";
						}
					}
				}

				pathVals[i] = (v != null) ? v : "";
				if (!pathVals[i].trim().isEmpty()) hasAnyPathVal = true;
			}

			// Build section key from all available levels (strongest)
			String sectionKey = buildSectionKeyFromPathValues(pathVals, maxLevels, hasAnyPathVal);

			if (isNewJumpBlock || current == null) {
				current = new SheetJumpBlock();
				current.jumpName = jumpName;
				current.sectionKey = sectionKey;

				// section-aware identity: normalized jump name + section path
				String baseKey = CsvUtils.normalizeKey(jumpName) + sectionKey;

				Integer occObj = occurrenceByKey.get(baseKey);
				int occ = (occObj != null) ? (occObj.intValue() + 1) : 1;
				occurrenceByKey.put(baseKey, Integer.valueOf(occ));

				current.sheetRowKey = baseKey + "#" + occ;

				out.add(current);
			}

			current.rows.add(row);
		}

		return out;
	}

	private static final class TemplateIndexContext {
		// sectionKey -> normJumpName -> list of Jump in template order
		final java.util.HashMap<String, java.util.HashMap<String, java.util.ArrayList<Jump>>> bySection = new java.util.HashMap<String, java.util.HashMap<String, java.util.ArrayList<Jump>>>();

		// For fast persisted binding lookup
		final java.util.HashMap<String, Jump> bySheetRowKey = new java.util.HashMap<String, Jump>();
	}

	private static TemplateIndexContext buildTemplateIndexContextBySections(ParkourMap map) {
		TemplateIndexContext ctx = new TemplateIndexContext();
		if (map == null || map.getJumps() == null) return ctx;

		ArrayList<Jump> jumps = map.getJumps();

		for (int i = 0; i < jumps.size(); i++) {
			Jump j = jumps.get(i);
			if (j == null) continue;

			// Require coords (per your rule)
			if (j.getX() == 0 && j.getY() == 0 && j.getZ() == 0) continue;

			// Persisted key index (strongest)
			String k = j.getSheetRowKey();
			if (k != null) {
				k = k.trim();
				if (!k.isEmpty() && !ctx.bySheetRowKey.containsKey(k)) {
					ctx.bySheetRowKey.put(k, j);
				}
			}

			// Section path for this template jump index
			String sectionKey = buildSectionKeyForTemplateIndex(map, i);
			String normName = CsvUtils.normalizeKey(j.getId());

			java.util.HashMap<String, java.util.ArrayList<Jump>> byName = ctx.bySection.get(sectionKey);
			if (byName == null) {
				byName = new java.util.HashMap<String, java.util.ArrayList<Jump>>();
				ctx.bySection.put(sectionKey, byName);
			}

			java.util.ArrayList<Jump> list = byName.get(normName);
			if (list == null) {
				list = new java.util.ArrayList<Jump>();
				byName.put(normName, list);
			}
			list.add(j);
		}

		return ctx;
	}

	private static String buildSectionKeyForTemplateIndex(ParkourMap map, int jumpIndex) {
		if (map == null) return "";

		int max = 0;
		try { max = map.getMaxSectionLevelsAllowed(); } catch (Throwable ignored) { }
		if (max < 1) return "";
		if (max > 4) max = 4;

		String[] pathVals = new String[max];
		boolean hasAny = false;

		for (int lvl = 1; lvl <= max; lvl++) {
			String v = getSectionNameAtIndex(map, lvl, jumpIndex);
			v = (v != null) ? v.trim() : "";
			pathVals[lvl - 1] = v;
			if (!v.isEmpty()) hasAny = true;
		}

		return buildSectionKeyFromPathValues(pathVals, max, hasAny);
	}

	private static String buildSectionKeyFromPathValues(String[] pathVals, int maxLevels, boolean hasAnyPathVal) {
		if (!hasAnyPathVal) return "";

		StringBuilder sb = new StringBuilder();

		int usable = Math.min(maxLevels, pathVals != null ? pathVals.length : 0);
		for (int i = 0; i < usable; i++) {
			String v = pathVals[i];
			v = (v != null) ? v.trim() : "";
			if (v.isEmpty()) continue;

			sb.append("|L").append(i + 1).append("=").append(CsvUtils.normalizeKey(v));
		}

		return sb.toString();
	}

	private static String getSectionNameAtIndex(ParkourMap map, int levelOneBased, int jumpIndex) {
		if (map == null) return "";
		ArrayList<MapSection> secs = map.getSectionsForLevel(levelOneBased);
		if (secs == null || secs.isEmpty()) return "";

		MapSection s = findSectionContainingIndex(secs, jumpIndex);
		if (s == null) return "";

		String n = s.getName();
		return (n != null) ? n.trim() : "";
	}

	private static ResolvedJump resolveJumpSectionAware(
			String jumpName,
			String sectionKey,
			String sheetRowKey,
			TemplateIndexContext templateIndex,
			java.util.HashSet<Jump> assignedThisRun
	) {
		if (jumpName == null) return null;
		if (sectionKey == null) sectionKey = "";
		if (templateIndex == null) return null;

		// 1) Prefer persisted binding (strongest)
		if (sheetRowKey != null) {
			String k = sheetRowKey.trim();
			if (!k.isEmpty()) {
				Jump bound = templateIndex.bySheetRowKey.get(k);
				if (bound != null) {
					return new ResolvedJump(bound, true);
				}
			}
		}

		String norm = CsvUtils.normalizeKey(jumpName);

		java.util.HashMap<String, java.util.ArrayList<Jump>> byName = templateIndex.bySection.get(sectionKey);
		java.util.ArrayList<Jump> candidates = (byName != null) ? byName.get(norm) : null;

		if (candidates == null || candidates.isEmpty()) {
			// No exact candidates in this section.
			// DO NOT fall back globally.
			return null;
		}

		// 2) Deterministic duplicate handling: first unassigned in template order
		for (int i = 0; i < candidates.size(); i++) {
			Jump j = candidates.get(i);
			if (j == null) continue;
			if (assignedThisRun != null && assignedThisRun.contains(j)) continue;
			return new ResolvedJump(j, true);
		}

		// 3) If all candidates are already assigned (rare), just use the first (do not bind again)
		return new ResolvedJump(candidates.get(0), false);
	}

	// -------------------------
	// Placeholder jump-name sync (CreateJumpContext helper)
	// -------------------------

	public interface IPlaceholderJumpSyncCallback {
		void onSuccess(int count);
		void onError(String errorMessage);
	}

	private static final String PLACEHOLDER_SHEETS_CACHE_FILE = "StratReminders/sheets_cache/placeholder_jumps.csv";

	// Runtime cache: we persist only URL/index/enabled in ParkourMap; the list is kept in memory.
	private static java.util.List<String> placeholderJumpNames = new java.util.ArrayList<String>();

	// Runtime cache: sheet-driven Level 1 sections for placeholder pager
	private static java.util.List<SheetJumpNameExtractor.PlaceholderSection> placeholderSectionsL1
			= new java.util.ArrayList<SheetJumpNameExtractor.PlaceholderSection>();

	public static boolean isAnySheetSyncInProgress() {
		return SheetSyncManager.isSyncInProgress();
	}

	public static boolean isPlaceholderSyncEnabledForSelectedMap() {
		ensureStoresInitialized();
		ParkourMap map = selectedMap;
		return map != null && map.isPlaceholderSyncEnabled() && placeholderJumpNames != null && !placeholderJumpNames.isEmpty();
	}

	public static int getPlaceholderJumpCount() {
		if (placeholderJumpNames == null) return 0;
		return placeholderJumpNames.size();
	}

	public static int getPlaceholderJumpIndex() {
		ensureStoresInitialized();
		ParkourMap map = selectedMap;
		if (map == null) return 0;

		int idx = map.getPlaceholderJumpIndex();
		int count = getPlaceholderJumpCount();
		if (count <= 0) return 0;

		if (idx < 0) idx = 0;
		if (idx >= count) idx = count - 1;
		map.setPlaceholderJumpIndex(idx);
		return idx;
	}

	public static String getCurrentPlaceholderJumpName() {
		if (!isPlaceholderSyncEnabledForSelectedMap()) return "";
		int idx = getPlaceholderJumpIndex();
		if (placeholderJumpNames == null || placeholderJumpNames.isEmpty()) return "";
		if (idx < 0 || idx >= placeholderJumpNames.size()) return "";
		String s = placeholderJumpNames.get(idx);
		return s != null ? s : "";
	}

	public static int getCurrentPlaceholderSectionIndex() {
		if (placeholderSectionsL1 == null || placeholderSectionsL1.isEmpty()) return -1;

		int jumpIdx = getPlaceholderJumpIndex(); // clamps + persists map index
		int best = -1;

		// 1) Exact section containing the current jump index
		for (int i = 0; i < placeholderSectionsL1.size(); i++) {
			SheetJumpNameExtractor.PlaceholderSection s = placeholderSectionsL1.get(i);
			if (s == null) continue;

			int a = s.startJumpIndex;
			int b = s.endJumpIndex;

			if (a <= jumpIdx && jumpIdx <= b) {
				return i;
			}
		}

		// 2) Fallback: nearest previous section by start index
		int bestStart = Integer.MIN_VALUE;
		for (int i = 0; i < placeholderSectionsL1.size(); i++) {
			SheetJumpNameExtractor.PlaceholderSection s = placeholderSectionsL1.get(i);
			if (s == null) continue;

			int a = s.startJumpIndex;
			if (a <= jumpIdx && a > bestStart) {
				bestStart = a;
				best = i;
			}
		}

		// If jumpIdx is before the first section start, best stays -1 -> return 0 (safe default)
		if (best < 0) return 0;
		return best;
	}

	public static String getCurrentPlaceholderSectionName() {
		int idx = getCurrentPlaceholderSectionIndex();
		if (idx < 0) return "";

		if (placeholderSectionsL1 == null || idx >= placeholderSectionsL1.size()) return "";

		SheetJumpNameExtractor.PlaceholderSection s = placeholderSectionsL1.get(idx);
		if (s == null) return "";

		String nm = s.name;
		return nm != null ? nm : "";
	}

	public static void setPlaceholderJumpIndex(int idx) {
		ensureStoresInitialized();
		ParkourMap map = selectedMap;
		if (map == null) return;
		if (!map.isPlaceholderSyncEnabled()) return;

		int count = getPlaceholderJumpCount();
		if (count <= 0) return;

		int clamped = idx;
		if (clamped < 0) clamped = 0;
		if (clamped >= count) clamped = count - 1;

		map.setPlaceholderJumpIndex(clamped);
		saveToFile();
	}

	public static void prevPlaceholderJump() {
		ensureStoresInitialized();
		ParkourMap map = selectedMap;
		if (map == null) return;
		if (!map.isPlaceholderSyncEnabled()) return;

		int count = getPlaceholderJumpCount();
		if (count <= 0) return;

		int idx = getPlaceholderJumpIndex();
		idx--;
		if (idx < 0) idx = 0;
		map.setPlaceholderJumpIndex(idx);
		saveToFile();
	}

	public static void nextPlaceholderJump() {
		ensureStoresInitialized();
		ParkourMap map = selectedMap;
		if (map == null) return;
		if (!map.isPlaceholderSyncEnabled()) return;

		int count = getPlaceholderJumpCount();
		if (count <= 0) return;

		int idx = getPlaceholderJumpIndex();
		idx++;
		if (idx >= count) idx = count - 1;
		map.setPlaceholderJumpIndex(idx);
		saveToFile();
	}

	/**
	 * Called after "Create placeholder" is used, so next time the GUI opens
	 * the next jump name is prefilled.
	 */
	public static void advancePlaceholderAfterCreate() {
		ensureStoresInitialized();
		ParkourMap map = selectedMap;
		if (map == null) return;
		if (!map.isPlaceholderSyncEnabled()) return;

		int count = getPlaceholderJumpCount();
		if (count <= 0) return;

		int idx = getPlaceholderJumpIndex();
		idx++;
		if (idx >= count) idx = count - 1; // clamp at end
		map.setPlaceholderJumpIndex(idx);
		saveToFile();
	}

	/**
	 * Enables placeholder sync for a given map and persists the URL/index/enabled state.
	 * The actual list is fetched by requestPlaceholderJumpListSync(...).
	 */
	public static void enablePlaceholderSyncForMap(ParkourMap map, String url) {
		ensureStoresInitialized();
		if (map == null) return;

		String trimmed = url != null ? url.trim() : "";
		map.setPlaceholderSheetUrl(trimmed);
		map.setPlaceholderSyncEnabled(true);

		// Keep index within range later when list is loaded
		if (map.getPlaceholderJumpIndex() < 0) {
			map.setPlaceholderJumpIndex(0);
		}

		saveToFile();
	}

	public static void disablePlaceholderSyncForMap(ParkourMap map) {
		ensureStoresInitialized();
		if (map == null) return;

		map.setPlaceholderSyncEnabled(false);
		map.setPlaceholderSheetUrl("");
		map.setPlaceholderJumpIndex(0);

		placeholderJumpNames = new java.util.ArrayList<String>();
		placeholderSectionsL1 = new java.util.ArrayList<SheetJumpNameExtractor.PlaceholderSection>();

		saveToFile();
	}

	public static java.util.List<SheetJumpNameExtractor.PlaceholderSection> getPlaceholderSectionsL1() {
		if (placeholderSectionsL1 == null) {
			return new java.util.ArrayList<SheetJumpNameExtractor.PlaceholderSection>();
		}
		return placeholderSectionsL1;
	}

	public static int getPlaceholderSectionCount() {
		return placeholderSectionsL1 != null ? placeholderSectionsL1.size() : 0;
	}

	public static SheetJumpNameExtractor.PlaceholderSection getPlaceholderSectionForJumpIndex(int jumpIndex) {
		if (placeholderSectionsL1 == null) return null;

		for (int i = 0; i < placeholderSectionsL1.size(); i++) {
			SheetJumpNameExtractor.PlaceholderSection s = placeholderSectionsL1.get(i);
			if (s == null) continue;
			if (jumpIndex >= s.startJumpIndex && jumpIndex <= s.endJumpIndex) {
				return s;
			}
		}
		return null;
	}

	public static void jumpToPlaceholderSection(int sectionIndex) {
		ensureStoresInitialized();
		ParkourMap map = selectedMap;
		if (map == null) return;

		// Keep behavior consistent with other placeholder navigation.
		if (!map.isPlaceholderSyncEnabled()) return;

		if (placeholderSectionsL1 == null || placeholderSectionsL1.isEmpty()) return;

		int count = getPlaceholderJumpCount();
		if (count <= 0) return;

		int idx = sectionIndex;
		if (idx < 0) idx = 0;
		if (idx >= placeholderSectionsL1.size()) idx = placeholderSectionsL1.size() - 1;

		SheetJumpNameExtractor.PlaceholderSection s = placeholderSectionsL1.get(idx);
		if (s == null) return;

		// Delegate clamping + persistence to the canonical setter.
		setPlaceholderJumpIndex(s.startJumpIndex);
	}

	/**
	 * Downloads CSV from placeholderSheetUrl and extracts jump names in sheet order.
	 * Stores the list in runtime cache and keeps map index clamped.
	 */
	public static void requestPlaceholderJumpListSync(ParkourMap map, IPlaceholderJumpSyncCallback cb) {
		ensureStoresInitialized();

		if (map == null) {
			if (cb != null) cb.onError("No map selected.");
			return;
		}

		if (!map.isPlaceholderSyncEnabled()) {
			if (cb != null) cb.onError("Placeholder sync is not enabled.");
			return;
		}

		final String url = map.getPlaceholderSheetUrl();
		if (url == null || url.trim().isEmpty()) {
			if (cb != null) cb.onError("No placeholder sheet URL configured.");
			return;
		}

		if (SheetSyncManager.isSyncInProgress()) {
			if (cb != null) cb.onError("Sync already in progress.");
			return;
		}

		// forcedGid = -1 (SheetUrlUtils can derive gid if present; otherwise export default)
		SheetSyncManager.fetchCsvAsync(url, -1, PLACEHOLDER_SHEETS_CACHE_FILE, new SheetSyncManager.ISyncCallback() {
			@Override
			public void onSuccess(String csvText, String cachePath) {
				Minecraft.getMinecraft().addScheduledTask(new Runnable() {
					@Override
					public void run() {
						try {
							SheetJumpNameExtractor.Result res = SheetJumpNameExtractor.extract(csvText);
							if (res == null || res.jumpNames == null || res.jumpNames.isEmpty()) {
								if (cb != null) cb.onError("No jump names found in CSV.");
								return;
							}

							placeholderJumpNames = new java.util.ArrayList<String>(res.jumpNames);

							// Store sheet-driven level-1 sections for placeholder pager
							if (res.sectionsL1 != null) {
								placeholderSectionsL1 = new java.util.ArrayList<SheetJumpNameExtractor.PlaceholderSection>(res.sectionsL1);
							} else {
								placeholderSectionsL1 = new java.util.ArrayList<SheetJumpNameExtractor.PlaceholderSection>();
							}

							// Clamp saved index
							int idx = map.getPlaceholderJumpIndex();
							if (idx < 0) idx = 0;
							if (idx >= placeholderJumpNames.size()) idx = placeholderJumpNames.size() - 1;
							map.setPlaceholderJumpIndex(idx);
							saveToFile();

							if (cb != null) cb.onSuccess(placeholderJumpNames.size());
						} catch (Exception ex) {
							if (cb != null) cb.onError("Failed to parse CSV.");
						}
					}
				});
			}

			@Override
			public void onError(String errorMessage) {
				Minecraft.getMinecraft().addScheduledTask(new Runnable() {
					@Override
					public void run() {
						if (cb != null) cb.onError(errorMessage != null ? errorMessage : "Failed to download CSV.");
					}
				});
			}
		});
	}

	public static void exportMapTemplateToFile(ServerProfile server, ParkourMap map, File file) throws IOException {
		ensureStoresInitialized();
		if (server == null || map == null || file == null) return;

		Gson gson = new GsonBuilder().setPrettyPrinting().create();

		// Wrap to allow future versioning
		MapTemplatePayload payload = new MapTemplatePayload();
		payload.version = 1;
		payload.serverId = server.getId();
		payload.map = map;

		File parent = file.getParentFile();
		if (parent != null && !parent.exists()) parent.mkdirs();

		FileWriter fw = new FileWriter(file);
		try {
			fw.write(gson.toJson(payload));
		} finally {
			fw.close();
		}
	}

	public static ParkourMap importMapTemplateFromFile(ServerProfile targetServer, File file) throws IOException {
		ensureStoresInitialized();
		if (targetServer == null || file == null) return null;

		Gson gson = new Gson();
		MapTemplatePayload payload;

		FileReader fr = new FileReader(file);
		try {
			payload = gson.fromJson(fr, MapTemplatePayload.class);
		} finally {
			fr.close();
		}

		if (payload == null || payload.map == null) return null;

		ParkourMap imported = payload.map;

		// Safety: ensure structure is not null
		if (imported.getJumps() == null) imported.setJumps(new ArrayList<Jump>());

		// Optional: normalize reminders lists, active indices, etc.
		// (If you implemented ensureEightLines, you can normalize reminder lines here too.)

		// Ensure unique map ID inside the target server
		String baseId = imported.getId() != null ? imported.getId().trim() : "ImportedMap";
		if (baseId.isEmpty()) baseId = "ImportedMap";
		String uniqueId = makeUniqueMapId(targetServer, baseId);
		imported.setId(uniqueId);

		if (targetServer.getMaps() == null) {
			targetServer.setMaps(new ArrayList<ParkourMap>());
		}
		targetServer.getMaps().add(imported);

		saveToFile();
		return imported;
	}

	private static String makeUniqueMapId(ServerProfile server, String baseId) {
		String id = baseId;
		int n = 2;

		while (findMapById(server, id) != null) {
			id = baseId + "_" + n;
			n++;
			if (n > 9999) {
				id = baseId + "_" + System.currentTimeMillis();
				break;
			}
		}
		return id;
	}

	private static class MapTemplatePayload {
		int version;
		String serverId;
		ParkourMap map;
	}

	// Build occurrence key from context columns if they exist
	private static String buildOccurrenceKey(
			String jumpName,
			String section,
			String subSection,
			String area,
			String cp,
			String sp
	) {
		StringBuilder sb = new StringBuilder();
		sb.append(CsvUtils.normalizeKey(jumpName));

		if (section != null && !section.trim().isEmpty()) {
			sb.append("|S=").append(CsvUtils.normalizeKey(section));
		}
		if (subSection != null && !subSection.trim().isEmpty()) {
			sb.append("|SS=").append(CsvUtils.normalizeKey(subSection));
		}
		if (area != null && !area.trim().isEmpty()) {
			sb.append("|A=").append(CsvUtils.normalizeKey(area));
		}
		if (cp != null && !cp.trim().isEmpty()) {
			sb.append("|CP=").append(CsvUtils.normalizeKey(cp));
		}
		if (sp != null && !sp.trim().isEmpty()) {
			sb.append("|SP=").append(CsvUtils.normalizeKey(sp));
		}

		return sb.toString();
	}

	// Header lookup with normalization (supports "Sub-section" etc.)
	private static int findHeaderCol(List<String> header, String... names) {
		if (header == null || names == null) return -1;

		for (int i = 0; i < header.size(); i++) {
			String h = header.get(i);
			String hn = CsvUtils.normalizeKey(h);

			for (String n : names) {
				if (n == null) continue;
				if (hn.equals(CsvUtils.normalizeKey(n))) {
					return i;
				}
			}
		}
		return -1;
	}

	private static String getByCol(List<String> row, int col) {
		if (col < 0) return "";
		return CsvUtils.getCell(row, col).trim();
	}

	private static ArrayList<String> ensureEightLines(ArrayList<String> lines) {
		ArrayList<String> out = new ArrayList<String>(8);
		for (int i = 0; i < 8; i++) {
			String v = (lines != null && i < lines.size() && lines.get(i) != null) ? lines.get(i) : "";
			out.add(v);
		}
		return out;
	}

	private static Jump findFirstJumpByIdWithCoords(ParkourMap map, String id) {
		return findBestJumpByNameWithCoords(map, id);
	}

	private static Jump findBestJumpByNameWithCoords(ParkourMap map, String nameFromSheet) {
		if (map == null || nameFromSheet == null) return null;
		if (map.getJumps() == null) return null;

		String needleRaw = nameFromSheet.trim();
		if (needleRaw.isEmpty()) return null;

		// 1) Exact match first (fast + deterministic)
		for (Jump j : map.getJumps()) {
			if (j == null) continue;

			// Require coords (per your rule)
			if (j.getX() == 0 && j.getY() == 0 && j.getZ() == 0) continue;

			if (needleRaw.equals(j.getId())) {
				return j;
			}
		}

		// 2) Fuzzy match (typos/spaces)
		Jump best = null;
		double bestScore = 0.0;

		for (Jump j : map.getJumps()) {
			if (j == null) continue;

			if (j.getX() == 0 && j.getY() == 0 && j.getZ() == 0) continue;

			String candidate = j.getId();
			if (candidate == null || candidate.trim().isEmpty()) continue;

			double score = CsvUtils.similarityScore(needleRaw, candidate);
			if (score > bestScore) {
				bestScore = score;
				best = j;
			}
		}

		if (best == null) return null;

		// Safety threshold: avoid accidental merges
		if (!isAcceptableJumpMatch(needleRaw, best.getId(), bestScore)) {
			return null;
		}

		return best;
	}

	private static boolean isAcceptableJumpMatch(String a, String b, double score) {
		// Delegate to CsvUtils thresholds (length-aware)
		if (CsvUtils.areJumpNamesSimilar(a, b)) {
			return true;
		}

		// Extra hard floor (just in case)
		return score >= 0.90;
	}

	private static void removePlaceholderReminders(Jump j) {
		if (j == null || j.getReminders() == null) return;

		ArrayList<Reminder> rs = j.getReminders();
		for (int i = rs.size() - 1; i >= 0; i--) {
			Reminder r = rs.get(i);
			if (r == null || r.lines == null || r.lines.isEmpty()) continue;

			if (isPlaceholderReminder(r)) {
				rs.remove(i);
			}
		}

		if (rs.isEmpty()) {
			j.setActiveReminderIndex(-1);
		} else if (j.getActiveReminderIndex() < 0 || j.getActiveReminderIndex() >= rs.size()) {
			j.setActiveReminderIndex(0);
		}
	}

	private static boolean isPlaceholderReminder(Reminder r) {
		if (r == null || r.lines == null || r.lines.isEmpty()) {
			return false;
		}

		// Legacy placeholder format: first cell (index 0)
		String v0 = getLineTrim(r.lines, 0);
		if ("PLACEHOLDER".equals(v0)) {
			return true;
		}

		// New forced placeholder format: Setup (index 2)
		String v2 = getLineTrim(r.lines, 2);
		if ("PLACEHOLDER".equals(v2)) {
			return true;
		}

		// Extra safety: some bad historical cases ended up in Strategy (index 3)
		String v3 = getLineTrim(r.lines, 3);
		if ("PLACEHOLDER".equals(v3)) {
			return true;
		}

		// Extra safety: some bad historical cases ended up in Strafe (index 4)
		String v4 = getLineTrim(r.lines, 4);
        return "PLACEHOLDER".equals(v4);
    }

	private static String getLineTrim(ArrayList<String> lines, int idx) {
		if (lines == null || idx < 0 || idx >= lines.size()) return "";
		String s = lines.get(idx);
		return s != null ? s.trim() : "";
	}

	private static boolean alreadyHasReminderWithExactLines(Jump j, ArrayList<String> lines) {
		if (j == null || j.getReminders() == null || lines == null) return false;

		for (Reminder r : j.getReminders()) {
			if (r == null || r.lines == null) continue;
			if (sameLines(r.lines, lines)) {
				return true;
			}
		}
		return false;
	}

	private static boolean sameLines(ArrayList<String> a, ArrayList<String> b) {
		if (a == null || b == null) return false;
		if (a.size() != b.size()) return false;

		for (int i = 0; i < a.size(); i++) {
			String sa = a.get(i) != null ? a.get(i) : "";
			String sb = b.get(i) != null ? b.get(i) : "";
			if (!sa.equals(sb)) return false;
		}
		return true;
	}

	private static boolean sameCore6(ArrayList<String> a, ArrayList<String> b) {
		if (a == null || b == null) return false;

		// Core identity: indices 0..5 (Position, Facing, Setup, Strategy, Strafe, Turn)
		for (int i = 0; i <= 5; i++) {
			String sa = (i < a.size() && a.get(i) != null) ? a.get(i).trim() : "";
			String sb = (i < b.size() && b.get(i) != null) ? b.get(i).trim() : "";
			if (!sa.equals(sb)) return false;
		}
		return true;
	}

	private static Reminder findReminderByCore6(Jump j, ArrayList<String> lines) {
		if (j == null || j.getReminders() == null || lines == null) return null;

		for (Reminder r : j.getReminders()) {
			if (r == null || r.lines == null) continue;
			if (sameCore6(r.lines, lines)) {
				return r;
			}
		}
		return null;
	}

	private static void updateAuthorAndTipsIfChanged(Reminder existing, ArrayList<String> incoming) {
		if (existing == null || existing.lines == null || incoming == null) return;

		// Ensure existing has at least 8 slots to update safely
		while (existing.lines.size() < 8) {
			existing.lines.add("");
		}

		String newAuthor = (incoming.size() > 6 && incoming.get(6) != null) ? incoming.get(6) : "";
		String newTips = (incoming.size() > 7 && incoming.get(7) != null) ? incoming.get(7) : "";

		// Always update (including empty), per your rule: if only these change, we treat it as same strategy and overwrite.
		existing.lines.set(6, newAuthor);
		existing.lines.set(7, newTips);
	}

	public static void requestSheetSyncForMap(ServerProfile server, ParkourMap map, boolean manual) {
		ensureStoresInitialized();
		if (map == null || !map.hasSheetConfigured()) {
			sendChat(Minecraft.getMinecraft(),
					EnumChatFormatting.DARK_AQUA + "[ParkourStrats] " + EnumChatFormatting.RED + "No sheet URL configured for this map.");
			return;
		}

		if (SheetSyncManager.isSyncInProgress()) {
			if (manual) {
				sendChat(Minecraft.getMinecraft(),
						EnumChatFormatting.DARK_AQUA + "[ParkourStrats] " + EnumChatFormatting.YELLOW + "Sync already in progress.");
			}
			return;
		}

		final String cachePath = buildCachePath(server, map);

		final String announceKey = buildSheetAnnounceKey(server, map);
		final boolean announceInChat = manual || !sheetSyncAnnouncedKeys.contains(announceKey);

		if (announceInChat) {
			sendChat(Minecraft.getMinecraft(),
					EnumChatFormatting.DARK_AQUA + "[ParkourStrats] " + EnumChatFormatting.AQUA + "Sync started...");
		}

		SheetSyncManager.fetchCsvAsync(map.getSheetUrl(), map.getSheetGid(), cachePath, new SheetSyncManager.ISyncCallback() {
			@Override
			public void onSuccess(String csvText, String cachePath) {
				// Apply changes on main thread
				Minecraft.getMinecraft().addScheduledTask(new Runnable() {
					@Override
					public void run() {
						applyCsvToMap(server, map, csvText, announceInChat);
					}
				});
			}

			@Override
			public void onError(String errorMessage) {
				Minecraft.getMinecraft().addScheduledTask(new Runnable() {
					@Override
					public void run() {
						// If user stopped sync while request was in-flight, do not spam chat.
						if (map == null || !map.hasSheetConfigured()) {
							return;
						}

						sendChat(Minecraft.getMinecraft(),
								EnumChatFormatting.DARK_AQUA + "[ParkourStrats] " + EnumChatFormatting.RED + errorMessage);
					}
				});
			}
		});
	}

	private static String buildCachePath(ServerProfile server, ParkourMap map) {
		String s = server != null ? safeFile(server.getId()) : "unknown_server";
		String m = map != null ? safeFile(map.getId()) : "unknown_map";
		return SHEETS_CACHE_DIR + "/" + s + "__" + m + ".csv";
	}

	private static String buildSheetAnnounceKey(ServerProfile server, ParkourMap map) {
		String s = server != null ? safeFile(server.getId()) : "unknown_server";
		String m = map != null ? safeFile(map.getId()) : "unknown_map";
		return s + "__" + m;
	}

	private static String safeFile(String s) {
		if (s == null) return "null";
		return s.replaceAll("[^a-zA-Z0-9._-]+", "_");
	}

	// -------------------------
	// Rendering
	// -------------------------

	@SubscribeEvent
	public void onWorldRender(RenderWorldLastEvent event) {
		ensureStoresInitialized();

		if (!(StratReminders.showKey.isKeyDown() || toggled)) {
			return;
		}

		Minecraft mc = Minecraft.getMinecraft();
		if (mc == null || mc.thePlayer == null) {
			return;
		}

		EntityPlayerSP player = mc.thePlayer;

		// Render all loaded jumps (Global + Restored + Shared) that are nearby.
		renderNearbyJumps(player, event);
	}

	private static void renderNearbyJumps(EntityPlayerSP player, RenderWorldLastEvent event) {
		if (player == null) {
			return;
		}

		// 1) Global
		renderNearbyJumpsFromStore(globalStore, player, event);

		// 2) RestoredStrats
		renderNearbyJumpsFromStore(restoredStore, player, event);

		// 3) Shared
		renderNearbyJumpsFromStore(sharedStore, player, event);
	}

	public static Jump selectJumpInCrosshair(double maxRangeBlocks, double maxPerpDistBlocks) {
		ensureStoresInitialized();

		Minecraft mc = Minecraft.getMinecraft();
		if (mc == null || mc.thePlayer == null) {
			return null;
		}

		EntityPlayerSP player = mc.thePlayer;

		JumpPickResult best = null;

		JumpPickResult g = findBestJumpInCrosshairInStore(globalStore, player, maxRangeBlocks, maxPerpDistBlocks);
		if (g != null && g.jump != null) {
			best = g;
		}

		JumpPickResult r = findBestJumpInCrosshairInStore(restoredStore, player, maxRangeBlocks, maxPerpDistBlocks);
		if (r != null && r.jump != null) {
			if (best == null || isCrosshairPickBetter(r, best)) {
				best = r;
			}
		}

		JumpPickResult s = findBestJumpInCrosshairInStore(sharedStore, player, maxRangeBlocks, maxPerpDistBlocks);
		if (s != null && s.jump != null) {
			if (best == null || isCrosshairPickBetter(s, best)) {
				best = s;
			}
		}

		if (best == null || best.jump == null) {
			return null;
		}

		selectedServer = best.server;
		selectedMap = best.map;
		selectedJump = best.jump;

		if (selectedJump.getReminders() == null) {
			selectedJump.setReminders(new ArrayList<Reminder>());
		}

		return selectedJump;
	}

	private static boolean isCrosshairPickBetter(JumpPickResult a, JumpPickResult b) {
		// Prefer smaller perpendicular distance to view ray,
		// then prefer closer along-ray distance (t).
		if (a == null || b == null) return false;

		if (a.perpDistSq < b.perpDistSq) return true;
		if (a.perpDistSq > b.perpDistSq) return false;

		return a.rayT < b.rayT;
	}

	private static boolean hasAnyStrategies() {
		ensureStoresInitialized();
		return storeHasAnyStrategies(globalStore) || storeHasAnyStrategies(restoredStore) || storeHasAnyStrategies(sharedStore);
	}

	private static boolean storeHasAnyStrategies(DataStore store) {
		if (store == null || store.getServers() == null) return false;

		for (ServerProfile s : store.getServers()) {
			if (s == null || s.getMaps() == null) continue;

			for (ParkourMap m : s.getMaps()) {
				if (m == null || m.getJumps() == null) continue;

				for (Jump j : m.getJumps()) {
					if (j == null) continue;

					ArrayList<Reminder> rs = j.getReminders();
					if (rs == null || rs.isEmpty()) continue;

					for (Reminder r : rs) {
						if (r == null || r.lines == null || r.lines.isEmpty()) continue;

						// At least one non-empty line counts as "strategy exists"
						for (String line : r.lines) {
							if (line != null && line.trim().length() > 0) {
								return true;
							}
						}
					}
				}
			}
		}

		return false;
	}

	private static void renderNearbyJumpsFromStore(DataStore store, EntityPlayerSP player, RenderWorldLastEvent event) {
		if (store == null || store.getServers() == null) {
			return;
		}

		for (ServerProfile s : store.getServers()) {
			if (s == null || s.getMaps() == null) continue;

			for (ParkourMap m : s.getMaps()) {
				if (m == null || m.getJumps() == null) continue;

				for (Jump j : m.getJumps()) {
					if (j == null) continue;

					int x = j.getX();
					int y = j.getY();
					int z = j.getZ();

					// Skip unset coords
					if (x == 0 && y == 0 && z == 0) {
						continue;
					}

					// Nearby filter
					double dx = player.posX - x;
					double dy = player.posY - y;
					double dz = player.posZ - z;
					double distSq = (dx * dx) + (dy * dy) + (dz * dz);

					if (distSq > NEARBY_RADIUS_SQ) {
						continue;
					}

					Reminder active = getActiveReminderOrFallback(j);
					if (active == null || active.lines == null || active.lines.isEmpty()) {
						continue;
					}

					String jumpName = (j.getId() != null) ? j.getId().trim() : "";
					boolean isRestoredStratsContext =
							(s != null && "RestoredStrats".equals(s.getId())) ||
									(m != null && "RestoredStrats".equals(m.getId()));

					boolean showName = !isRestoredStratsContext && isGlobalShowJumpNameEnabled();

					String header = null;
					if (showName && jumpName.length() > 0) {
						String jumpColor = getGlobalInWorldJumpNameColor();
						header = jumpColor + jumpName + EnumChatFormatting.RESET;
					}

					String textColor = getGlobalInWorldTextColor();

					DrawUtils.drawTextAtCoords(active.lines, header, showName, textColor, x, y, z, event.partialTicks);
				}
			}
		}
	}

	public static boolean isGlobalShowJumpNameEnabled() {
		ensureSettingsLoaded();
		return settings != null && settings.showJumpNameInWorld;
	}

	public static void setGlobalShowJumpNameEnabled(boolean enabled) {
		ensureSettingsLoaded();
		if (settings == null) settings = new ModSettings();

		settings.showJumpNameInWorld = enabled;
		saveSettingsToFile();
	}

	public static String getGlobalInWorldJumpNameColor() {
		ensureSettingsLoaded();
		if (settings == null) settings = new ModSettings();

		String c = settings.inWorldJumpNameColor;
		if (!isValidColorCode(c)) {
			c = "\u00A7b"; // default aqua
			settings.inWorldJumpNameColor = c;
			saveSettingsToFile();
		}
		return c;
	}

	public static void setGlobalInWorldJumpNameColor(String code) {
		ensureSettingsLoaded();
		if (settings == null) settings = new ModSettings();

		if (!isValidColorCode(code)) {
			return;
		}

		settings.inWorldJumpNameColor = code;
		saveSettingsToFile();
	}

	public static String getGlobalInWorldTextColor() {
		ensureSettingsLoaded();
		if (settings == null) settings = new ModSettings();

		String c = settings.inWorldTextColor;
		if (!isValidColorCode(c)) {
			c = "\u00A7f"; // default white
			settings.inWorldTextColor = c;
			saveSettingsToFile();
		}
		return c;
	}

	public static void setGlobalInWorldTextColor(String code) {
		ensureSettingsLoaded();
		if (settings == null) settings = new ModSettings();

		if (!isValidColorCode(code)) {
			return;
		}

		settings.inWorldTextColor = code;
		saveSettingsToFile();
	}

	private static boolean isValidColorCode(String s) {
		if (s == null) return false;
		if (s.length() != 2) return false;
		if (s.charAt(0) != '\u00A7') return false;

		char c = Character.toLowerCase(s.charAt(1));
		return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
	}

	private static Reminder getActiveReminderOrFallback(Jump jump) {
		if (jump == null) return null;

		ArrayList<Reminder> list = jump.getReminders();
		if (list == null || list.isEmpty()) {
			return null;
		}

		int idx = jump.getActiveReminderIndex();
		if (idx >= 0 && idx < list.size()) {
			return list.get(idx);
		}

		jump.setActiveReminderIndex(0);
		saveToFile();

		return list.get(0);
	}

	// -------------------------
	// Key handling
	// -------------------------

	@SubscribeEvent
	public void onKeyPress(InputEvent.KeyInputEvent event) {
		Minecraft minecraft = Minecraft.getMinecraft();
		EntityPlayerSP player = minecraft.thePlayer;
		WorldClient world = minecraft.theWorld;

		if (player == null || world == null) {
			return;
		}

		if (StratReminders.hierarchyKey.isKeyDown()) {
			Minecraft.getMinecraft().displayGuiScreen(new GuiServerList(null));
			return;
		}

		if (StratReminders.createKey.isKeyDown()) {
			player.openGui(StratReminders.instance, 1, world,
					(int) player.posX, (int) (player.posY + 0.4), (int) player.posZ);
		} else if (StratReminders.editKey.isKeyDown()) {
			// Safety: if the mod has no strategies at all, do not open the edit GUI.
			if (!hasAnyStrategies()) {
				sendChat(Minecraft.getMinecraft(),
						EnumChatFormatting.DARK_AQUA + "[ParkourStrats] " + EnumChatFormatting.YELLOW
								+ "No available strategies were found.");
				return;
			}

			player.openGui(StratReminders.instance, 2, world,
					(int) player.posX, (int) player.posY, (int) player.posZ);
		}
		else if (StratReminders.toggleKey.isKeyDown()) {
			toggled = !toggled;
		}
	}

	// -------------------------
	// Jump identity: allow same name, require exact coords match to reuse
	// -------------------------

	/**
	 * Finds a jump only if BOTH name and coords match.
	 * This is the new identity check for "reuse existing container".
	 */
	public static Jump findJumpByNameAndCoords(ParkourMap map, String id, int x, int y, int z) {
		ensureStoresInitialized();
		if (map == null || id == null) return null;
		if (map.getJumps() == null) map.setJumps(new ArrayList<Jump>());

		for (Jump j : map.getJumps()) {
			if (j == null) continue;

			if (!id.equals(j.getId())) continue;

			if (j.getX() == x && j.getY() == y && j.getZ() == z) {
				return j;
			}
		}

		return null;
	}

	/**
	 * Returns an existing jump if (name + coords) are identical, otherwise creates a new jump
	 * even if the name is the same (new container).
	 *
	 * This is the method your GUI should use instead of "find by name".
	 */
	public static Jump getOrCreateJumpByNameAndCoords(ParkourMap map, String id, int x, int y, int z) {
		ensureStoresInitialized();
		if (map == null) return null;
		if (!isValidId(id)) return null;

		Jump existing = findJumpByNameAndCoords(map, id, x, y, z);
		if (existing != null) {
			return existing;
		}

		Jump j = new Jump();
		j.setShowJumpNameInWorld(true);
		j.setId(id);
		j.setX(x);
		j.setY(y);
		j.setZ(z);
		j.setReminders(new ArrayList<Reminder>());
		j.setActiveReminderIndex(-1);

		if (map.getJumps() == null) {
			map.setJumps(new ArrayList<Jump>());
		}
		map.getJumps().add(j);

		saveToFile();
		return j;
	}

	/**
	 * Always creates a NEW jump entry, even if another jump with the same name and coords already exists.
	 * This is used to allow manual duplicates in the GUI (same Jump name allowed, same coords allowed).
	 */
	public static Jump createJumpByNameAndCoordsAlwaysNew(ParkourMap map, String id, int x, int y, int z) {
		ensureStoresInitialized();
		if (map == null) return null;
		if (!isValidId(id)) return null;

		Jump j = new Jump();
		j.setShowJumpNameInWorld(true);
		j.setId(id);
		j.setX(x);
		j.setY(y);
		j.setZ(z);
		j.setReminders(new ArrayList<Reminder>());
		j.setActiveReminderIndex(-1);

		if (map.getJumps() == null) {
			map.setJumps(new ArrayList<Jump>());
		}
		map.getJumps().add(j);

		saveToFile();
		return j;
	}

	// -------------------------
	// Strategies
	// -------------------------

	public static void createReminder(ArrayList<String> lines, int x, int y, int z) {
		ensureStoresInitialized();

		Minecraft mc = Minecraft.getMinecraft();

		if (selectedJump == null) {
			sendChat(mc, EnumChatFormatting.RED + "No jump selected. Create/select a jump first.");
			return;
		}

		if (lines == null) {
			System.out.println("[ParkourStrats] ERROR: lines is null, aborting createReminder.");
			return;
		}

		// Keep the original negative coordinate correction behavior.
		// IMPORTANT: This method no longer adds +1 to Y internally.
		// All callers must already provide the correct Y (player block Y + 1).
		if (x < 0) x--;
		if (y < 0) y--;
		if (z < 0) z--;

		// Keep existing jump coords if already set (jump-level coords are the category key)
		boolean jumpHasCoords = !(selectedJump.getX() == 0 && selectedJump.getY() == 0 && selectedJump.getZ() == 0);
		if (!jumpHasCoords) {
			selectedJump.setX(x);
			selectedJump.setY(y);
			selectedJump.setZ(z);
		}

		// Only apply Position/Facing when using the new 8-line format.
		if (lines.size() >= 8) {
			selectedJump.setPosition(getLineSafe(lines, 0));
			selectedJump.setFacing(getLineSafe(lines, 1));
		}

		selectedJump.setSetup(getLineSafe(lines, 2));
		selectedJump.setStrategy(getLineSafe(lines, 3));
		selectedJump.setStrafe(getLineSafe(lines, 4));
		selectedJump.setTurn(getLineSafe(lines, 5));
		selectedJump.setAuthor(getLineSafe(lines, 6));
		selectedJump.setTips(getLineSafe(lines, 7));

		if (selectedJump.getReminders() == null) {
			selectedJump.setReminders(new ArrayList<Reminder>());
		}

		Reminder reminder = new Reminder(lines);
		selectedJump.getReminders().add(reminder);

		if (selectedJump.getActiveReminderIndex() < 0) {
			selectedJump.setActiveReminderIndex(0);
		}

		try {
			saveToFile();
		} catch (Exception e) {
			System.out.println("[ParkourStrats] ERROR during saveToFile(): " + e.getClass().getName() + " " + e.getMessage());
			e.printStackTrace();
		}

		String coordInfo;
		if (jumpHasCoords) {
			coordInfo = EnumChatFormatting.GRAY + "[" + selectedJump.getX() + ", " + selectedJump.getY() + ", " + selectedJump.getZ() + "]";
		} else {
			coordInfo = EnumChatFormatting.GRAY + "[" + x + ", " + y + ", " + z + "]";
		}

		String str = EnumChatFormatting.DARK_AQUA + "[ParkourStrats] "
				+ EnumChatFormatting.AQUA + "Strategy saved at jump coordinates: "
				+ coordInfo;

		sendChat(mc, str);
	}

	public static void deleteReminder(Reminder rem) {
		ensureStoresInitialized();

		if (selectedJump == null || selectedJump.getReminders() == null) {
			return;
		}

		int oldIndex = selectedJump.getReminders().indexOf(rem);

		if (rem != null) {
			selectedJump.getReminders().remove(rem);
		}

		ArrayList<Reminder> list = selectedJump.getReminders();
		if (list == null || list.isEmpty()) {
			selectedJump.setActiveReminderIndex(-1);
		} else {
			int active = selectedJump.getActiveReminderIndex();
			if (active == oldIndex) {
				selectedJump.setActiveReminderIndex(0);
			} else if (active > oldIndex) {
				selectedJump.setActiveReminderIndex(active - 1);
			} else if (active >= list.size()) {
				selectedJump.setActiveReminderIndex(0);
			}
		}

		saveToFile();

		String str = EnumChatFormatting.DARK_AQUA + "[ParkourStrats] "
				+ EnumChatFormatting.AQUA + "Strategy deleted";

		Minecraft mc = Minecraft.getMinecraft();
		sendChat(mc, str);
	}

	public static ArrayList<Reminder> getReminderList() {
		ensureStoresInitialized();
		if (selectedJump == null || selectedJump.getReminders() == null) {
			return new ArrayList<Reminder>();
		}
		return selectedJump.getReminders();
	}

	public static void setActiveReminder(Reminder rem) {
		ensureStoresInitialized();
		if (selectedJump == null || selectedJump.getReminders() == null || rem == null) {
			return;
		}

		int idx = selectedJump.getReminders().indexOf(rem);
		if (idx < 0) {
			return;
		}

		selectedJump.setActiveReminderIndex(idx);
		saveToFile();
	}

	// -------------------------
	// Persistence
	// -------------------------

	public static void saveToFile() {
		ensureStoresInitialized();

		saveStoreToPath(globalStore, GLOBAL_FILE_PATH);
		saveStoreToPath(restoredStore, RESTORED_FILE_PATH);
		saveStoreToPath(sharedStore, SHARED_FILE_PATH);
	}

	private static void saveStoreToPath(DataStore store, String path) {
		if (store == null || path == null) {
			return;
		}

		GsonBuilder builder = new GsonBuilder();
		builder.setPrettyPrinting();
		Gson gson = builder.create();

		String jsonString = gson.toJson(store);

		try {
			File file = new File(path);
			File parent = file.getParentFile();
			if (parent != null && !parent.exists()) {
				parent.mkdirs();
			}
			file.createNewFile();

			FileWriter fw = new FileWriter(path);
			fw.write(jsonString);
			fw.close();

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void loadFromFile() {
		ensureGlobalLoadedAndNormalized();
		ensureRestoredLoadedAndNormalized();
		ensureSharedLoadedAndNormalized();
		autoSelectDefaultContext();
	}

	private static void ensureGlobalLoadedAndNormalized() {
		if (isGlobalLoaded && globalStore != null) {
			return;
		}

		if (globalStore == null) {
			globalStore = new DataStore();
			globalStore.setServers(new ArrayList<ServerProfile>());
		}
		if (globalStore.getServers() == null) {
			globalStore.setServers(new ArrayList<ServerProfile>());
		}

		Gson gson = new Gson();
		Type dataStoreType = new TypeToken<DataStore>() {}.getType();

		boolean needsSave = false;

		try {
			File file = new File(GLOBAL_FILE_PATH);
			File parent = file.getParentFile();
			if (parent != null && !parent.exists()) {
				parent.mkdirs();
			}

			boolean created = file.createNewFile();
			if (created) {
				needsSave = true;
			} else {
				DataStore loadedStore = null;
				try {
					loadedStore = gson.fromJson(new FileReader(GLOBAL_FILE_PATH), dataStoreType);
				} catch (JsonSyntaxException ignored) {
					loadedStore = null;
				}

				if (loadedStore != null && loadedStore.getServers() != null) {
					globalStore = loadedStore;
				} else {
					globalStore = new DataStore();
					globalStore.setServers(new ArrayList<ServerProfile>());
					needsSave = true;
				}
			}

		} catch (IOException ex) {
			ex.printStackTrace();
			needsSave = true;
		}

		if (normalizeStore(globalStore)) {
			needsSave = true;
		}

		if (ensureGlobalStructure()) {
			needsSave = true;
		}

		if (needsSave) {
			saveStoreToPath(globalStore, GLOBAL_FILE_PATH);
		}

		isGlobalLoaded = true;
	}

	private static void ensureRestoredLoadedAndNormalized() {
		if (isRestoredLoaded && restoredStore != null) {
			return;
		}

		if (restoredStore == null) {
			restoredStore = new DataStore();
			restoredStore.setServers(new ArrayList<ServerProfile>());
		}
		if (restoredStore.getServers() == null) {
			restoredStore.setServers(new ArrayList<ServerProfile>());
		}

		Gson gson = new Gson();
		Type dataStoreType = new TypeToken<DataStore>() {}.getType();

		boolean needsSave = false;

		try {
			File file = new File(RESTORED_FILE_PATH);
			File parent = file.getParentFile();
			if (parent != null && !parent.exists()) {
				parent.mkdirs();
			}

			boolean created = file.createNewFile();
			if (created) {
				needsSave = true;
			} else {
				DataStore loadedStore = null;
				try {
					loadedStore = gson.fromJson(new FileReader(RESTORED_FILE_PATH), dataStoreType);
				} catch (JsonSyntaxException ignored) {
					loadedStore = null;
				}

				if (loadedStore != null && loadedStore.getServers() != null) {
					restoredStore = loadedStore;
				} else {
					restoredStore = new DataStore();
					restoredStore.setServers(new ArrayList<ServerProfile>());
					needsSave = true;
				}
			}

		} catch (IOException ex) {
			ex.printStackTrace();
			needsSave = true;
		}

		if (normalizeStore(restoredStore)) {
			needsSave = true;
		}

		if (ensureRestoredStructure()) {
			needsSave = true;
		}

		if (needsSave) {
			saveStoreToPath(restoredStore, RESTORED_FILE_PATH);
		}

		isRestoredLoaded = true;
	}

	private static void ensureSharedLoadedAndNormalized() {
		if (isSharedLoaded && sharedStore != null) {
			return;
		}

		if (sharedStore == null) {
			sharedStore = new DataStore();
			sharedStore.setServers(new ArrayList<ServerProfile>());
		}
		if (sharedStore.getServers() == null) {
			sharedStore.setServers(new ArrayList<ServerProfile>());
		}

		Gson gson = new Gson();
		Type dataStoreType = new TypeToken<DataStore>() {}.getType();

		boolean needsSave = false;

		try {
			File file = new File(SHARED_FILE_PATH);
			File parent = file.getParentFile();
			if (parent != null && !parent.exists()) {
				parent.mkdirs();
			}

			boolean created = file.createNewFile();
			if (created) {
				needsSave = true;
			} else {
				DataStore loadedStore = null;
				try {
					loadedStore = gson.fromJson(new FileReader(SHARED_FILE_PATH), dataStoreType);
				} catch (JsonSyntaxException ignored) {
					loadedStore = null;
				}

				if (loadedStore != null && loadedStore.getServers() != null) {
					sharedStore = loadedStore;
				} else {
					sharedStore = new DataStore();
					sharedStore.setServers(new ArrayList<ServerProfile>());
					needsSave = true;
				}
			}

		} catch (IOException ex) {
			ex.printStackTrace();
			needsSave = true;
		}

		if (normalizeStore(sharedStore)) {
			needsSave = true;
		}

		if (needsSave) {
			saveStoreToPath(sharedStore, SHARED_FILE_PATH);
		}

		isSharedLoaded = true;
	}

	private static void ensureStoresInitialized() {
		ensureGlobalLoadedAndNormalized();
		ensureRestoredLoadedAndNormalized();
		ensureSharedLoadedAndNormalized();
		ensureSettingsLoaded();
	}

	private static boolean ensureGlobalStructure() {
		boolean changed = false;

		if (globalStore == null) {
			globalStore = new DataStore();
			globalStore.setServers(new ArrayList<ServerProfile>());
			changed = true;
		}
		if (globalStore.getServers() == null) {
			globalStore.setServers(new ArrayList<ServerProfile>());
			changed = true;
		}

		ServerProfile globalServer = findServerByIdInStore(globalStore, GLOBAL_SERVER_ID);
		if (globalServer == null) {
			globalServer = new ServerProfile();
			globalServer.setId(GLOBAL_SERVER_ID);
			globalServer.setMaps(new ArrayList<ParkourMap>());
			globalStore.getServers().add(globalServer);
			changed = true;
		}

		if (globalServer.getMaps() == null) {
			globalServer.setMaps(new ArrayList<ParkourMap>());
			changed = true;
		}

		ParkourMap globalMap = findMapByIdInServer(globalServer, GLOBAL_MAP_ID);
		if (globalMap == null) {
			globalMap = new ParkourMap();
			globalMap.setId(GLOBAL_MAP_ID);
			globalMap.setJumps(new ArrayList<Jump>());
			globalServer.getMaps().add(globalMap);
			changed = true;
		}

		if (globalMap.getJumps() == null) {
			globalMap.setJumps(new ArrayList<Jump>());
			changed = true;
		}

		return changed;
	}

	private static boolean ensureRestoredStructure() {
		boolean changed = false;

		if (restoredStore == null) {
			restoredStore = new DataStore();
			restoredStore.setServers(new ArrayList<ServerProfile>());
			changed = true;
		}
		if (restoredStore.getServers() == null) {
			restoredStore.setServers(new ArrayList<ServerProfile>());
			changed = true;
		}

		ServerProfile restoredServer = findServerByIdInStore(restoredStore, RESTORED_SERVER_ID);
		if (restoredServer == null) {
			restoredServer = new ServerProfile();
			restoredServer.setId(RESTORED_SERVER_ID);
			restoredServer.setMaps(new ArrayList<ParkourMap>());
			restoredStore.getServers().add(restoredServer);
			changed = true;
		}

		if (restoredServer.getMaps() == null) {
			restoredServer.setMaps(new ArrayList<ParkourMap>());
			changed = true;
		}

		ParkourMap restoredMap = findMapByIdInServer(restoredServer, RESTORED_MAP_ID);
		if (restoredMap == null) {
			restoredMap = new ParkourMap();
			restoredMap.setId(RESTORED_MAP_ID);
			restoredMap.setJumps(new ArrayList<Jump>());
			restoredServer.getMaps().add(restoredMap);
			changed = true;
		}

		if (restoredMap.getJumps() == null) {
			restoredMap.setJumps(new ArrayList<Jump>());
			changed = true;
		}

		return changed;
	}

	public static ParkourMap getGlobalMap() {
		ensureStoresInitialized();
		ServerProfile globalServer = findServerByIdInStore(globalStore, GLOBAL_SERVER_ID);
		if (globalServer == null || globalServer.getMaps() == null) {
			return null;
		}
		return findMapByIdInServer(globalServer, GLOBAL_MAP_ID);
	}

	public static ParkourMap getRestoredMap() {
		ensureStoresInitialized();
		ServerProfile restoredServer = findServerByIdInStore(restoredStore, RESTORED_SERVER_ID);
		if (restoredServer == null || restoredServer.getMaps() == null) {
			return null;
		}
		return findMapByIdInServer(restoredServer, RESTORED_MAP_ID);
	}

	public static boolean removeJumpFromRestoredStrats(Jump legacyJump) {
		ensureStoresInitialized();

		if (legacyJump == null) {
			return false;
		}

		ParkourMap restoredMap = getRestoredMap();
		if (restoredMap == null || restoredMap.getJumps() == null) {
			return false;
		}

		boolean removed = restoredMap.getJumps().remove(legacyJump);

		// Fallback: if the object reference does not match (e.g., deserialized copy), remove by coords.
		if (!removed) {
			int lx = legacyJump.getX();
			int ly = legacyJump.getY();
			int lz = legacyJump.getZ();

			for (int i = 0; i < restoredMap.getJumps().size(); i++) {
				Jump j = restoredMap.getJumps().get(i);
				if (j == null) continue;

				if (j.getX() == lx && j.getY() == ly && j.getZ() == lz) {
					restoredMap.getJumps().remove(i);
					removed = true;
					break;
				}
			}
		}

		if (!removed) {
			return false;
		}

		// Clear selection if it pointed to the removed legacy jump.
		if (selectedJump == legacyJump) {
			selectedJump = null;
		}

		saveToFile();
		autoSelectDefaultContext();
		return true;
	}

	public static boolean isGlobalServer(ServerProfile server) {
		return server != null && GLOBAL_SERVER_ID.equals(server.getId());
	}

	public static boolean isRestoredServer(ServerProfile server) {
		return server != null && RESTORED_SERVER_ID.equals(server.getId());
	}

	public static boolean isProtectedServer(ServerProfile server) {
		return isGlobalServer(server) || isRestoredServer(server);
	}

	public static boolean isGlobalMap(ParkourMap map) {
		if (map == null) return false;
		ParkourMap gm = getGlobalMap();
		return map == gm || (gm != null && GLOBAL_MAP_ID.equals(map.getId()));
	}

	public static boolean isRestoredMap(ParkourMap map) {
		if (map == null) return false;
		ParkourMap rm = getRestoredMap();
		return map == rm || (rm != null && RESTORED_MAP_ID.equals(map.getId()));
	}

	// -------------------------
	// Selection getters/setters
	// -------------------------

	public static DataStore getDataStore() {
		ensureStoresInitialized();
		DataStore view = new DataStore();
		view.setServers(getServers());
		return view;
	}

	public static ServerProfile getSelectedServer() {
		ensureStoresInitialized();
		return selectedServer;
	}

	public static ParkourMap getSelectedMap() {
		ensureStoresInitialized();
		return selectedMap;
	}

	public static Jump getSelectedJump() {
		ensureStoresInitialized();
		return selectedJump;
	}

	public static void setSelectedServer(ServerProfile server) {
		ensureStoresInitialized();

		selectedServer = server;

		if (selectedServer == null) {
			selectedMap = null;
			selectedJump = null;
			return;
		}

		if (selectedServer.getMaps() == null) {
			selectedServer.setMaps(new ArrayList<ParkourMap>());
		}
	}

	public static void setSelectedMap(ParkourMap map) {
		ensureStoresInitialized();

		selectedMap = map;

		if (selectedMap == null) {
			selectedJump = null;
			return;
		}

		if (selectedMap.getJumps() == null) {
			selectedMap.setJumps(new ArrayList<Jump>());
		}
	}

	public static void setSelectedJump(Jump jump) {
		ensureStoresInitialized();
		selectedJump = jump;
		if (selectedJump != null && selectedJump.getReminders() == null) {
			selectedJump.setReminders(new ArrayList<Reminder>());
		}
	}

	private static void autoSelectDefaultContext() {
		ensureStoresInitialized();
		ensureGlobalStructure();
		ensureRestoredStructure();

		ServerProfile bestS = null;
		ParkourMap bestM = null;
		Jump bestJ = null;

		for (ServerProfile s : getServers()) {
			if (s == null || s.getMaps() == null) continue;

			for (ParkourMap m : s.getMaps()) {
				if (m == null || m.getJumps() == null) continue;

				for (Jump j : m.getJumps()) {
					if (j == null) continue;

					ArrayList<Reminder> rs = j.getReminders();
					if (rs == null || rs.isEmpty()) continue;

					int idx = j.getActiveReminderIndex();
					if (idx >= 0 && idx < rs.size()) {
						bestS = s;
						bestM = m;
						bestJ = j;
						break;
					}

					if (bestJ == null) {
						bestS = s;
						bestM = m;
						bestJ = j;
					}
				}
			}
		}

		if (bestS != null) setSelectedServer(bestS);
		if (bestM != null) setSelectedMap(bestM);
		if (bestJ != null) setSelectedJump(bestJ);

		if (selectedJump != null) {
			getActiveReminderOrFallback(selectedJump);
		}
	}

	private static String getLineSafe(ArrayList<String> lines, int idx) {
		if (lines == null || idx < 0 || idx >= lines.size()) {
			return "";
		}
		String s = lines.get(idx);
		return s != null ? s : "";
	}

	// -------------------------
	// CRUD - Servers (Shared only; Global/Restored are fixed)
	// -------------------------

	public static ArrayList<ServerProfile> getServers() {
		ensureStoresInitialized();
		ensureGlobalStructure();
		ensureRestoredStructure();

		ArrayList<ServerProfile> out = new ArrayList<ServerProfile>();

		ServerProfile g = findServerByIdInStore(globalStore, GLOBAL_SERVER_ID);
		if (g != null) {
			out.add(g);
		}

		ServerProfile r = findServerByIdInStore(restoredStore, RESTORED_SERVER_ID);
		if (r != null) {
			out.add(r);
		}

		if (sharedStore != null && sharedStore.getServers() != null) {
			for (ServerProfile s : sharedStore.getServers()) {
				if (s == null) continue;
				if (GLOBAL_SERVER_ID.equals(s.getId())) continue;
				if (RESTORED_SERVER_ID.equals(s.getId())) continue;
				out.add(s);
			}
		}

		return out;
	}

	public static boolean createServer(String id) {
		ensureStoresInitialized();
		if (!isValidId(id)) return false;

		String trimmed = id.trim();

		if (GLOBAL_SERVER_ID.equals(trimmed)) return false;
		if (RESTORED_SERVER_ID.equals(trimmed)) return false;

		if (findServerById(trimmed) != null) return false;

		if (sharedStore.getServers() == null) {
			sharedStore.setServers(new ArrayList<ServerProfile>());
		}

		ServerProfile s = new ServerProfile();
		s.setId(trimmed);
		s.setMaps(new ArrayList<ParkourMap>());
		sharedStore.getServers().add(s);

		saveToFile();
		return true;
	}

	public static boolean renameServer(ServerProfile server, String newId) {
		ensureStoresInitialized();
		if (server == null) return false;

		if (isProtectedServer(server)) {
			return false;
		}

		if (!isValidId(newId)) return false;

		String trimmed = newId.trim();
		if (GLOBAL_SERVER_ID.equals(trimmed)) return false;
		if (RESTORED_SERVER_ID.equals(trimmed)) return false;

		ServerProfile existing = findServerById(trimmed);
		if (existing != null && existing != server) return false;

		server.setId(trimmed);
		saveToFile();
		return true;
	}

	public static boolean removeServer(ServerProfile server) {
		ensureStoresInitialized();
		if (server == null) return false;

		if (isProtectedServer(server)) {
			return false;
		}

		if (sharedStore.getServers() == null) {
			sharedStore.setServers(new ArrayList<ServerProfile>());
		}

		boolean removed = sharedStore.getServers().remove(server);
		if (removed) {
			if (selectedServer == server) {
				selectedServer = null;
				selectedMap = null;
				selectedJump = null;
			}
			saveToFile();
		}
		return removed;
	}

	public static ServerProfile findServerById(String id) {
		ensureStoresInitialized();
		if (id == null) return null;

		ServerProfile g = findServerByIdInStore(globalStore, id);
		if (g != null) return g;

		ServerProfile r = findServerByIdInStore(restoredStore, id);
		if (r != null) return r;

		return findServerByIdInStore(sharedStore, id);
	}

	private static ServerProfile findServerByIdInStore(DataStore store, String id) {
		if (store == null || id == null) return null;
		if (store.getServers() == null) return null;

		for (ServerProfile s : store.getServers()) {
			if (s != null && id.equals(s.getId())) return s;
		}
		return null;
	}

	// -------------------------
	// CRUD - Maps (Shared only; Global/Restored maps are fixed)
	// -------------------------

	public static ArrayList<ParkourMap> getMaps(ServerProfile server) {
		ensureStoresInitialized();
		if (server == null) return new ArrayList<ParkourMap>();
		if (server.getMaps() == null) server.setMaps(new ArrayList<ParkourMap>());
		return server.getMaps();
	}

	public static boolean createMap(ServerProfile server, String id) {
		// Backward-compatible overload: no sections by default.
		return createMap(server, id, false, 0, null);
	}

	public static boolean createMap(ServerProfile server, String id, boolean sectionsEnabled, int sectionsCount, String[] sectionNames) {
		ensureStoresInitialized();
		if (server == null) return false;

		if (isProtectedServer(server)) return false;

		if (!isValidId(id)) return false;
		if (findMapById(server, id) != null) return false;

		ParkourMap m = new ParkourMap();
		m.setId(id);
		m.setJumps(new ArrayList<Jump>());

		// Sections config (offline v1.6)
		m.setSectionsEnabled(sectionsEnabled);
		if (!sectionsEnabled) {
			m.setSectionsCountRaw(0);
		} else {
			// Force 1..4
			int c = sectionsCount;
			if (c < 1) c = 1;
			if (c > 4) c = 4;
			m.setSectionsCountRaw(c);
		}
		if (sectionNames != null) {
			m.setSectionNames(sectionNames);
		}

		server.getMaps().add(m);

		saveToFile();
		return true;
	}

	public static boolean updateMapConfig(ServerProfile server, ParkourMap map, String newId,
										  boolean sectionsEnabled, int sectionsCount, String[] sectionNames) {
		ensureStoresInitialized();
		if (server == null || map == null) return false;

		if (isProtectedServer(server)) return false;

		String t = (newId != null) ? newId.trim() : "";
		if (!isValidId(t)) return false;

		ParkourMap existing = findMapById(server, t);
		if (existing != null && existing != map) return false;

		// Apply id
		map.setId(t);

		// Apply sections config
		map.setSectionsEnabled(sectionsEnabled);

		if (!sectionsEnabled) {
			map.setSectionsCountRaw(0);
			// Keep names as-is or reset; keeping is fine (future re-enable).
		} else {
			int c = sectionsCount;
			if (c < 1) c = 1;
			if (c > 4) c = 4;
			map.setSectionsCountRaw(c);

			if (sectionNames != null) {
				map.setSectionNames(sectionNames);
			}
		}

		saveToFile();
		return true;
	}

	public static boolean removeMap(ServerProfile server, ParkourMap map) {
		ensureStoresInitialized();
		if (server == null || map == null) return false;

		if (isProtectedServer(server)) return false;

		boolean removed = server.getMaps().remove(map);
		if (removed) {
			if (selectedMap == map) {
				selectedMap = null;
				selectedJump = null;
			}
			saveToFile();
		}
		return removed;
	}

	public static ParkourMap findMapById(ServerProfile server, String id) {
		ensureStoresInitialized();
		if (server == null || id == null) return null;
		if (server.getMaps() == null) server.setMaps(new ArrayList<ParkourMap>());

		for (ParkourMap m : server.getMaps()) {
			if (m != null && id.equals(m.getId())) return m;
		}
		return null;
	}

	private static ParkourMap findMapByIdInServer(ServerProfile server, String id) {
		if (server == null || id == null) return null;
		if (server.getMaps() == null) return null;

		for (ParkourMap m : server.getMaps()) {
			if (m != null && id.equals(m.getId())) return m;
		}
		return null;
	}

	// -------------------------
	// CRUD - Jumps (ALLOW same name; identity is name+coords for reuse)
	// -------------------------

	public static ArrayList<Jump> getJumps(ParkourMap map) {
		ensureStoresInitialized();
		if (map == null) return new ArrayList<Jump>();
		if (map.getJumps() == null) map.setJumps(new ArrayList<Jump>());
		return map.getJumps();
	}

	// Treat a section as "inactive" if it does not have a valid range.
	// Inactive sections remain stored, but the GUI should not render them.
	private static boolean isSectionRangeActive(MapSection s, int jumpsSize) {
		if (s == null) return false;
		int a = s.getStartJumpIndex();
		int b = s.getEndJumpIndex();

		if (a < 0 || b < 0) return false;
		if (a > b) return false;
		if (jumpsSize <= 0) return false;
		if (a >= jumpsSize || b >= jumpsSize) return false;

		return true;
	}

	private static void deactivateSectionRange(MapSection s) {
		if (s == null) return;
		s.setStartJumpIndex(-1);
		s.setEndJumpIndex(-1);
	}

	public static void insertJumpAtEndOfSection(ParkourMap map, int levelOneBased, MapSection targetSection, Jump jump) {
		if (map == null || targetSection == null || jump == null) {
			return;
		}

		int max = map.getMaxSectionLevelsAllowed();
		if (max <= 0) {
			return;
		}
		if (levelOneBased < 1 || levelOneBased > max) {
			return;
		}

		ArrayList<Jump> jumps = map.getJumps();
		if (jumps == null) {
			return;
		}

		int oldIndex = indexOfJumpByReference(jumps, jump);

		// Remove existing instance (if present) and shift section indices.
		if (oldIndex >= 0) {
			jumps.remove(oldIndex);
			shiftAllSectionRangesAfterRemove(map, oldIndex, max);
		}

		int sizeAfterRemove = jumps.size();
		boolean active = isSectionRangeActive(targetSection, sizeAfterRemove);

		// Capture the target end BEFORE insertion, after potential removal-shifts.
		// This is the boundary we want to "stick to" for any other sections that ended here.
		int oldTargetEnd = -1;
		if (active) {
			oldTargetEnd = targetSection.getEndJumpIndex();
		}

		int insertPos;
		if (!active) {
			// Inactive section -> append to end
			insertPos = jumps.size();
		} else {
			insertPos = targetSection.getEndJumpIndex() + 1;
			if (insertPos < 0) insertPos = 0;
			if (insertPos > jumps.size()) insertPos = jumps.size();
		}

		// True only for the "insert at end of the section" scenario (not just any insert).
		boolean isInsertAtEndOfActiveSection = active && (oldTargetEnd >= 0) && (insertPos == oldTargetEnd + 1);

		jumps.add(insertPos, jump);

		// Shift indices for ALL sections in ALL levels due to insertion.
		shiftAllSectionRangesAfterInsert(map, insertPos, max);

		if (!active) {
			// Activate target section.
			targetSection.setStartJumpIndex(insertPos);
			targetSection.setEndJumpIndex(insertPos);
		} else {
			if (isInsertAtEndOfActiveSection) {
				// NEW RULE:
				// If we inserted at the end of this section, extend ALL active sections (any level)
				// that ended exactly at the same old end boundary.
				extendAllSectionEndsThatWereAt(map, oldTargetEnd, max);

				// NOTE: targetSection is included in the extension above,
				// so we do NOT manually do targetSection.setEndJumpIndex(end+1) here.
			} else {
				// Insert happened inside the section (or clamped away from exact end) -> only target should extend.
				targetSection.setEndJumpIndex(targetSection.getEndJumpIndex() + 1);
			}
		}

		saveToFile();
	}

	/**
	 * Extends endJumpIndex by +1 for all ACTIVE sections in ALL levels
	 * that ended exactly at oldEndIndex.
	 *
	 * This is used when inserting a jump at the end of a section, to keep
	 * other "aligned" sections (parents/children on other levels) ending at the same boundary.
	 */
	private static void extendAllSectionEndsThatWereAt(ParkourMap map, int oldEndIndex, int maxLevel) {
		if (map == null) return;
		if (oldEndIndex < 0) return;

		for (int lvl = 1; lvl <= maxLevel; lvl++) {
			ArrayList<MapSection> sections = map.getSectionsForLevel(lvl);
			if (sections == null || sections.isEmpty()) continue;

			for (int i = 0; i < sections.size(); i++) {
				MapSection s = sections.get(i);
				if (s == null) continue;

				int start = s.getStartJumpIndex();
				int end = s.getEndJumpIndex();

				// Inactive sections -> ignore
				if (start < 0 || end < 0) continue;

				// Normalize for safety
				if (end < start) {
					int t = start;
					start = end;
					end = t;
				}

				if (end == oldEndIndex) {
					s.setEndJumpIndex(end + 1);
				}
			}
		}
	}

	public static void moveExistingJumpToEndOfSection(ParkourMap map, int levelOneBased, MapSection targetSection, Jump jump) {
		ensureStoresInitialized();
		if (map == null || targetSection == null || jump == null) return;

		insertJumpAtEndOfSection(map, levelOneBased, targetSection, jump);
	}

	/**
	 * Shifts section start/end indices by +1 for all sections in all levels
	 * when a new jump is inserted at insertPos in the jumps list.
	 */
	private static void shiftAllSectionRangesAfterInsert(ParkourMap map, int insertPos, int maxLevel) {
		if (map == null) return;

		for (int lvl = 1; lvl <= maxLevel; lvl++) {
			ArrayList<MapSection> sections = map.getSectionsForLevel(lvl);
			if (sections == null) continue;

			for (int i = 0; i < sections.size(); i++) {
				MapSection s = sections.get(i);
				if (s == null) continue;

				int start = s.getStartJumpIndex();
				int end = s.getEndJumpIndex();

				// Inactive section -> keep as inactive
				if (start < 0 || end < 0) {
					continue;
				}

				// If insertion is before or at start, both start and end shift.
				if (start >= insertPos) {
					start += 1;
					end += 1;
				}
				// If insertion is inside the section range (before or at end), end shifts.
				else if (end >= insertPos) {
					end += 1;
				}

				s.setStartJumpIndex(start);
				s.setEndJumpIndex(end);
			}
		}
	}

	/**
	 * Shifts section start/end indices after a jump is removed.
	 * If a section becomes invalid/out-of-range, it is deactivated (-1...-1) so it exists but is not rendered.
	 */
	private static void shiftAllSectionRangesAfterRemove(ParkourMap map, int removedIndex, int maxLevels) {
		if (map == null) return;

		if (maxLevels < 1) return;
		if (maxLevels > 4) maxLevels = 4;

		ArrayList<Jump> jumps = map.getJumps();
		int jumpCount = (jumps != null) ? jumps.size() : 0;

		for (int lvl = 1; lvl <= maxLevels; lvl++) {
			ArrayList<MapSection> secs = map.getSectionsForLevel(lvl);
			if (secs == null || secs.isEmpty()) continue;

			for (int i = 0; i < secs.size(); i++) {
				MapSection s = secs.get(i);
				if (s == null) continue;

				int start = s.getStartJumpIndex();
				int end = s.getEndJumpIndex();

				// Inactive section -> keep as inactive
				if (start < 0 || end < 0) {
					continue;
				}

				// If the removed index is before the section, shift the whole range left.
				if (removedIndex < start) {
					start -= 1;
					end -= 1;
				}
				// If removed index was inside the section, the section loses one element.
				else if (removedIndex >= start && removedIndex <= end) {
					end -= 1;
				}
				// else: removedIndex > end => unaffected

				// Validate against current jump count AFTER removal.
				// If out-of-range or empty/invalid, deactivate it instead of clamping.
				if (jumpCount <= 0) {
					deactivateSectionRange(s);
					continue;
				}

				if (end < start || end < 0 || start < 0) {
					deactivateSectionRange(s);
					continue;
				}

				// Out-of-range after shift (e.g., removed last jump, or section was at the end)
				if (start >= jumpCount || end >= jumpCount) {
					deactivateSectionRange(s);
					continue;
				}

				s.setStartJumpIndex(start);
				s.setEndJumpIndex(end);
			}
		}
	}

	public static boolean moveJumpByOneRespectingSections(ParkourMap map, Jump jump, int delta) {
		ensureStoresInitialized();
		if (map == null || jump == null) return false;
		if (delta != -1 && delta != 1) return false;

		ArrayList<Jump> jumps = map.getJumps();
		if (jumps == null || jumps.size() <= 1) return false;

		int idx = indexOfJumpByReference(jumps, jump);
		if (idx < 0) return false;

		int otherIdx = idx + delta;
		if (otherIdx < 0 || otherIdx >= jumps.size()) return false;

		int maxLevels = 0;
		try {
			maxLevels = map.getMaxSectionLevelsAllowed();
		} catch (Throwable ignored) { }

		if (maxLevels < 1) {
			// No sections configured -> plain swap by one.
			java.util.Collections.swap(jumps, idx, otherIdx);
			saveToFile();
			return true;
		}
		if (maxLevels > 4) maxLevels = 4;

		// Try to shift section boundaries on ALL levels where a boundary exists at this move position.
		boolean shiftedAny = shiftBoundariesAllLevels(map, idx, delta, maxLevels);

		if (!shiftedAny) {
			// No boundary at any level -> normal swap by one.
			java.util.Collections.swap(jumps, idx, otherIdx);
		} else {
			// Minimal non-invasive cleanup:
			// If shifting boundaries emptied any section, deactivate it (-1/-1),
			// so it disappears from the table but stays in the section list.
			deactivateEmptySectionsAfterBoundaryShift(map, maxLevels);
		}

		// IMPORTANT: keep empty sections (do NOT remove them automatically).
		// No cleanupEmptySections(map) here.

		saveToFile();
		return true;
	}

	private static void deactivateEmptySectionsAfterBoundaryShift(ParkourMap map, int maxLevels) {
		if (map == null) return;
		if (maxLevels < 1) return;
		if (maxLevels > 4) maxLevels = 4;

		for (int lvl = 1; lvl <= maxLevels; lvl++) {
			ArrayList<MapSection> secs = map.getSectionsForLevel(lvl);
			if (secs == null || secs.isEmpty()) continue;

			for (int i = 0; i < secs.size(); i++) {
				MapSection s = secs.get(i);
				if (s == null) continue;

				int start = s.getStartJumpIndex();
				int end = s.getEndJumpIndex();

				// Already inactive -> keep inactive
				if (start < 0 || end < 0) continue;

				// If boundary shifting made the section empty, deactivate it.
				// This matches the "all jumps removed from the section" behavior.
				if (end < start) {
					deactivateSectionRange(s);
				}
			}
		}
	}

	/**
	 * Shifts section boundaries by one step across ALL levels (1...maxLevels) where a boundary exists.
	 *
	 * Supports 3 cases per level:
	 *  A) left != null && right != null && left != right   (between two sibling sections)
	 *  B) left != null && right == null                    (between a section and "no subsection" area)
	 *  C) left == null && right != null                    (between "no subsection" area and a section)
	 *
	 * BUT: For cases B/C we only allow the shift if BOTH indices are inside the SAME ancestor chain
	 * on all higher levels (1...lvl-1). This prevents a deeper-level section from expanding across a parent boundary.
	 *
	 * Jumps' list order is NOT changed here.
	 *
	 * Returns true if at least one level boundary was shifted.
	 */
	private static boolean shiftBoundariesAllLevels(ParkourMap map, int idx, int delta, int maxLevels) {
		if (map == null) return false;
		if (delta != -1 && delta != 1) return false;

		boolean changed = false;

		for (int lvl = 1; lvl <= maxLevels; lvl++) {
			ArrayList<MapSection> secs = map.getSectionsForLevel(lvl);
			if (secs == null || secs.isEmpty()) continue;

			if (delta == -1) {
				// Move up: boundary between (idx-1) and idx
				int leftIdx = idx - 1;
				if (leftIdx < 0) continue;

				MapSection left = findSectionContainingIndex(secs, leftIdx);
				MapSection right = findSectionContainingIndex(secs, idx);

				// ---- Case A: between two different sections ----
				if (left != null && right != null && left != right) {
					int rStart = normStart(right);
					if (idx == rStart) {
						left.setEndJumpIndex(normEnd(left) + 1);
						right.setStartJumpIndex(rStart + 1);
						changed = true;
					}
					continue;
				}

				// ---- Case B: left is a section, right is "no section" (no subsection) ----
				// Example: parent S2 includes both, but only leftIdx is inside child R2; idx is outside any child.
				if (left != null && right == null) {
					// Only if idx is immediately AFTER left's end, and both indices share the same ancestors on higher levels.
					int lEnd = normEnd(left);
					if (idx == lEnd + 1 && sameAncestorsOnHigherLevels(map, leftIdx, idx, lvl, maxLevels)) {
						left.setEndJumpIndex(lEnd + 1); // expand child to include idx
						changed = true;
					}
					continue;
				}

				// ---- Case C: left is "no section", right is a section ----
				// This would mean idx is inside a section, leftIdx is not; moving up can pull idx out by shifting right.start.
				if (left == null && right != null) {
					int rStart = normStart(right);
					if (idx == rStart && sameAncestorsOnHigherLevels(map, leftIdx, idx, lvl, maxLevels)) {
						right.setStartJumpIndex(rStart + 1); // shrink from the start (idx leaves the section)
						changed = true;
					}
				}
			} else {
				// delta == +1
				// Move down: boundary between idx and (idx+1)
				int rightIdx = idx + 1;

				MapSection left = findSectionContainingIndex(secs, idx);
				MapSection right = findSectionContainingIndex(secs, rightIdx);

				// ---- Case A: between two different sections ----
				if (left != null && right != null && left != right) {
					int lEnd = normEnd(left);
					if (idx == lEnd) {
						left.setEndJumpIndex(lEnd - 1);
						right.setStartJumpIndex(normStart(right) - 1);
						changed = true;
					}
					continue;
				}

				// ---- Case B: left is a section, right is "no section" (no subsection) ----
				// Example: idx is last inside child R2, rightIdx is outside any child but still within same parent S2.
				if (left != null && right == null) {
					int lEnd = normEnd(left);
					if (idx == lEnd && sameAncestorsOnHigherLevels(map, idx, rightIdx, lvl, maxLevels)) {
						left.setEndJumpIndex(lEnd - 1); // shrink child, idx leaves the child
						changed = true;
					}
					continue;
				}

				// ---- Case C: left is "no section", right is a section ----
				// Moving down can pull idx into the right section by expanding right.start to the left.
				if (left == null && right != null) {
					int rStart = normStart(right);
					if (rightIdx == rStart && sameAncestorsOnHigherLevels(map, idx, rightIdx, lvl, maxLevels)) {
						right.setStartJumpIndex(rStart - 1); // expand section to include idx
						changed = true;
					}
				}
			}
		}

		return changed;
	}

	private static int normStart(MapSection s) {
		int a = s.getStartJumpIndex();
		int b = s.getEndJumpIndex();
		return (a <= b) ? a : b;
	}

	private static int normEnd(MapSection s) {
		int a = s.getStartJumpIndex();
		int b = s.getEndJumpIndex();
		return (a <= b) ? b : a;
	}

	/**
	 * Finds the first section whose (normalized) range contains index.
	 * Empty sections (start > end) never match and are simply ignored (kept in list).
	 */
	private static MapSection findSectionContainingIndex(ArrayList<MapSection> secs, int index) {
		if (secs == null || index < 0) return null;

		for (int i = 0; i < secs.size(); i++) {
			MapSection s = secs.get(i);
			if (s == null) continue;

			int a = s.getStartJumpIndex();
			int b = s.getEndJumpIndex();
			if (a > b) {
				// empty section, keep it but it cannot contain anything
				continue;
			}

			if (index >= a && index <= b) {
				return s;
			}
		}

		return null;
	}

	/**
	 * Returns true if indexA and indexB belong to the SAME section chain on all higher levels (1...lvl-1).
	 * If on a higher level there are no sections, we treat it as "same".
	 *
	 * This is the key rule that allows cross-nesting behavior safely:
	 * - A child section may expand into "no subsection" area only if both indices are inside the same parent(s).
	 */
	private static boolean sameAncestorsOnHigherLevels(ParkourMap map, int indexA, int indexB, int currentLevelOneBased, int maxLevels) {
		if (map == null) return false;
		if (currentLevelOneBased <= 1) return true; // no higher levels

		int top = Math.min(maxLevels, currentLevelOneBased - 1);
		for (int lvl = 1; lvl <= top; lvl++) {
			ArrayList<MapSection> secs = map.getSectionsForLevel(lvl);
			if (secs == null || secs.isEmpty()) {
				// no constraints at this level
				continue;
			}

			MapSection a = findSectionContainingIndex(secs, indexA);
			MapSection b = findSectionContainingIndex(secs, indexB);

			if (a != b) {
				return false;
			}
		}

		return true;
	}

	public static void renameJump(ParkourMap map, Jump jump, String newName) {
		ensureStoresInitialized();
		if (map == null || jump == null) return;

		String t = (newName != null) ? newName.trim() : "";
		if (t.isEmpty()) return;

		jump.setId(t);

		saveToFile();
	}

	public static boolean removeJump(ParkourMap map, Jump jump) {
		ensureStoresInitialized();
		if (map == null || jump == null) return false;

		ArrayList<Jump> jumps = map.getJumps();
		if (jumps == null || jumps.isEmpty()) return false;

		int idx = indexOfJumpByReference(jumps, jump);
		if (idx < 0) return false;

		jumps.remove(idx);

		int max = map.getMaxSectionLevelsAllowed();
		if (max > 4) max = 4;

		if (max > 0) {
			shiftAllSectionRangesAfterRemove(map, idx, max);
		}

		if (selectedJump == jump) {
			selectedJump = null;
		}

		saveToFile();
		return true;
	}

	public static boolean transferJump(
			ParkourMap fromMap,
			Jump jump,
			ServerProfile toServer,
			ParkourMap toMap,
			int targetLevelOneBased,
			MapSection targetSection
	) {
		ensureStoresInitialized();

		if (fromMap == null || toMap == null || jump == null) return false;
		if (toMap == fromMap) return false;

		// NOTE: Global IS allowed as a transfer target.
		// (Do not block it here.)

		if (fromMap.getJumps() == null) fromMap.setJumps(new ArrayList<Jump>());
		if (toMap.getJumps() == null) toMap.setJumps(new ArrayList<Jump>());

		// ------------------------------------------------------------
		// 1) Remove from source (like removeJump) + shift section ranges
		// ------------------------------------------------------------
		ArrayList<Jump> fromJumps = fromMap.getJumps();
		int removedIndex = indexOfJumpByReference(fromJumps, jump);
		boolean removed = false;

		if (removedIndex >= 0) {
			fromJumps.remove(removedIndex);
			removed = true;
		} else {
			// Fallback: remove by coords+id if reference does not match
			for (int i = 0; i < fromJumps.size(); i++) {
				Jump j = fromJumps.get(i);
				if (j == null) continue;

				boolean sameId = safeEq(j.getId(), jump.getId());
				boolean sameCoords = (j.getX() == jump.getX() && j.getY() == jump.getY() && j.getZ() == jump.getZ());

				if (sameId && sameCoords) {
					fromJumps.remove(i);
					removedIndex = i;
					removed = true;
					break;
				}
			}
		}

		if (!removed) return false;

		// Apply section shift on source map (exactly like removeJump does)
		int fromMax = 0;
		try { fromMax = fromMap.getMaxSectionLevelsAllowed(); } catch (Throwable ignored) {}
		if (fromMax > 4) fromMax = 4;

		if (fromMax > 0 && removedIndex >= 0) {
			shiftAllSectionRangesAfterRemove(fromMap, removedIndex, fromMax);
		}

		// If selection pointed to this jump, clear it.
		if (selectedJump == jump) {
			selectedJump = null;
		}

		// ------------------------------------------------------------
		// 2) Add to target
		//    If section selected -> behave like InsertJump at end of section
		// ------------------------------------------------------------
		boolean insertedViaSection = false;

		if (targetSection != null && targetLevelOneBased > 0) {
			int toMax = 0;
			try { toMax = toMap.getMaxSectionLevelsAllowed(); } catch (Throwable ignored) {}
			if (toMax > 4) toMax = 4;

			if (toMax > 0 && targetLevelOneBased >= 1 && targetLevelOneBased <= toMax) {
				insertJumpAtEndOfSection(toMap, targetLevelOneBased, targetSection, jump);
				insertedViaSection = true;
			}
		}

		if (!insertedViaSection) {
			toMap.getJumps().add(jump);
		}

		// Update selection to moved item
		ServerProfile owner = findOwningServerForMap(toMap);
		selectedServer = (owner != null) ? owner : toServer;
		selectedMap = toMap;
		selectedJump = jump;

		saveToFile();
		return true;
	}

	private static boolean safeEq(String a, String b) {
		if (a == null && b == null) return true;
		if (a == null || b == null) return false;
		return a.equals(b);
	}

	private static ServerProfile findOwningServerForMap(ParkourMap map) {
		if (map == null) return null;

		for (ServerProfile s : getServers()) {
			if (s == null || s.getMaps() == null) continue;

			for (ParkourMap m : s.getMaps()) {
				if (m == map) {
					return s;
				}

				// Fallback: same id reference match (safer when deserialized)
				if (m != null && map.getId() != null && map.getId().equals(m.getId())) {
					// Only accept if this server actually contains the map object by id
					// (this is a best-effort fallback)
					return s;
				}
			}
		}
		return null;
	}

	// -------------------------
	// Internals
	// -------------------------

	private static boolean normalizeStore(DataStore store) {
		boolean changed = false;

		if (store == null) return false;

		if (store.getServers() == null) {
			store.setServers(new ArrayList<ServerProfile>());
			changed = true;
		}

		for (ServerProfile s : store.getServers()) {
			if (s == null) continue;
			if (s.getMaps() == null) {
				s.setMaps(new ArrayList<ParkourMap>());
				changed = true;
			}

			for (ParkourMap m : s.getMaps()) {
				if (m == null) continue;
				if (m.getJumps() == null) {
					m.setJumps(new ArrayList<Jump>());
					changed = true;
				}

				for (Jump j : m.getJumps()) {
					if (j == null) continue;

					if (j.getReminders() == null) {
						j.setReminders(new ArrayList<Reminder>());
						changed = true;
					}

					boolean isRestoredStratsContext =
							(s != null && "RestoredStrats".equals(s.getId())) ||
									(m != null && "RestoredStrats".equals(m.getId()));

					// Ensure explicit persisted value (no fallback reliance).
					if (j.ensureShowJumpNameInWorldInitialized(isRestoredStratsContext)) {
						changed = true;
					}

					int idx = j.getActiveReminderIndex();
					ArrayList<Reminder> rs = j.getReminders();
					if (rs == null || rs.isEmpty()) {
						if (j.getActiveReminderIndex() != -1) {
							j.setActiveReminderIndex(-1);
							changed = true;
						}
					} else {
						if (idx < -1 || idx >= rs.size()) {
							j.setActiveReminderIndex(-1);
							changed = true;
						}
					}
				}
			}
		}

		return changed;
	}

	// ------------------------------------------------------------
// Reference-based jump lookup (avoids equals()/hashCode() traps)
// ------------------------------------------------------------

	private static int indexOfJumpByReference(java.util.List<Jump> list, Jump target) {
		if (list == null || target == null) return -1;
		for (int i = 0; i < list.size(); i++) {
			if (list.get(i) == target) {
				return i;
			}
		}
		return -1;
	}

	private static JumpPickResult findBestJumpInCrosshairInStore(
			DataStore store,
			EntityPlayerSP player,
			double maxRangeBlocks,
			double maxPerpDistBlocks
	) {
		if (store == null || store.getServers() == null || player == null) {
			return null;
		}

		Vec3 eye = player.getPositionEyes(1.0F);
		Vec3 look = player.getLookVec();
		if (eye == null || look == null) {
			return null;
		}

		double lx = look.xCoord;
		double ly = look.yCoord;
		double lz = look.zCoord;

		// Normalize look vector (defensive)
		double len = Math.sqrt(lx * lx + ly * ly + lz * lz);
		if (len <= 0.000001) {
			return null;
		}
		lx /= len;
		ly /= len;
		lz /= len;

		double maxRange = Math.max(0.0, maxRangeBlocks);
		double maxPerp = Math.max(0.0, maxPerpDistBlocks);
		double maxPerpSq = maxPerp * maxPerp;

		JumpPickResult best = null;

		for (ServerProfile s : store.getServers()) {
			if (s == null || s.getMaps() == null) continue;

			for (ParkourMap m : s.getMaps()) {
				if (m == null || m.getJumps() == null) continue;

				for (Jump j : m.getJumps()) {
					if (j == null) continue;

					int x = j.getX();
					int y = j.getY();
					int z = j.getZ();

					if (x == 0 && y == 0 && z == 0) {
						continue;
					}

					// Only consider jumps that actually have strategies
					if (j.getReminders() == null || j.getReminders().isEmpty()) {
						continue;
					}

					// Use center of block for nicer aiming
					double jx = x + 0.5;
					double jy = y + 0.5;
					double jz = z + 0.5;

					double tx = jx - eye.xCoord;
					double ty = jy - eye.yCoord;
					double tz = jz - eye.zCoord;

					// Projection length along the view ray
					double t = (tx * lx) + (ty * ly) + (tz * lz);

					if (t <= 0.0 || t > maxRange) {
						continue;
					}

					// Closest point on ray to jump point
					double cx = eye.xCoord + (lx * t);
					double cy = eye.yCoord + (ly * t);
					double cz = eye.zCoord + (lz * t);

					double dx = jx - cx;
					double dy = jy - cy;
					double dz = jz - cz;

					double perpSq = (dx * dx) + (dy * dy) + (dz * dz);
					if (perpSq > maxPerpSq) {
						continue;
					}

					JumpPickResult cand = new JumpPickResult(s, m, j, perpSq, t);
					if (best == null || isCrosshairPickBetter(cand, best)) {
						best = cand;
					}
				}
			}
		}

		return best;
	}

	public static Jump selectNearestJumpToPlayer() {
		ensureStoresInitialized();

		Minecraft mc = Minecraft.getMinecraft();
		if (mc == null || mc.thePlayer == null) {
			return null;
		}

		EntityPlayerSP player = mc.thePlayer;

		Jump bestJump = null;
		ParkourMap bestMap = null;
		ServerProfile bestServer = null;

		double bestDistSq = Double.MAX_VALUE;

		// Search: Global + Restored + Shared (in that order)
		JumpPickResult globalPick = findNearestJumpInStore(globalStore, player);
		if (globalPick != null && globalPick.jump != null) {
			bestJump = globalPick.jump;
			bestMap = globalPick.map;
			bestServer = globalPick.server;
			bestDistSq = globalPick.distSq;
		}

		JumpPickResult restoredPick = findNearestJumpInStore(restoredStore, player);
		if (restoredPick != null && restoredPick.jump != null && restoredPick.distSq < bestDistSq) {
			bestJump = restoredPick.jump;
			bestMap = restoredPick.map;
			bestServer = restoredPick.server;
			bestDistSq = restoredPick.distSq;
		}

		JumpPickResult sharedPick = findNearestJumpInStore(sharedStore, player);
		if (sharedPick != null && sharedPick.jump != null && sharedPick.distSq < bestDistSq) {
			bestJump = sharedPick.jump;
			bestMap = sharedPick.map;
			bestServer = sharedPick.server;
			bestDistSq = sharedPick.distSq;
		}

		if (bestJump == null) {
			return null;
		}

		selectedServer = bestServer;
		selectedMap = bestMap;
		selectedJump = bestJump;

		if (selectedJump.getReminders() == null) {
			selectedJump.setReminders(new ArrayList<Reminder>());
		}

		return selectedJump;
	}

	private static class JumpPickResult {
		final ServerProfile server;
		final ParkourMap map;
		final Jump jump;

		// Used by nearest selection
		final double distSq;

		// Used by crosshair selection
		final double perpDistSq;
		final double rayT;

		// Nearest constructor
		JumpPickResult(ServerProfile server, ParkourMap map, Jump jump, double distSq) {
			this.server = server;
			this.map = map;
			this.jump = jump;
			this.distSq = distSq;
			this.perpDistSq = Double.MAX_VALUE;
			this.rayT = Double.MAX_VALUE;
		}

		// Crosshair constructor
		JumpPickResult(ServerProfile server, ParkourMap map, Jump jump, double perpDistSq, double rayT) {
			this.server = server;
			this.map = map;
			this.jump = jump;
			this.distSq = Double.MAX_VALUE;
			this.perpDistSq = perpDistSq;
			this.rayT = rayT;
		}
	}


	private static JumpPickResult findNearestJumpInStore(DataStore store, EntityPlayerSP player) {
		if (store == null || store.getServers() == null || player == null) {
			return null;
		}

		Jump bestJump = null;
		ParkourMap bestMap = null;
		ServerProfile bestServer = null;
		double bestDistSq = Double.MAX_VALUE;

		for (ServerProfile s : store.getServers()) {
			if (s == null || s.getMaps() == null) continue;

			for (ParkourMap m : s.getMaps()) {
				if (m == null || m.getJumps() == null) continue;

				for (Jump j : m.getJumps()) {
					if (j == null) continue;

					int x = j.getX();
					int y = j.getY();
					int z = j.getZ();

					if (x == 0 && y == 0 && z == 0) {
						continue;
					}

					double dx = player.posX - x;
					double dy = player.posY - y;
					double dz = player.posZ - z;
					double distSq = (dx * dx) + (dy * dy) + (dz * dz);

					if (distSq < bestDistSq) {
						bestDistSq = distSq;
						bestJump = j;
						bestMap = m;
						bestServer = s;
					}
				}
			}
		}

		if (bestJump == null) {
			return null;
		}

		return new JumpPickResult(bestServer, bestMap, bestJump, bestDistSq);
	}

	// -------------------------
	// Legacy import (Insert)
	// -------------------------

	private static class LegacyEntry {
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
		sendChat(Minecraft.getMinecraft(),
				EnumChatFormatting.DARK_AQUA + "[ParkourStrats] " + EnumChatFormatting.RED + msg);
	}

	private static void sendLegacyInfo(String msg) {
		sendChat(Minecraft.getMinecraft(),
				EnumChatFormatting.DARK_AQUA + "[ParkourStrats] " + EnumChatFormatting.AQUA + msg);
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
	public static boolean insertLegacyFileIntoRestoredStrats() {
		ensureStoresInitialized();

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

			ParkourMap restoredMap = getRestoredMap();
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
				saveToFile();
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

	public static boolean insertBetterLinkCraftFileIntoRestoredStrats() {
		ensureStoresInitialized();

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

			ParkourMap restoredMap = getRestoredMap();
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
				saveToFile();
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
		sendChat(Minecraft.getMinecraft(),
				EnumChatFormatting.DARK_AQUA + "[ParkourStrats] " + EnumChatFormatting.RED + msg);
	}

	private static void sendBlcInfo(String msg) {
		sendChat(Minecraft.getMinecraft(),
				EnumChatFormatting.DARK_AQUA + "[ParkourStrats] " + EnumChatFormatting.AQUA + msg);
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

	private static boolean isValidId(String id) {
		if (id == null) return false;
		String trimmed = id.trim();
		if (trimmed.isEmpty()) return false;
		return trimmed.length() <= 48;
	}

	private static void postSectionSyncErrorToChat_() {
		try {
			final String err = getLastSectionSyncError();
			if (err == null || err.trim().isEmpty()) {
				return;
			}

			Minecraft mc = Minecraft.getMinecraft();
			if (mc == null) return;

			mc.addScheduledTask(new Runnable() {
				@Override
				public void run() {
					sendChat(Minecraft.getMinecraft(),
							EnumChatFormatting.DARK_AQUA + "[ParkourStrats] " +
									EnumChatFormatting.RED + "Sync Sections failed: " +
									EnumChatFormatting.GRAY + err);
				}
			});
		} catch (Throwable ignored) {
		}
	}

	private static void sendChat(Minecraft mc, String msg) {
		if (mc == null || mc.thePlayer == null || msg == null) {
			return;
		}
		mc.thePlayer.addChatMessage(new ChatComponentText(msg));
	}
}