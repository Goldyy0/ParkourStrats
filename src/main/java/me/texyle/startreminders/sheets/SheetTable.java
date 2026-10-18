package me.texyle.startreminders.sheets;

import java.util.List;

/** A parsed strategy sheet: rows, the located header and its column mapping. */
final class SheetTable {

	final List<List<String>> rows;
	final List<String> header;
	final SheetHeaderMatcher.Columns cols;
	final int dataStartRow;

	/** Why the sheet cannot be used, or null if it is valid. */
	final String error;

	private SheetTable(List<List<String>> rows, List<String> header, SheetHeaderMatcher.Columns cols, int dataStartRow, String error) {
		this.rows = rows;
		this.header = header;
		this.cols = cols;
		this.dataStartRow = dataStartRow;
		this.error = error;
	}

	private static SheetTable invalid(String error) {
		return new SheetTable(null, null, null, -1, error);
	}

	static SheetTable parse(String csvText) {
		List<List<String>> rows = CsvUtils.parseCsv(csvText);
		if (rows == null || rows.size() < 2) {
			return invalid("CSV has no data rows.");
		}

		int headerRowIndex = SheetHeaderLookup.findHeaderRowIndex(rows);
		if (headerRowIndex < 0) {
			return invalid("CSV header row not found (expected columns like 'jump', 'strategy').");
		}

		int dataStartRow = headerRowIndex + 1;
		if (dataStartRow >= rows.size()) {
			return invalid("CSV has header but no data rows.");
		}

		List<String> header = rows.get(headerRowIndex);
		SheetHeaderMatcher.Columns cols = SheetHeaderMatcher.match(header);

		// Strengthen matching with strict, index-based fallbacks (supports "by" etc.)
		SheetHeaderLookup.applyHeaderFallbacks(header, cols);

		if (cols.jump < 0) {
			return invalid("CSV missing 'Jump' column.");
		}

		return new SheetTable(rows, header, cols, dataStartRow, null);
	}
}
