package me.texyle.startreminders.sheets;

import java.util.ArrayList;
import java.util.List;

public final class SheetJumpNameExtractor {

    private SheetJumpNameExtractor() {}

    public static final class PlaceholderSection {
        public String name = "";
        public int startJumpIndex = 0; // inclusive, 0-based in jumpNames
        public int endJumpIndex = 0;   // inclusive
    }

    public static final class Result {
        public final ArrayList<String> jumpNames = new ArrayList<String>();
        public final ArrayList<PlaceholderSection> sectionsL1 = new ArrayList<PlaceholderSection>();
        public int headerRowIndex = -1;
    }

    /**
     * Extracts jump names in the same order as they appear in the sheet (jump block order).
     * Rule: a new "jump block" starts when the Jump column cell is non-empty on a row.
     * Carry-down rows do NOT create a new entry.
     *
     * IMPORTANT: Duplicates are allowed and preserved (common in templates).
     */
    public static Result extract(String csvText) {
        Result res = new Result();

        List<List<String>> rows = CsvUtils.parseCsv(csvText);
        if (rows == null || rows.size() < 2) {
            return res;
        }

        int headerRowIndex = SheetHeaderLookup.findHeaderRowIndex(rows);
        res.headerRowIndex = headerRowIndex;

        if (headerRowIndex < 0 || headerRowIndex >= rows.size()) {
            return res;
        }

        List<String> header = rows.get(headerRowIndex);
        SheetHeaderMatcher.Columns cols = SheetHeaderMatcher.match(header);

        // Fallback: try to find "Jump" column manually if matcher failed
        if (cols == null || cols.jump < 0) {
            cols = (cols != null) ? cols : new SheetHeaderMatcher.Columns();
            cols.jump = SheetHeaderLookup.findHeaderCol(header, "jump", "jumpname", "jump id", "name");
        }

        if (cols.jump < 0) {
            return res;
        }

        int dataStart = headerRowIndex + 1;
        if (dataStart >= rows.size()) {
            return res;
        }

        // Level 1 section is always column A (index 0)
        final int COL_SECTION_L1 = 0;

        String lastSectionName = "";
        PlaceholderSection currentSection = null;
        String currentSectionKey = "";

        for (int r = dataStart; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            if (row == null) continue;

            // Carry-down section name for merged cells
            String secRaw = CsvUtils.getCell(row, COL_SECTION_L1);
            String secName = (secRaw != null) ? secRaw.trim() : "";
            if (secName.isEmpty()) secName = lastSectionName;
            else lastSectionName = secName;

            // New jump block starts when Jump column cell is non-empty
            String raw = CsvUtils.getCell(row, cols.jump);
            String jumpName = raw != null ? raw.trim() : "";

            if (jumpName.isEmpty()) {
                continue;
            }

            int jumpIndex = res.jumpNames.size();
            res.jumpNames.add(jumpName);

            // Group sections by normalized section name (sheet-driven)
            String secKey = CsvUtils.normalizeKey(secName);
            if (secKey.isEmpty()) secKey = "__no_section__";

            if (currentSection == null || !secKey.equals(currentSectionKey)) {
                // Close previous section
                if (currentSection != null) {
                    currentSection.endJumpIndex = jumpIndex - 1;
                }

                currentSectionKey = secKey;

                currentSection = new PlaceholderSection();
                currentSection.name = (secName != null && secName.trim().length() > 0) ? secName.trim() : "(no section)";
                currentSection.startJumpIndex = jumpIndex;
                currentSection.endJumpIndex = jumpIndex;

                res.sectionsL1.add(currentSection);
            } else {
                currentSection.endJumpIndex = jumpIndex;
            }
        }

        if (currentSection != null) {
            currentSection.endJumpIndex = res.jumpNames.size() - 1;
        }

        return res;
    }

}