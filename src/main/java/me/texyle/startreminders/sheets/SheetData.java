package me.texyle.startreminders.sheets;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** A downloaded strategy sheet: the header, its column mapping and the data rows (with cell colors if known). */
public final class SheetData {

	/** One cell: its text and its background color (ARGB), or 0 when the colors are not known. */
	static final class Cell {
		static final Cell EMPTY = new Cell("", 0);

		final String v;
		final int bg;

		Cell(String v, int bg) {
			this.v = v != null ? v.trim() : "";
			this.bg = bg;
		}
	}

	final List<String> header;
	final SheetHeaderMatcher.Columns cols;
	final List<Cell[]> rows;

	/** True when the section cells and their colors come from the sheet script; false for plain CSV. */
	boolean hasColors;

	/** Something worth telling the player that did not stop the import (e.g. colors unavailable), or null. */
	String warning;

	/** Why the sheet cannot be used, or null if it is valid. */
	final String error;

	private SheetData(List<String> header, SheetHeaderMatcher.Columns cols, List<Cell[]> rows, boolean hasColors, String error) {
		this.header = header;
		this.cols = cols;
		this.rows = rows;
		this.hasColors = hasColors;
		this.error = error;
	}

	private static SheetData invalid(String error) {
		return new SheetData(null, null, null, false, error);
	}

	// -------------------------
	// Sources
	// -------------------------

	/** What the Apps Script knows about a sheet: the header and the cells (text + color) of the rows it returns. */
	static final class ScriptRows {
		final List<String> header = new ArrayList<String>();
		final List<Cell[]> rows = new ArrayList<Cell[]>();
	}

	/**
	 * Parses the Apps Script answer {"ok":true,"header":[...],"rows":[[{"v":"text","bg":"FFRRGGBB"},...],...]}.
	 * Merged cells are already expanded there, which the plain CSV does not do. The script may return only some
	 * of the rows (for one sheet: only the first row of every jump), so it is used for the section columns only.
	 *
	 * @return null if the answer is not usable
	 */
	static ScriptRows parseScript(String json) {
		JsonObject root;
		try {
			JsonElement parsed = new JsonParser().parse(json);
			if (!parsed.isJsonObject()) return null;
			root = parsed.getAsJsonObject();
		} catch (RuntimeException ex) {
			return null;
		}

		if (!root.has("ok") || !root.get("ok").isJsonPrimitive() || !root.get("ok").getAsBoolean()) return null;
		if (!root.has("rows") || !root.get("rows").isJsonArray()) return null;

		ScriptRows out = new ScriptRows();
		if (root.has("header") && root.get("header").isJsonArray()) {
			for (JsonElement el : root.getAsJsonArray("header")) out.header.add(text(el));
		}

		for (JsonElement rowEl : root.getAsJsonArray("rows")) {
			List<Cell> row = new ArrayList<Cell>();
			if (rowEl.isJsonArray()) {
				for (JsonElement cellEl : rowEl.getAsJsonArray()) {
					if (cellEl.isJsonObject()) {
						JsonObject cell = cellEl.getAsJsonObject();
						row.add(new Cell(text(cell.get("v")), parseArgbHex(text(cell.get("bg")))));
					} else {
						row.add(new Cell(text(cellEl), 0));
					}
				}
			}
			out.rows.add(row.toArray(new Cell[0]));
		}
		return out;
	}

