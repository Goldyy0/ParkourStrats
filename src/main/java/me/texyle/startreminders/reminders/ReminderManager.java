package me.texyle.startreminders.reminders;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import me.texyle.startreminders.StratReminders;
import me.texyle.startreminders.data.DataStore;
import me.texyle.startreminders.data.Jump;
import me.texyle.startreminders.data.ParkourMap;
import me.texyle.startreminders.data.ServerProfile;
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
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.HashSet;
import java.util.Set;

public class ReminderManager {

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

	// Folder for datasets
	private static final String DATASET_DIR = "StratReminders/datasets";

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
	// Google Sheets sync
	// -------------------------

	// Tracks which (server,map) already announced the first successful sync in chat.
	private static final Set<String> sheetSyncAnnouncedKeys = new HashSet<String>();

	private static final String SHEETS_CACHE_DIR = "StratReminders/sheets_cache";
	private static final long SHEET_AUTO_SYNC_INTERVAL_MS = 5L * 60L * 1000L;

	private static long lastAutoSyncAttemptMs = 0L;

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

	@SubscribeEvent
	public void onClientTick(TickEvent.ClientTickEvent event) {
		if (event == null || event.phase != TickEvent.Phase.END) {
			return;
		}

		ensureStoresInitialized();

		long now = System.currentTimeMillis();
		if (now - lastAutoSyncAttemptMs < SHEET_AUTO_SYNC_INTERVAL_MS) {
			return;
		}
		lastAutoSyncAttemptMs = now;

		// Auto-sync only the currently selected map (no "sync all")
		if (selectedMap == null || !selectedMap.hasSheetConfigured()) {
			return;
		}

		requestSheetSyncForMap(selectedServer, selectedMap, false);
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

		List<String> header = rows.get(0);
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

		int imported = 0;
		int skipped = 0;

		String lastJumpName = "";

		// Carry-down for context columns (Google Sheets merged cells export to empty CSV cells on subsequent rows)
		String lastSection = "";
		String lastSubSection = "";
		String lastArea = "";
		String lastCp = "";

		// Build candidate lists once (template order matters)
		java.util.Map<String, java.util.List<Jump>> jumpsByNormName = buildJumpCandidatesByNormalizedName(map);

		// Occurrence counter per (context + jumpName)
		java.util.Map<String, Integer> occurrenceByKey = new java.util.HashMap<String, Integer>();

		// Optional context columns (if present in sheet)
		int colSection = findHeaderCol(header, "section");
		int colSubSection = findHeaderCol(header, "sub-section", "subsection", "sub section", "sub_section");
		int colArea = findHeaderCol(header, "area");
		int colCp = findHeaderCol(header, "cp", "checkpoint");

		for (int r = 1; r < rows.size(); r++) {
			List<String> row = rows.get(r);
			if (row == null) continue;

			String jumpName = CsvUtils.getCell(row, cols.jump).trim();
			if (jumpName.isEmpty()) {
				jumpName = lastJumpName; // carry-down for merged cells
			} else {
				lastJumpName = jumpName;
			}

			if (jumpName == null || jumpName.trim().isEmpty()) {
				skipped++;
				continue;
			}

			// Carry-down for context columns (same rule as jumpName carry-down)
			String sectionVal = "";
			if (colSection >= 0) {
				String v = CsvUtils.getCell(row, colSection).trim();
				if (v.isEmpty()) v = lastSection;
				else lastSection = v;
				sectionVal = v;
			}

			String subSectionVal = "";
			if (colSubSection >= 0) {
				String v = CsvUtils.getCell(row, colSubSection).trim();
				if (v.isEmpty()) v = lastSubSection;
				else lastSubSection = v;
				subSectionVal = v;
			}

			String areaVal = "";
			if (colArea >= 0) {
				String v = CsvUtils.getCell(row, colArea).trim();
				if (v.isEmpty()) v = lastArea;
				else lastArea = v;
				areaVal = v;
			}

			String cpVal = "";
			if (colCp >= 0) {
				String v = CsvUtils.getCell(row, colCp).trim();
				if (v.isEmpty()) v = lastCp;
				else lastCp = v;
				cpVal = v;
			}

			// Build a stable occurrence key using resolved (carried-down) context values
			String key = buildOccurrenceKey(jumpName, sectionVal, subSectionVal, areaVal, cpVal);

			int occ = occurrenceByKey.containsKey(key) ? occurrenceByKey.get(key).intValue() + 1 : 1;
			occurrenceByKey.put(key, Integer.valueOf(occ));

			Jump target = resolveJumpByNameUsingOccurrence(map, jumpName.trim(), occ, jumpsByNormName);
			if (target == null) {
				// Requirement: do not create new jumps without coords
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

	// Build candidates in template order: normalized jump name -> list of jumps
	private static java.util.Map<String, java.util.List<Jump>> buildJumpCandidatesByNormalizedName(ParkourMap map) {
		java.util.Map<String, java.util.List<Jump>> out = new java.util.HashMap<String, java.util.List<Jump>>();
		if (map == null || map.getJumps() == null) return out;

		for (Jump j : map.getJumps()) {
			if (j == null) continue;

			// Skip unset coords (you require coords)
			if (j.getX() == 0 && j.getY() == 0 && j.getZ() == 0) continue;

			String id = j.getId() != null ? j.getId() : "";
			String norm = CsvUtils.normalizeKey(id);

			java.util.List<Jump> list = out.get(norm);
			if (list == null) {
				list = new java.util.ArrayList<Jump>();
				out.put(norm, list);
			}
			list.add(j);
		}

		return out;
	}

	// Resolve a jump: if unique -> return; if duplicates -> use nth occurrence
	private static Jump resolveJumpByNameUsingOccurrence(
			ParkourMap map,
			String jumpName,
			int occurrence,
			java.util.Map<String, java.util.List<Jump>> jumpsByNormName
	) {
		if (map == null || jumpName == null) return null;

		String norm = CsvUtils.normalizeKey(jumpName);
		java.util.List<Jump> candidates = jumpsByNormName != null ? jumpsByNormName.get(norm) : null;
		if (candidates == null || candidates.isEmpty()) {
			return null;
		}

		if (candidates.size() == 1) {
			return candidates.get(0);
		}

		int idx = occurrence - 1;
		if (idx >= 0 && idx < candidates.size()) {
			return candidates.get(idx);
		}

		// If sheet has more occurrences than template, do not guess
		return null;
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
			String cp
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

			String first = r.lines.get(0);
			if (first != null && "PLACEHOLDER".equals(first.trim())) {
				rs.remove(i);
			}
		}

		if (rs.isEmpty()) {
			j.setActiveReminderIndex(-1);
		} else if (j.getActiveReminderIndex() < 0 || j.getActiveReminderIndex() >= rs.size()) {
			j.setActiveReminderIndex(0);
		}
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

					DrawUtils.drawTextAtCoords(active.lines, x, y, z, event.partialTicks);
				}
			}
		}
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

