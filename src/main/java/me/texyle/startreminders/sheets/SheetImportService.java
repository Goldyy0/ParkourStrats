package me.texyle.startreminders.sheets;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;

import me.texyle.startreminders.data.Jump;
import me.texyle.startreminders.data.MapSection;
import me.texyle.startreminders.data.ParkourMap;
import me.texyle.startreminders.data.SheetFilter;
import me.texyle.startreminders.data.ServerProfile;
import me.texyle.startreminders.persistence.DataStoreRepository;
import me.texyle.startreminders.reminders.Reminder;
import me.texyle.startreminders.services.MapService;
import me.texyle.startreminders.sheets.SheetData.Cell;
import me.texyle.startreminders.utils.ChatUtils;
import me.texyle.startreminders.utils.IdRules;

/**
 * Builds and updates a map from a strategy sheet.
 *
 * The sheet decides which jumps exist, in which order, which sections they belong to and what their strategies are.
 * Jumps start without coordinates ("unplaced"); the player places them in the game afterwards. Every jump remembers
 * which sheet rows it came from, so syncing again updates the same jumps instead of creating new ones.
 * Jumps and strategies the player added by hand are never touched.
 */
public final class SheetImportService {

	private SheetImportService() {}

	private static final int MAX_NAME_LENGTH = 48;

	private static final int[] FALLBACK_COLORS = {
			0xFF3F51B5, 0xFF009688, 0xFFE91E63, 0xFFFF9800, 0xFF8BC34A, 0xFF9C27B0,
			0xFF00BCD4, 0xFFF44336, 0xFFCDDC39, 0xFF795548, 0xFF607D8B, 0xFFFFC107
	};

	// -------------------------
	// Entry points
	// -------------------------

	public static void setSheetUrl(ParkourMap map, String url) {
		DataStoreRepository.ensureLoaded();
		if (map == null) return;

		map.setSheetUrl(url != null ? url.trim() : "");
		map.setSheetGid(-1);
		DataStoreRepository.save();
	}

	public static void stopSync(ParkourMap map) {
		DataStoreRepository.ensureLoaded();
		if (map == null) return;

		map.setSheetUrl("");
		map.setSheetGid(-1);
		map.setLastSheetSyncMs(0L);
		map.setSheetFilter(null);
		DataStoreRepository.save();
	}

	/** Downloads the sheet of the map and brings the map in line with it. */
	public static void sync(final ServerProfile server, final ParkourMap map) {
		DataStoreRepository.ensureLoaded();
		if (map == null || !map.hasSheetConfigured()) {
			ChatUtils.error("No sheet is linked to this map.");
			return;
		}

		ChatUtils.info("Syncing the sheet...");
		SheetSyncManager.fetchAsync(map.getSheetUrl(), map.getSheetGid(), new SheetSyncManager.Callback() {
			@Override
			public void onSuccess(final SheetData data) {
				onMainThread(new Runnable() {
					@Override
					public void run() {
						// The user may have unlinked the sheet while it was downloading.
						if (!map.hasSheetConfigured()) return;
						report(apply(map, data, map.getSheetFilter(), true));
					}
				});
			}

			@Override
			public void onError(final String message) {
				onMainThread(new Runnable() {
					@Override
					public void run() {
						ChatUtils.error(message);
					}
				});
			}
		});
	}

	/** @return why a new map cannot get this name, or null if it can */
	public static String checkNewMapName(ServerProfile server, String mapId) {
		DataStoreRepository.ensureLoaded();
		if (server == null) return "No server selected.";
		if (!IdRules.isValidId(mapId)) return "The map name must be 1 to " + MAX_NAME_LENGTH + " characters.";
		if (MapService.findMapById(server, mapId.trim()) != null) {
			return "A map named \"" + mapId.trim() + "\" already exists on this server.";
		}
		return null;
	}

	/** Creates a map on the server from the downloaded sheet, taking only what the filter lets through. */
	public static void createMapFromSheet(ServerProfile server, String mapId, String url, SheetData data, SheetFilter filter) {
		String problem = checkNewMapName(server, mapId);
		if (problem != null) {
			ChatUtils.error(problem);
			return;
		}

		String id = mapId.trim();
		if (!MapService.createMap(server, id)) {
			ChatUtils.error("Could not create the map \"" + id + "\".");
			return;
		}

		ParkourMap map = MapService.findMapById(server, id);
		if (map == null) return;

		map.setSheetUrl(url.trim());
		map.setSheetFilter(filter);
		report(apply(map, data, filter, true));
	}