	/**
	 * Takes the section columns (left of "Jump") from the script rows, so that merged section cells are filled in on
	 * every jump and the section colors are known. The script rows are matched to the sheet rows by order, and only
	 * when the jump names agree; otherwise the sheet is left as it is.
	 *
	 * @return why the script rows were not used, or null if they were
	 */
	String mergeSections(ScriptRows script) {
		int jumpCol = cols.jump;
		if (jumpCol <= 0) return null; // no section columns

		List<Integer> starts = new ArrayList<Integer>();
		for (int r = 0; r < rows.size(); r++) {
			if (!text(rows.get(r), jumpCol).isEmpty()) starts.add(Integer.valueOf(r));
		}

		// The script either gives every row, or one row per jump.
		List<Integer> targets;
		if (script.rows.size() == rows.size()) {
			targets = new ArrayList<Integer>();
			for (int r = 0; r < rows.size(); r++) targets.add(Integer.valueOf(r));
		} else if (script.rows.size() == starts.size()) {
			targets = starts;
		} else {
			return "The sheet script returned " + script.rows.size() + " rows for " + starts.size()
					+ " jumps; section colors were not used.";
		}

		for (int k = 0; k < targets.size(); k++) {
			String mine = text(rows.get(targets.get(k).intValue()), jumpCol);
			String theirs = text(script.rows.get(k), jumpCol);
			if (!mine.isEmpty() && !CsvUtils.normalizeKey(mine).equals(CsvUtils.normalizeKey(theirs))) {
				return "The sheet script lists the jumps in a different order; section colors were not used.";
			}
		}

		for (int k = 0; k < targets.size(); k++) {
			int r = targets.get(k).intValue();
			Cell[] row = rows.get(r);
			if (row.length < jumpCol) {
				Cell[] wider = new Cell[jumpCol];
				for (int c = 0; c < wider.length; c++) wider[c] = c < row.length ? row[c] : Cell.EMPTY;
				row = wider;
				rows.set(r, row);
			}
			Cell[] source = script.rows.get(k);
			for (int c = 0; c < jumpCol; c++) row[c] = cell(source, c);
		}

		hasColors = true;
		return null;
	}

	static SheetData fromCsv(String csvText) {
		List<List<String>> csv = CsvUtils.parseCsv(csvText);
		List<List<Cell>> grid = new ArrayList<List<Cell>>(csv.size());
		for (List<String> csvRow : csv) {
			List<Cell> row = new ArrayList<Cell>(csvRow.size());
			for (String v : csvRow) row.add(new Cell(v, 0));
			grid.add(row);
		}
		return fromGrid(grid, false);
	}

	private static SheetData fromGrid(List<List<Cell>> grid, boolean hasColors) {
		if (grid.size() < 2) {
			return invalid("The sheet has no data rows.");
		}

		List<List<String>> values = new ArrayList<List<String>>(grid.size());
		for (List<Cell> row : grid) {
			List<String> texts = new ArrayList<String>(row.size());
			for (Cell cell : row) texts.add(cell.v);
			values.add(texts);
		}

		int headerRow = SheetHeaderLookup.findHeaderRowIndex(values);
		if (headerRow < 0) {
			return invalid("Header row not found (expected columns like 'Jump' and 'Strategy').");
		}

		List<String> header = values.get(headerRow);
		SheetHeaderMatcher.Columns cols = SheetHeaderMatcher.match(header);
		SheetHeaderLookup.applyHeaderFallbacks(header, cols);
		if (cols.jump < 0) {
			return invalid("The sheet has no 'Jump' column.");
		}

		List<Cell[]> rows = new ArrayList<Cell[]>();
		for (int r = headerRow + 1; r < grid.size(); r++) {
			List<Cell> row = grid.get(r);
			if (values.get(r).equals(header)) continue; // the script may repeat the header among the rows
			if (isBlank(row)) continue;
			rows.add(row.toArray(new Cell[0]));
		}

		if (rows.isEmpty()) {
			return invalid("The sheet has a header but no data rows.");
		}

		return new SheetData(header, cols, rows, hasColors, null);
	}

	private static boolean isBlank(List<Cell> row) {
		for (Cell cell : row) {
			if (!cell.v.isEmpty()) return false;
		}
		return true;
	}

	// -------------------------
	// Access
	// -------------------------

	static Cell cell(Cell[] row, int col) {
		if (col < 0 || col >= row.length) return Cell.EMPTY;
		return row[col];
	}

	static String text(Cell[] row, int col) {
		return cell(row, col).v;
	}

	private static String text(JsonElement el) {
		if (el == null || el.isJsonNull() || !el.isJsonPrimitive()) return "";
		String s = el.getAsString();
		return s != null ? s.trim() : "";
	}

	private static int parseArgbHex(String hex) {
		try {
			String h = hex.startsWith("#") ? hex.substring(1) : hex;
			if (h.length() == 8) return (int) Long.parseLong(h, 16);
			if (h.length() == 6) return (int) Long.parseLong("FF" + h, 16);
		} catch (NumberFormatException ignored) {
			// no usable color
		}
		return 0;
	}
}
