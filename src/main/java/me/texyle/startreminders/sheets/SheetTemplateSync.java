package me.texyle.startreminders.sheets;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.client.Minecraft;

import me.texyle.startreminders.StratReminders;
import me.texyle.startreminders.data.Jump;
import me.texyle.startreminders.data.ParkourMap;
import me.texyle.startreminders.data.ServerProfile;
import me.texyle.startreminders.persistence.DataStoreRepository;
import me.texyle.startreminders.reminders.Reminder;
import me.texyle.startreminders.sheets.SheetHeaderMatcher;
import me.texyle.startreminders.sheets.SheetJumpResolver.ResolvedJump;
import me.texyle.startreminders.sheets.SheetJumpResolver.SheetJumpBlock;
import me.texyle.startreminders.sheets.SheetJumpResolver.TemplateIndexContext;
import me.texyle.startreminders.sheets.SheetSyncManager;
import me.texyle.startreminders.utils.ChatUtils;

/** Synchronises a map's jumps and strategies with a Google Sheet exported as CSV. */
public final class SheetTemplateSync {

	private SheetTemplateSync() {}

	// Tracks which (server,map) already announced the first successful sync in chat.
	private static final Set<String> sheetSyncAnnouncedKeys = new HashSet<String>();

	private static final String SHEETS_CACHE_DIR = "StratReminders/sheets_cache";

	public static void setSheetUrlForMap(ParkourMap map, String url) {
		DataStoreRepository.ensureLoaded();
		if (map == null) return;

		String trimmed = url != null ? url.trim() : "";
		map.setSheetUrl(trimmed);
		map.setSheetGid(-1); // gid is parsed during download, unless user provides URL with gid
		DataStoreRepository.save();
	}

	public static void stopSheetSyncForMap(ServerProfile server, ParkourMap map) {
		DataStoreRepository.ensureLoaded();
		if (map == null) return;

		map.setSheetUrl("");
		map.setSheetGid(-1);
		map.setLastSheetSyncMs(0L);

		// Reset "first sync announced" so that if user re-enables sync later,
		// the first successful auto-sync can announce again.
		sheetSyncAnnouncedKeys.remove(buildSheetAnnounceKey(server, map));

		DataStoreRepository.save();
	}

	private static final class ImportStats {
		int imported = 0;
		int skipped = 0;
	}

	private static void applyCsvToMap(ServerProfile server, ParkourMap map, String csvText, boolean announceInChat) {

		if (map == null || csvText == null) {
			return;
		}

		// If user stopped sync while the request was in-flight, do not apply.
		if (!map.hasSheetConfigured()) {
			return;
		}

		SheetTable table = SheetTable.parse(csvText);
		if (table.error != null) {
			if (announceInChat) {
				ChatUtils.error(table.error);
			}
			return;
		}

		int[] sectionCols = findSectionColumns(map, table.header);

		// 1) Build template index by (sectionPathKey -> (normJumpName -> list<Jump> in template order))
		TemplateIndexContext templateIndex = SheetJumpResolver.buildTemplateIndexContextBySections(map);

		// 2) Build sheet blocks (1 jump block -> N strategy rows) with carry-down for merged cells
		// IMPORTANT: start from dataStartRow, not from row 1
		ArrayList<SheetJumpBlock> blocks = SheetJumpResolver.buildSheetJumpBlocks(
				table.rows, table.dataStartRow, table.cols.jump, sectionCols, sectionCols.length);

		ImportStats stats = new ImportStats();

		// Used to avoid mapping the same template jump multiple times in a single sync run (duplicates)
		HashSet<Jump> assignedThisRun = new HashSet<Jump>();

		for (SheetJumpBlock block : blocks) {
			if (block == null) continue;
			importBlock(block, table.cols, templateIndex, assignedThisRun, stats);
		}

		map.setLastSheetSyncMs(System.currentTimeMillis());
		DataStoreRepository.save();

		if (announceInChat) {
			ChatUtils.info("Sheet sync completed. Imported: " + stats.imported + ", skipped: " + stats.skipped + ".");

			// Mark that we already announced the first sync.
			sheetSyncAnnouncedKeys.add(buildSheetAnnounceKey(server, map));
		}
	}

