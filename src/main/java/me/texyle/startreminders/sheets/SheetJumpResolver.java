package me.texyle.startreminders.sheets;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import me.texyle.startreminders.data.Jump;
import me.texyle.startreminders.data.MapSection;
import me.texyle.startreminders.data.ParkourMap;
import me.texyle.startreminders.services.SectionLayoutService;
import me.texyle.startreminders.sheets.CsvUtils;

/** Maps rows of a sheet CSV to jumps of the map, taking section structure into account. */
public final class SheetJumpResolver {

	private SheetJumpResolver() {}

	static class ResolvedJump {
		final Jump jump;
		final boolean allowBind; // true = safe to persist sheetRowKey, false = "approx/guess" mapping

		ResolvedJump(Jump jump, boolean allowBind) {
			this.jump = jump;
			this.allowBind = allowBind;
		}
	}

	static final class SheetJumpBlock {
		String jumpName;       // carried down
		String sectionKey;     // normalized section path key
		String sheetRowKey;    // section-aware persistent identity: (jump+sectionPath)+#occurrence
		final ArrayList<List<String>> rows = new ArrayList<List<String>>();
	}

	public static ArrayList<SheetJumpBlock> buildSheetJumpBlocks(
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

	static final class TemplateIndexContext {
		// sectionKey -> normJumpName -> list of Jump in template order
		final java.util.HashMap<String, java.util.HashMap<String, java.util.ArrayList<Jump>>> bySection = new java.util.HashMap<String, java.util.HashMap<String, java.util.ArrayList<Jump>>>();

		// For fast persisted binding lookup
		final java.util.HashMap<String, Jump> bySheetRowKey = new java.util.HashMap<String, Jump>();
	}

	public static TemplateIndexContext buildTemplateIndexContextBySections(ParkourMap map) {
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
		if (max > ParkourMap.MAX_SECTION_LEVELS) max = ParkourMap.MAX_SECTION_LEVELS;

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

		MapSection s = SectionLayoutService.findSectionContainingIndex(secs, jumpIndex);
		if (s == null) return "";

		String n = s.getName();
		return (n != null) ? n.trim() : "";
	}

	public static ResolvedJump resolveJumpSectionAware(
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

}
