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

        int headerRowIndex = findHeaderRowIndex(rows);
        res.headerRowIndex = headerRowIndex;

        if (headerRowIndex < 0 || headerRowIndex >= rows.size()) {
            return res;
        }

        List<String> header = rows.get(headerRowIndex);
        SheetHeaderMatcher.Columns cols = SheetHeaderMatcher.match(header);

        // Fallback: try to find "Jump" column manually if matcher failed
        if (cols == null || cols.jump < 0) {
            cols = (cols != null) ? cols : new SheetHeaderMatcher.Columns();
            cols.jump = findHeaderCol(header, "jump", "jumpname", "jump id", "name");
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

    /**
     * Backwards compatible helper: returns only jump names (old API).
     */
    public static List<String> extractJumpNames(String csvText) {
        Result r = extract(csvText);
        return r != null ? r.jumpNames : new ArrayList<String>();
    }

    private static int findHeaderRowIndex(List<List<String>> rows) {
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

        // Require minimal confidence
        return (bestScore >= 4) ? bestRow : -1;
    }

    private static int scoreHeaderRow(List<String> row) {
        int score = 0;

        for (int i = 0; i < row.size(); i++) {
            String cell = row.get(i);
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

                // Section-ish headers often contain "section"
            else if (k.contains("section")) score += 1;
        }

        return score;
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