		normalizeStore(globalStore);

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

		normalizeStore(restoredStore);

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

		normalizeStore(sharedStore);

		if (needsSave) {
			saveStoreToPath(sharedStore, SHARED_FILE_PATH);
		}

		isSharedLoaded = true;
	}

	private static void ensureStoresInitialized() {
		ensureGlobalLoadedAndNormalized();
		ensureRestoredLoadedAndNormalized();
		ensureSharedLoadedAndNormalized();
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
		ensureStoresInitialized();
		if (server == null) return false;

		if (isProtectedServer(server)) return false;

		if (!isValidId(id)) return false;
		if (findMapById(server, id) != null) return false;

		ParkourMap m = new ParkourMap();
		m.setId(id);
		m.setJumps(new ArrayList<Jump>());
		server.getMaps().add(m);

		saveToFile();
		return true;
	}

	public static boolean renameMap(ServerProfile server, ParkourMap map, String newId) {
		ensureStoresInitialized();
		if (server == null || map == null) return false;

		if (isProtectedServer(server)) return false;

		if (!isValidId(newId)) return false;

		ParkourMap existing = findMapById(server, newId);
		if (existing != null && existing != map) return false;

		map.setId(newId);
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

	public static boolean renameJump(ParkourMap map, Jump jump, String newId) {
		ensureStoresInitialized();
		if (map == null || jump == null) return false;
		if (!isValidId(newId)) return false;

		// Allow duplicate names; name alone is not unique.
		jump.setId(newId);
		saveToFile();
		return true;
	}

	public static boolean removeJump(ParkourMap map, Jump jump) {
		ensureStoresInitialized();
		if (map == null || jump == null) return false;

		boolean removed = map.getJumps().remove(jump);
		if (removed) {
			if (selectedJump == jump) {
				selectedJump = null;
			}
			saveToFile();
		}
		return removed;
	}

	public static boolean transferJump(ParkourMap fromMap, Jump jump, ServerProfile toServer, ParkourMap toMap) {
		ensureStoresInitialized();

		if (fromMap == null || toMap == null || jump == null) return false;
		if (toMap == fromMap) return false;

		if (fromMap.getJumps() == null) fromMap.setJumps(new ArrayList<Jump>());
		if (toMap.getJumps() == null) toMap.setJumps(new ArrayList<Jump>());

		// Remove from source
		boolean removed = fromMap.getJumps().remove(jump);

		// Fallback: remove by coords+id if reference does not match
		if (!removed) {
			for (int i = 0; i < fromMap.getJumps().size(); i++) {
				Jump j = fromMap.getJumps().get(i);
				if (j == null) continue;

				boolean sameId = safeEq(j.getId(), jump.getId());
				boolean sameCoords = (j.getX() == jump.getX() && j.getY() == jump.getY() && j.getZ() == jump.getZ());

				if (sameId && sameCoords) {
					fromMap.getJumps().remove(i);
					removed = true;
					break;
				}
			}
		}

		if (!removed) return false;

		// Add to target
		toMap.getJumps().add(jump);

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

	/**
	 * Legacy: returns the first jump with a given name.
	 * With duplicates allowed, this is NOT safe for selection logic.
	 * Prefer findJumpByNameAndCoords(...) or selecting by object reference.
	 */
	public static Jump findJumpById(ParkourMap map, String id) {
		ensureStoresInitialized();
		if (map == null || id == null) return null;
		if (map.getJumps() == null) map.setJumps(new ArrayList<Jump>());

		for (Jump j : map.getJumps()) {
			if (j != null && id.equals(j.getId())) return j;
		}
		return null;
	}

	// -------------------------
	// Internals
	// -------------------------

	private static void normalizeStore(DataStore store) {
		if (store == null) return;

		if (store.getServers() == null) {
			store.setServers(new ArrayList<ServerProfile>());
		}

		for (ServerProfile s : store.getServers()) {
			if (s == null) continue;
			if (s.getMaps() == null) s.setMaps(new ArrayList<ParkourMap>());

			for (ParkourMap m : s.getMaps()) {
				if (m == null) continue;
				if (m.getJumps() == null) m.setJumps(new ArrayList<Jump>());

				for (Jump j : m.getJumps()) {
					if (j == null) continue;
					if (j.getReminders() == null) j.setReminders(new ArrayList<Reminder>());

					int idx = j.getActiveReminderIndex();
					ArrayList<Reminder> rs = j.getReminders();
					if (rs == null || rs.isEmpty()) {
						j.setActiveReminderIndex(-1);
					} else {
						if (idx < -1 || idx >= rs.size()) {
							j.setActiveReminderIndex(-1);
						}
					}
				}
			}
		}
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
		final double distSq;

		JumpPickResult(ServerProfile server, ParkourMap map, Jump jump, double distSq) {
			this.server = server;
			this.map = map;
			this.jump = jump;
			this.distSq = distSq;
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

	private static void sendChat(Minecraft mc, String msg) {
		if (mc == null || mc.thePlayer == null || msg == null) {
			return;
		}
		mc.thePlayer.addChatMessage(new ChatComponentText(msg));
	}
}