	/** Links the sheet (when {@code url} is given), remembers the filter and brings the map in line with the sheet. */
	public static void applySheet(ParkourMap map, String url, SheetData data, SheetFilter filter) {
		DataStoreRepository.ensureLoaded();
		if (map == null) return;

		if (url != null) {
			map.setSheetUrl(url.trim());
			map.setSheetGid(-1);
		}
		map.setSheetFilter(filter);
		report(apply(map, data, filter, true));
	}

	/** The whole sheet as a throw-away map (nothing is saved), for the review screen. Jump {@code i} is block {@code i}. */
	public static ParkourMap preview(SheetData data) {
		ParkourMap map = new ParkourMap();
		map.setId("sheet");
		apply(map, data, null, false);
		return map;
	}

	private static void onMainThread(final Runnable task) {
		Minecraft.getMinecraft().addScheduledTask(new Runnable() {
			@Override
			public void run() {
				try {
					task.run();
				} catch (RuntimeException ex) {
					ChatUtils.error("Sheet sync failed: " + ex);
				}
			}
		});
	}

	// -------------------------
	// Result
	// -------------------------

	public static final class Result {
		public int jumps;
		public int newJumps;
		public int strategiesAdded;
		public int strategiesUpdated;
		public int strategiesRemoved;
		public int unplaced;
		public int missing;
		public int sectionLevels;
		public boolean colors;
		public String warning;
		public int leftOut;
	}

	private static void report(Result r) {
		if (r == null) return;

		StringBuilder msg = new StringBuilder("Sheet synced: ").append(r.jumps).append(r.jumps == 1 ? " jump" : " jumps");
		if (r.newJumps > 0) msg.append(" (").append(r.newJumps).append(" new)");
		msg.append(". Strategies: ").append(r.strategiesAdded).append(" added, ")
				.append(r.strategiesUpdated).append(" updated, ")
				.append(r.strategiesRemoved).append(" removed.");
		ChatUtils.info(msg.toString());

		if (r.leftOut > 0) {
			ChatUtils.info(r.leftOut + " jumps were left out by the import filter.");
		}
		if (r.unplaced > 0) {
			ChatUtils.info(r.unplaced + " jumps still need a position.");
		}
		if (r.missing > 0) {
			ChatUtils.warning(r.missing + " jumps are no longer in the sheet (marked, not deleted).");
		}
		if (r.warning != null) {
			ChatUtils.warning(r.warning);
		}
		if (r.sectionLevels > 0 && !r.colors) {
			ChatUtils.warning("Section colors were picked automatically.");
		}
	}

	// -------------------------
	// Applying a sheet
	// -------------------------

	/** One jump of the sheet: its first row starts it, rows without a jump name belong to it. */
	private static final class Block {
		final List<Cell[]> rows = new ArrayList<Cell[]>();
		String name;
		String[] labels;
		int[] backgrounds;
		String key;
		Jump jump;
	}

