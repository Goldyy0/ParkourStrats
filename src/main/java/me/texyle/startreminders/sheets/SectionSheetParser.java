package me.texyle.startreminders.sheets;

import java.util.ArrayList;
import java.util.HashMap;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import me.texyle.startreminders.data.Jump;
import me.texyle.startreminders.data.MapSection;
import me.texyle.startreminders.data.ParkourMap;

/** Turns the Apps Script JSON of a sheet (cell values and background colors) into the sections of a map. */
public final class SectionSheetParser {

	private SectionSheetParser() {}

	static class JumpCellView {
		final String v;
		final int bg;

		JumpCellView(String v, int bg) {
			this.v = v != null ? v : "";
			this.bg = bg;
		}
	}

	public static boolean applySectionsFromJson(ParkourMap map, String json) {
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

				JumpCellView view = reduceBlockToJumpCell(rows, r0, r1, colIdx);
				jumpView.add(view);
			}

			buildSectionsFromJumpView(target, jumpView, usableJumps);
		}

		return true;
	}

	private static JumpCellView reduceBlockToJumpCell(JsonArray rows, int r0, int r1, int colIdx) {
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
				int bg = parseArgbHex(bgHex);
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
			int bg = parseArgbHex(bgHex);

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

	private static void buildSectionsFromJumpView(
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
					addSectionIfValid(out, activeName, activeColor, activeStart, j - 1, jumpsCount);

					activeName = v;
					activeColor = bg;
					activeStart = j;
				}
				continue;
			}

			// Empty text cell: keep section only if background matches
			if ((bg & 0xFFFFFF) != (activeColor & 0xFFFFFF)) {
				addSectionIfValid(out, activeName, activeColor, activeStart, j - 1, jumpsCount);

				activeName = null;
				activeColor = 0xFFFFFFFF;
				activeStart = -1;
			}
		}

		// Close trailing section
		if (activeName != null && activeStart >= 0) {
			addSectionIfValid(out, activeName, activeColor, activeStart, usable - 1, jumpsCount);
		}
	}

	private static void addSectionIfValid(
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
		s.setId(makeSectionId());
		s.setName(n);

		// Ensure opaque
		s.setColorArgb(colorArgb);

		// Auto text color (black/white)
		s.setTextColorArgb(pickTextColorForBg(s.getColorArgb()));

		s.setStartJumpIndex(start);
		s.setEndJumpIndex(end);

		out.add(s);
	}

	private static String makeSectionId() {
		// Simple unique id that is stable enough for JSON persistence
		return "sec_" + System.currentTimeMillis() + "_" + (int) (Math.random() * 1000000.0);
	}

	private static int parseArgbHex(String hex) {
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

	private static int pickTextColorForBg(int argb) {
		int r = (argb >> 16) & 0xFF;
		int g = (argb >> 8) & 0xFF;
		int b = (argb) & 0xFF;

		int y = (int) (0.2126 * r + 0.7152 * g + 0.0722 * b);

		return (y >= 140) ? 0xFF000000 : 0xFFFFFFFF;
	}

	public static String readErrorFromJson(String json) {
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

}
