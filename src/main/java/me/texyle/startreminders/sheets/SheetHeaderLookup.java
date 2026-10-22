package me.texyle.startreminders.sheets;

import java.util.List;

import me.texyle.startreminders.data.Jump;
import me.texyle.startreminders.sheets.CsvUtils;
import me.texyle.startreminders.sheets.SheetHeaderMatcher;

/** Locates the header row and columns of a sheet CSV, tolerating naming variations. */
public final class SheetHeaderLookup {

	private SheetHeaderLookup() {}

	/**
	 * Fixes header matching issues by applying strict, index-based fallback lookups.
	 * This prevents cases where "by" is not recognized as Author and ends up in Tips.
	 */
	public static void applyHeaderFallbacks(List<String> header, SheetHeaderMatcher.Columns cols) {
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

		// Headers with a remark in brackets, e.g. "Position (Red=SP)".
		if (cols.position < 0) cols.position = findHeaderIndexIgnoringBrackets(header, "position", "pos");
		if (cols.facing < 0) cols.facing = findHeaderIndexIgnoringBrackets(header, "facing", "face");
		if (cols.setup < 0) cols.setup = findHeaderIndexIgnoringBrackets(header, "setup");
		if (cols.strategy < 0) cols.strategy = findHeaderIndexIgnoringBrackets(header, "strategy", "strat");
		if (cols.strafe < 0) cols.strafe = findHeaderIndexIgnoringBrackets(header, "strafe");
		if (cols.turn < 0) cols.turn = findHeaderIndexIgnoringBrackets(header, "turn");
		if (cols.author < 0) cols.author = findHeaderIndexIgnoringBrackets(header, "by", "author", "creator");
		if (cols.tips < 0) cols.tips = findHeaderIndexIgnoringBrackets(header, "tips", "notes", "note");
	}

	private static int findHeaderIndexIgnoringBrackets(List<String> header, String... candidates) {
		if (header == null || candidates == null) return -1;

		for (int i = 0; i < header.size(); i++) {
			String h = header.get(i);
			if (h == null) continue;
			String stripped = normalizeHeader(h.replaceAll("\\(.*?\\)|\\[.*?\\]", " "));
			if (stripped.isEmpty()) continue;

			for (String c : candidates) {
				if (c != null && stripped.equals(normalizeHeader(c))) return i;
			}
		}
		return -1;
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

	public static int findHeaderRowIndex(List<List<String>> rows) {
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

	// Header lookup with normalization (supports "Sub-section" etc.)
	public static int findHeaderCol(List<String> header, String... names) {
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

	public static String getByCol(List<String> row, int col) {
		if (col < 0) return "";
		return CsvUtils.getCell(row, col).trim();
	}

}