	static Result apply(ParkourMap map, SheetData data, SheetFilter filter, boolean persist) {
		Result result = new Result();
		result.colors = data.hasColors;
		result.warning = data.warning;

		int[] sectionCols = sectionColumns(data);
		int levels = sectionCols.length;

		// Labels and keys are worked out on the whole sheet (section labels carry down from block to block),
		// and only then the filter takes its part.
		List<Block> all = buildBlocks(data);
		assignSectionLabels(all, data, sectionCols);
		assignKeys(all, levels);

		Set<String> filteredOut = new HashSet<String>();
		List<Block> blocks = select(all, filter, filteredOut, result);
		result.leftOut = filteredOut.size();

		// Existing jumps by their sheet key
		ArrayList<Jump> oldJumps = map.getJumps() != null ? map.getJumps() : new ArrayList<Jump>();
		Map<String, Jump> byKey = new HashMap<String, Jump>();
		for (Jump j : oldJumps) {
			if (j != null && j.hasSheetRowKey() && !byKey.containsKey(j.getSheetRowKey())) {
				byKey.put(j.getSheetRowKey(), j);
			}
		}

		// Jumps and their strategies
		Set<Jump> fromSheet = new LinkedHashSet<Jump>();
		for (Block block : blocks) {
			Jump jump = byKey.get(block.key);
			if (jump == null) {
				jump = newJump(block.name);
				jump.setSheetRowKey(block.key);
				result.newJumps++;
			}
			jump.setSheetMissing(false);
			block.jump = jump;
			fromSheet.add(jump);

			applyStrategies(jump, block, data, result);

			if (jump.getX() == 0 && jump.getY() == 0 && jump.getZ() == 0) {
				result.unplaced++;
			}
		}
		result.jumps = fromSheet.size();

		// Sheet jumps first in sheet order, then everything the sheet did not mention.
		ArrayList<Jump> ordered = new ArrayList<Jump>(fromSheet);
		for (Jump j : oldJumps) {
			if (j == null || fromSheet.contains(j)) continue;
			if (j.hasSheetRowKey()) {
				boolean unplaced = j.getX() == 0 && j.getY() == 0 && j.getZ() == 0;
				if (filteredOut.contains(j.getSheetRowKey()) && unplaced) {
					continue; // the player left it out and it holds no position: drop it
				}
				j.setSheetMissing(true);
				result.missing++;
			}
			ordered.add(j);
		}
		map.setJumps(ordered);

		if (levels > 0) {
			buildSections(map, data, blocks, sectionCols);
			result.sectionLevels = levels;
		}

		map.setLastSheetSyncMs(System.currentTimeMillis());
		if (persist) {
			DataStoreRepository.save();
		}
		return result;
	}

	/** Applies the first / last jump and the unchecked jumps of the filter. */
	private static List<Block> select(List<Block> all, SheetFilter filter, Set<String> filteredOut, Result result) {
		if (filter == null || filter.isEmpty()) return all;

		int from = indexOfKey(all, filter.getStartKey());
		int to = indexOfKey(all, filter.getEndKey());
		String note = null;

		if (!filter.getStartKey().isEmpty() && from < 0) note = "The first jump of the import filter is no longer in the sheet; starting at the top.";
		if (!filter.getEndKey().isEmpty() && to < 0) note = "The last jump of the import filter is no longer in the sheet; reading to the bottom.";
		if (from < 0) from = 0;
		if (to < 0) to = all.size() - 1;
		if (from > to) {
			note = "The import filter's first jump now comes after its last one; the range was ignored.";
			from = 0;
			to = all.size() - 1;
		}
		if (note != null) {
			result.warning = result.warning == null ? note : result.warning + " " + note;
		}

		Set<String> excluded = new HashSet<String>(filter.getExcludedKeys());
		List<Block> out = new ArrayList<Block>();
		for (int i = 0; i < all.size(); i++) {
			Block block = all.get(i);
			if (i < from || i > to || excluded.contains(block.key)) {
				filteredOut.add(block.key);
			} else {
				out.add(block);
			}
		}
		return out;
	}

	private static int indexOfKey(List<Block> blocks, String key) {
		if (key == null || key.isEmpty()) return -1;
		for (int i = 0; i < blocks.size(); i++) {
			if (key.equals(blocks.get(i).key)) return i;
		}
		return -1;
	}

	private static Jump newJump(String name) {
		Jump jump = new Jump();
		jump.setShowJumpNameInWorld(true);
		jump.setId(name);
		jump.setReminders(new ArrayList<Reminder>());
		jump.setActiveReminderIndex(-1);
		return jump;
	}

	/** Header columns left of "Jump" are the section levels, outermost first (at most five). */
	private static int[] sectionColumns(SheetData data) {
		List<Integer> cols = new ArrayList<Integer>();
		for (int c = 0; c < data.cols.jump && cols.size() < ParkourMap.MAX_SECTION_LEVELS; c++) {
			String title = c < data.header.size() ? data.header.get(c) : "";
			if (title != null && !title.trim().isEmpty()) cols.add(Integer.valueOf(c));
		}

		int[] out = new int[cols.size()];
		for (int i = 0; i < out.length; i++) out[i] = cols.get(i).intValue();
		return out;
	}

