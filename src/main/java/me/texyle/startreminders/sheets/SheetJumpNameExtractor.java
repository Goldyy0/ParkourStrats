package me.texyle.startreminders.sheets;

import java.util.ArrayList;
import java.util.List;

public final class SheetJumpNameExtractor {

    private SheetJumpNameExtractor() {}

    /**
     * Extracts jump names in the same order as they appear in the sheet (jump block order).
     * Rule: a new "jump block" starts when the Jump column cell is non-empty on a row.
     * Carry-down rows do NOT create a new entry.
     *
     * IMPORTANT: Duplicates are allowed and preserved (common in templates).
     */
    public static List<String> extractJumpNames(String csvText) {
        ArrayList<String> out = new ArrayList<String>();

        List<List<String>> rows = CsvUtils.parseCsv(csvText);
        if (rows == null || rows.size() < 2) {
            return out;
        }

        List<String> header = rows.get(0);
        SheetHeaderMatcher.Columns cols = SheetHeaderMatcher.match(header);

        // Fallback: try to find "Jump" column manually if matcher failed
        if (cols == null || cols.jump < 0) {
            cols = (cols != null) ? cols : new SheetHeaderMatcher.Columns();
            cols.jump = findHeaderCol(header, "jump", "jumpname", "jump id", "name");
        }

        if (cols.jump < 0) {
            return out;
        }

        for (int r = 1; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            if (row == null) continue;

            String raw = CsvUtils.getCell(row, cols.jump);
            String name = raw != null ? raw.trim() : "";

            // Only treat non-empty Jump cell as a new jump block
            if (name.isEmpty()) {
                continue;
            }

            // Preserve duplicates as-is
            out.add(name);
        }

        return out;
    }

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
}