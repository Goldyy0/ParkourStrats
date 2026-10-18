package me.texyle.startreminders.sheets;

import java.util.ArrayList;
import java.util.List;

public final class CsvUtils {

    private CsvUtils() {}

    public static List<List<String>> parseCsv(String csvText) {
        ArrayList<List<String>> rows = new ArrayList<List<String>>();
        if (csvText == null) return rows;

        ArrayList<String> row = new ArrayList<String>();
        StringBuilder cell = new StringBuilder();

        boolean inQuotes = false;

        for (int i = 0; i < csvText.length(); i++) {
            char c = csvText.charAt(i);

            if (inQuotes) {
                if (c == '"') {
                    // Escaped quote?
                    if (i + 1 < csvText.length() && csvText.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cell.append(c);
                }
                continue;
            }

            if (c == '"') {
                inQuotes = true;
                continue;
            }

            if (c == ',') {
                row.add(cell.toString());
                cell.setLength(0);
                continue;
            }

            if (c == '\r') {
                // Handle CRLF or CR
                if (i + 1 < csvText.length() && csvText.charAt(i + 1) == '\n') {
                    i++;
                }
                row.add(cell.toString());
                cell.setLength(0);
                rows.add(row);
                row = new ArrayList<String>();
                continue;
            }

            if (c == '\n') {
                row.add(cell.toString());
                cell.setLength(0);
                rows.add(row);
                row = new ArrayList<String>();
                continue;
            }

            cell.append(c);
        }

        // Last cell/row
        row.add(cell.toString());
        rows.add(row);

        return rows;
    }

    public static String getCell(List<String> row, int idx) {
        if (row == null || idx < 0 || idx >= row.size()) return "";
        String v = row.get(idx);
        return v != null ? v : "";
    }

    // -------------------------
    // Fuzzy jump name matching helpers
    // -------------------------

    // Normalizes keys for fuzzy-ish matching: ignores case, spaces, underscores, hyphens
    public static String normalizeKey(String s) {
        if (s == null) return "";
        String t = s.trim().toLowerCase();
        t = t.replace(" ", "");
        t = t.replace("\t", "");
        t = t.replace("_", "");
        t = t.replace("-", "");
        return t;
    }
}