	private static List<Block> buildBlocks(SheetData data) {
		List<Block> blocks = new ArrayList<Block>();
		Block current = null;

		for (Cell[] row : data.rows) {
			String name = SheetData.text(row, data.cols.jump);
			if (!name.isEmpty()) {
				current = new Block();
				current.name = name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) : name;
				blocks.add(current);
			}
			if (current != null) current.rows.add(row);
		}
		return blocks;
	}

	/**
	 * A section label is written once for the rows it covers (merged cells), so a block without its own label keeps the
	 * one before it. A new label or color on an outer level ends all inner levels.
	 */
	private static void assignSectionLabels(List<Block> blocks, SheetData data, int[] sectionCols) {
		int levels = sectionCols.length;
		String[] carryLabel = new String[levels];
		int[] carryBg = new int[levels];
		for (int i = 0; i < levels; i++) carryLabel[i] = "";

		for (Block block : blocks) {
			block.labels = new String[levels];
			block.backgrounds = new int[levels];

			boolean outerChanged = false;
			for (int i = 0; i < levels; i++) {
				if (outerChanged) {
					carryLabel[i] = "";
					carryBg[i] = 0;
				}

				Cell explicit = null;
				for (Cell[] row : block.rows) {
					Cell cell = SheetData.cell(row, sectionCols[i]);
					if (!cell.v.isEmpty()) {
						explicit = cell;
						break;
					}
				}

				if (explicit != null) {
					boolean changed = !CsvUtils.normalizeKey(explicit.v).equals(CsvUtils.normalizeKey(carryLabel[i]))
							|| (data.hasColors && rgb(explicit.bg) != rgb(carryBg[i]));
					carryLabel[i] = explicit.v;
					carryBg[i] = explicit.bg;
					if (changed) outerChanged = true;
				}

				block.labels[i] = carryLabel[i];
				block.backgrounds[i] = carryBg[i];
			}
		}
	}

	/** Identity of each block: jump name + section path + how many identical ones came before. */
	private static void assignKeys(List<Block> blocks, int levels) {
		Map<String, Integer> seen = new HashMap<String, Integer>();
		for (Block block : blocks) {
			String base = CsvUtils.normalizeKey(block.name) + pathKey(block.labels, levels - 1);
			Integer before = seen.get(base);
			int occurrence = before != null ? before.intValue() + 1 : 1;
			seen.put(base, Integer.valueOf(occurrence));
			block.key = base + "#" + occurrence;
		}
	}

	private static String pathKey(String[] labels, int uptoLevel) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i <= uptoLevel && i < labels.length; i++) {
			if (labels[i].isEmpty()) continue;
			sb.append("|L").append(i + 1).append("=").append(CsvUtils.normalizeKey(labels[i]));
		}
		return sb.toString();
	}

	private static int rgb(int argb) {
		return argb & 0xFFFFFF;
	}

	// -------------------------
	// Strategies
	// -------------------------

	private static void applyStrategies(Jump jump, Block block, SheetData data, Result result) {
		if (jump.getReminders() == null) jump.setReminders(new ArrayList<Reminder>());
		ArrayList<Reminder> reminders = jump.getReminders();

		Set<String> current = new LinkedHashSet<String>();
		for (int r = 0; r < block.rows.size(); r++) {
			Cell[] row = block.rows.get(r);
			ArrayList<String> lines = new ArrayList<String>(8);
			lines.add(SheetData.text(row, data.cols.position));
			lines.add(SheetData.text(row, data.cols.facing));
			lines.add(SheetData.text(row, data.cols.setup));
			lines.add(SheetData.text(row, data.cols.strategy));
			lines.add(SheetData.text(row, data.cols.strafe));
			lines.add(SheetData.text(row, data.cols.turn));
			lines.add(SheetData.text(row, data.cols.author));
			lines.add(SheetData.text(row, data.cols.tips));

			boolean any = false;
			for (String line : lines) {
				if (!line.isEmpty()) any = true;
			}
			if (!any) continue;

			String source = block.key + "/" + r;
			current.add(source);

			Reminder existing = null;
			for (Reminder reminder : reminders) {
				if (reminder != null && source.equals(reminder.sheetSource)) {
					existing = reminder;
					break;
				}
			}

			if (existing == null) {
				Reminder added = new Reminder(lines);
				added.sheetSource = source;
				reminders.add(added);
				result.strategiesAdded++;
			} else if (!lines.equals(existing.lines)) {
				existing.lines = new ArrayList<String>(lines);
				result.strategiesUpdated++;
			}
		}

		// Strategies that came from the sheet and are gone from it
		for (int i = reminders.size() - 1; i >= 0; i--) {
			Reminder reminder = reminders.get(i);
			if (reminder != null && reminder.sheetSource != null && !current.contains(reminder.sheetSource)) {
				reminders.remove(i);
				result.strategiesRemoved++;
			}
		}

		if (reminders.isEmpty()) {
			jump.setActiveReminderIndex(-1);
		} else if (jump.getActiveReminderIndex() < 0 || jump.getActiveReminderIndex() >= reminders.size()) {
			jump.setActiveReminderIndex(0);
		}
	}

	// -------------------------
	// Sections
	// -------------------------

	private static void buildSections(ParkourMap map, SheetData data, List<Block> blocks, int[] sectionCols) {
		int levels = sectionCols.length;

		// Section names and count follow the sheet's header.
		String[] names = new String[ParkourMap.MAX_SECTION_LEVELS];
		for (int i = 0; i < ParkourMap.MAX_SECTION_LEVELS; i++) {
			names[i] = i < levels ? data.header.get(sectionCols[i]).trim() : "";
		}
		map.setSectionsEnabled(true);
		map.setSectionsCountRaw(levels);
		map.setSectionNames(names);

		for (int level = 1; level <= ParkourMap.MAX_SECTION_LEVELS; level++) {
			ArrayList<MapSection> target = map.getSectionsForLevel(level);

			// Keep the ids of sections that are still there.
			Map<String, List<String>> oldIds = new HashMap<String, List<String>>();
			for (MapSection old : target) {
				String k = CsvUtils.normalizeKey(old.getName());
				List<String> ids = oldIds.get(k);
				if (ids == null) {
					ids = new ArrayList<String>();
					oldIds.put(k, ids);
				}
				ids.add(old.getId());
			}
			target.clear();

			if (level > levels) continue;
			int i = level - 1;

			String activePath = null;
			int activeBg = 0;
			int start = -1;
			String activeName = "";

			for (int b = 0; b <= blocks.size(); b++) {
				String path = null;
				int bg = 0;
				String label = "";
				if (b < blocks.size()) {
					Block block = blocks.get(b);
					label = block.labels[i];
					bg = block.backgrounds[i];
					if (!label.isEmpty()) path = pathKey(block.labels, i);
				}

				boolean continues = path != null && path.equals(activePath)
						&& (!data.hasColors || rgb(bg) == rgb(activeBg));
				if (continues) continue;

				if (activePath != null) {
					target.add(makeSection(activeName, activeBg, data.hasColors, start, b - 1, oldIds));
				}

				activePath = path;
				activeBg = bg;
				activeName = label;
				start = b;
			}
		}
	}

	private static MapSection makeSection(String name, int bg, boolean hasColors, int start, int end,
										   Map<String, List<String>> oldIds) {
		MapSection section = new MapSection();

		List<String> ids = oldIds.get(CsvUtils.normalizeKey(name));
		if (ids != null && !ids.isEmpty()) {
			section.setId(ids.remove(0));
		} else {
			section.setId("sec_" + System.currentTimeMillis() + "_" + (int) (Math.random() * 1000000.0));
		}

		section.setName(name);
		section.setColorArgb(hasColors && bg != 0 ? bg : fallbackColor(name));
		section.setTextColorArgb(textColorFor(section.getColorArgb()));
		section.setStartJumpIndex(start);
		section.setEndJumpIndex(end);
		return section;
	}

	private static int fallbackColor(String name) {
		int h = CsvUtils.normalizeKey(name).hashCode();
		return FALLBACK_COLORS[(h & 0x7FFFFFFF) % FALLBACK_COLORS.length];
	}

	private static int textColorFor(int argb) {
		int r = (argb >> 16) & 0xFF;
		int g = (argb >> 8) & 0xFF;
		int b = argb & 0xFF;
		int luma = (int) (0.2126 * r + 0.7152 * g + 0.0722 * b);
		return luma >= 140 ? 0xFF000000 : 0xFFFFFFFF;
	}
}
