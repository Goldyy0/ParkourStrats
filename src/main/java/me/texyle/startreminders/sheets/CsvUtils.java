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

    /**
     * Normalizes a jump name for fuzzy matching:
     * - lowercases
     * - trims
     * - removes quotes
     * - converts separators to spaces (underscore, dash, slash)
     * - removes most punctuation
     * - collapses whitespace
     */
    public static String normalizeJumpName(String s) {
        if (s == null) return "";
        String t = s.trim().toLowerCase();

        // Remove surrounding quotes (common in CSV exports)
        if (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"")) {
            t = t.substring(1, t.length() - 1).trim();
        }

        // Normalize separators to spaces
        t = t.replace('_', ' ');
        t = t.replace('-', ' ');
        t = t.replace('/', ' ');
        t = t.replace('\\', ' ');
        t = t.replace('|', ' ');

        // Remove bracket-like punctuation but keep their content
        t = t.replace("(", " ");
        t = t.replace(")", " ");
        t = t.replace("[", " ");
        t = t.replace("]", " ");
        t = t.replace("{", " ");
        t = t.replace("}", " ");

        // Remove most remaining punctuation/symbols except dots and digits/letters/spaces
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);

            // Keep letters/digits and spaces
            if (Character.isLetterOrDigit(c) || Character.isSpaceChar(c)) {
                out.append(c);
                continue;
            }

            // Keep dot if it is likely part of a token (e.g., "4.5")
            if (c == '.') {
                out.append(c);
                continue;
            }

            // Otherwise replace with space to avoid accidental concatenation
            out.append(' ');
        }

        // Collapse whitespace
        return collapseSpaces(out.toString());
    }

    private static String collapseSpaces(String s) {
        if (s == null || s.isEmpty()) return "";
        StringBuilder b = new StringBuilder();
        boolean lastWasSpace = false;

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean isSpace = Character.isWhitespace(c) || Character.isSpaceChar(c);

            if (isSpace) {
                if (!lastWasSpace) {
                    b.append(' ');
                    lastWasSpace = true;
                }
            } else {
                b.append(c);
                lastWasSpace = false;
            }
        }

        String t = b.toString().trim();
        return t;
    }

    /**
     * Returns a similarity score in range [0..1].
     * 1.0 = identical after normalization.
     *
     * This uses:
     * - exact normalized match => 1.0
     * - token-based quick checks
     * - normalized Levenshtein distance for robust typo tolerance
     */
    public static double similarityScore(String a, String b) {
        String na = normalizeJumpName(a);
        String nb = normalizeJumpName(b);

        if (na.isEmpty() || nb.isEmpty()) return 0.0;
        if (na.equals(nb)) return 1.0;

        // Token overlap helps when order differs or there are extra descriptors.
        String[] ta = splitTokens(na);
        String[] tb = splitTokens(nb);

        int common = countCommonTokens(ta, tb);
        int maxLen = Math.max(ta.length, tb.length);

        double tokenOverlap = maxLen > 0 ? ((double) common / (double) maxLen) : 0.0;

        // Edit distance on compact form (remove spaces) handles small typos well.
        String ca = na.replace(" ", "");
        String cb = nb.replace(" ", "");

        int dist = levenshtein(ca, cb);
        int L = Math.max(ca.length(), cb.length());
        double editSim = (L > 0) ? (1.0 - ((double) dist / (double) L)) : 0.0;

        // Weighted blend:
        // - editSim dominates for small typos
        // - tokenOverlap helps with extra words / different ordering
        double score = (0.70 * editSim) + (0.30 * tokenOverlap);

        // Clamp to [0..1]
        if (score < 0.0) score = 0.0;
        if (score > 1.0) score = 1.0;
        return score;
    }

    /**
     * Returns true if jump names are similar enough to be treated as the same jump.
     *
     * Threshold rules are length-aware to avoid incorrect merges on very short names.
     */
    public static boolean areJumpNamesSimilar(String a, String b) {
        String na = normalizeJumpName(a);
        String nb = normalizeJumpName(b);

        if (na.isEmpty() || nb.isEmpty()) return false;
        if (na.equals(nb)) return true;

        String ca = na.replace(" ", "");
        String cb = nb.replace(" ", "");
        int L = Math.max(ca.length(), cb.length());

        double score = similarityScore(na, nb);

        // Stricter for short strings to avoid accidental merges.
        if (L <= 6) {
            return score >= 0.92;
        }
        if (L <= 10) {
            return score >= 0.88;
        }
        return score >= 0.84;
    }

    /**
     * Finds the best fuzzy match index in a list of candidate names.
     * Returns -1 if nothing meets the similarity threshold.
     */
    public static int bestFuzzyMatchIndex(String needle, List<String> candidates) {
        if (candidates == null || candidates.isEmpty()) return -1;

        int bestIdx = -1;
        double bestScore = 0.0;

        for (int i = 0; i < candidates.size(); i++) {
            String c = candidates.get(i);
            double score = similarityScore(needle, c);
            if (score > bestScore) {
                bestScore = score;
                bestIdx = i;
            }
        }

        if (bestIdx < 0) return -1;

        // Must pass threshold to be accepted
        String na = normalizeJumpName(needle).replace(" ", "");
        int L = na.length();

        double min;
        if (L <= 6) min = 0.92;
        else if (L <= 10) min = 0.88;
        else min = 0.84;

        return bestScore >= min ? bestIdx : -1;
    }

    private static String[] splitTokens(String s) {
        if (s == null) return new String[0];
        String t = collapseSpaces(s);
        if (t.isEmpty()) return new String[0];
        return t.split(" ");
    }

    private static int countCommonTokens(String[] a, String[] b) {
        if (a == null || b == null) return 0;
        int common = 0;

        // Simple O(n*m) is fine (jump names are tiny)
        for (int i = 0; i < a.length; i++) {
            String ta = a[i];
            if (ta == null || ta.isEmpty()) continue;

            for (int j = 0; j < b.length; j++) {
                String tb = b[j];
                if (tb == null || tb.isEmpty()) continue;

                if (ta.equals(tb)) {
                    common++;
                    break;
                }
            }
        }

        return common;
    }

    /**
     * Classic Levenshtein distance (iterative DP).
     * No allocations per cell; suitable for short strings.
     */
    public static int levenshtein(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";
        if (a.equals(b)) return 0;

        int n = a.length();
        int m = b.length();

        if (n == 0) return m;
        if (m == 0) return n;

        int[] prev = new int[m + 1];
        int[] cur = new int[m + 1];

        for (int j = 0; j <= m; j++) {
            prev[j] = j;
        }

        for (int i = 1; i <= n; i++) {
            cur[0] = i;
            char ca = a.charAt(i - 1);

            for (int j = 1; j <= m; j++) {
                char cb = b.charAt(j - 1);
                int cost = (ca == cb) ? 0 : 1;

                int del = prev[j] + 1;
                int ins = cur[j - 1] + 1;
                int sub = prev[j - 1] + cost;

                int best = del;
                if (ins < best) best = ins;
                if (sub < best) best = sub;

                cur[j] = best;
            }

            int[] tmp = prev;
            prev = cur;
            cur = tmp;
        }

        return prev[m];

    }
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