	/** Header column of each section level (1..max for this map); -1 where the sheet has no such column. */
	private static int[] findSectionColumns(ParkourMap map, List<String> header) {
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
			sectionCols[lvl - 1] = SheetHeaderLookup.findHeaderCol(header, colName);
		}
		return sectionCols;
	}

	/** Finds the template jump a sheet block belongs to and imports the block's strategy rows into it. */
	private static void importBlock(SheetJumpBlock block, SheetHeaderMatcher.Columns cols,
									TemplateIndexContext templateIndex, HashSet<Jump> assignedThisRun, ImportStats stats) {
		int blockRows = Math.max(1, block.rows.size());

		String jumpName = (block.jumpName != null) ? block.jumpName.trim() : "";
		if (jumpName.isEmpty()) {
			stats.skipped += blockRows;
			return;
		}

		// Resolve jump deterministically within same section path
		ResolvedJump resolved = SheetJumpResolver.resolveJumpSectionAware(
				jumpName,
				block.sectionKey,
				block.sheetRowKey,
				templateIndex,
				assignedThisRun
		);

		Jump target = (resolved != null) ? resolved.jump : null;

		if (target == null) {
			// Requirement: do not create new jumps without coords
			stats.skipped += blockRows;
			return;
		}

		// Bind only when we are confident.
		if (resolved.allowBind) {
			bindSheetRowKey(target, block.sheetRowKey);
		}

		// Mark assigned so duplicates map deterministically
		assignedThisRun.add(target);

		for (List<String> row : block.rows) {
			if (row == null) {
				stats.skipped++;
				continue;
			}

			if (importStrategyRow(target, row, cols)) {
				stats.imported++;
			} else {
				stats.skipped++;
			}
		}
	}

	/** Remembers the sheet row key on the jump once; an already bound jump is never rebound (prevents flips). */
	private static void bindSheetRowKey(Jump target, String incomingKeyRaw) {
		if (incomingKeyRaw == null || incomingKeyRaw.trim().isEmpty()) {
			return;
		}

		String existingKey = target.getSheetRowKey();
		if (existingKey == null || existingKey.trim().isEmpty()) {
			target.setSheetRowKey(incomingKeyRaw.trim());
		}
	}

	/** @return true if the row was imported (or refreshed an existing strategy), false if it was empty */
	private static boolean importStrategyRow(Jump target, List<String> row, SheetHeaderMatcher.Columns cols) {
		// Always map by column index (never by "non-empty count"), so empty Strafe/Turn do not shift Author/Tips.
		ArrayList<String> lines = new ArrayList<String>(8);
		lines.add(SheetHeaderLookup.getByCol(row, cols.position));
		lines.add(SheetHeaderLookup.getByCol(row, cols.facing));
		lines.add(SheetHeaderLookup.getByCol(row, cols.setup));
		lines.add(SheetHeaderLookup.getByCol(row, cols.strategy));
		lines.add(SheetHeaderLookup.getByCol(row, cols.strafe));
		lines.add(SheetHeaderLookup.getByCol(row, cols.turn));
		lines.add(SheetHeaderLookup.getByCol(row, cols.author));
		lines.add(SheetHeaderLookup.getByCol(row, cols.tips));

		lines = StrategyLines.ensureEightLines(lines);

		// Skip empty strategies (all fields empty)
		boolean anyText = false;
		for (String s : lines) {
			if (s != null && s.trim().length() > 0) {
				anyText = true;
				break;
			}
		}
		if (!anyText) {
			return false;
		}

		// Remove placeholders before first real import into that jump
		StrategyLines.removePlaceholderReminders(target);

		// Strategy identity is core 0..5. Author/Tips (6/7) are metadata that can change without creating a new strategy.
		Reminder existing = StrategyLines.findReminderByCore6(target, lines);
		if (existing != null) {
			StrategyLines.updateAuthorAndTipsIfChanged(existing, lines);
			return true;
		}

		if (target.getReminders() == null) {
			target.setReminders(new ArrayList<Reminder>());
		}

		target.getReminders().add(new Reminder(lines));
		if (target.getActiveReminderIndex() < 0) {
			target.setActiveReminderIndex(0);
		}

		return true;
	}

	public static void requestSheetSyncForMap(ServerProfile server, ParkourMap map, boolean manual) {
		DataStoreRepository.ensureLoaded();
		if (map == null || !map.hasSheetConfigured()) {
			ChatUtils.error("No sheet URL configured for this map.");
			return;
		}

		if (SheetSyncManager.isSyncInProgress()) {
			if (manual) {
				ChatUtils.warning("Sync already in progress.");
			}
			return;
		}

		final String cachePath = buildCachePath(server, map);

		final String announceKey = buildSheetAnnounceKey(server, map);
		final boolean announceInChat = manual || !sheetSyncAnnouncedKeys.contains(announceKey);

		if (announceInChat) {
			ChatUtils.info("Sync started...");
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

						ChatUtils.error(errorMessage);
					}
				});
			}
		});
	}

	private static String buildCachePath(ServerProfile server, ParkourMap map) {
		return SHEETS_CACHE_DIR + "/" + buildSheetAnnounceKey(server, map) + ".csv";
	}

	/** File-system safe "server__map" name identifying a map's sheet. */
	private static String buildSheetAnnounceKey(ServerProfile server, ParkourMap map) {
		String s = server != null ? safeFile(server.getId()) : "unknown_server";
		String m = map != null ? safeFile(map.getId()) : "unknown_map";
		return s + "__" + m;
	}

	private static String safeFile(String s) {
		if (s == null) return "null";
		return s.replaceAll("[^a-zA-Z0-9._-]+", "_");
	}

}
