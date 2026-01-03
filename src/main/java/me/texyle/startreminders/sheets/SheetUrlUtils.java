package me.texyle.startreminders.sheets;

public final class SheetUrlUtils {

    private SheetUrlUtils() {}

    public static class ParsedSheetUrl {
        public final String spreadsheetId;
        public final int gid;

        public ParsedSheetUrl(String spreadsheetId, int gid) {
            this.spreadsheetId = spreadsheetId;
            this.gid = gid;
        }
    }

    /**
     * Normalize any supported Google Sheets URL into a CSV-downloadable URL.
     *
     * Supported inputs:
     *  - Normal edit/view URL: https://docs.google.com/spreadsheets/d/<ID>/edit?gid=0#gid=0
     *    -> https://docs.google.com/spreadsheets/d/<ID>/export?format=csv&gid=0
     *
     *  - Published HTML URL: https://docs.google.com/spreadsheets/d/e/<PUBLISHED_ID>/pubhtml?gid=0&single=true
     *    -> https://docs.google.com/spreadsheets/d/e/<PUBLISHED_ID>/pub?gid=0&single=true&output=csv
     *
     *  - Published CSV URL: https://docs.google.com/spreadsheets/d/e/<PUBLISHED_ID>/pub?gid=0&single=true&output=csv
     *    -> used as-is (optionally forcedGid applied if present)
     */
    public static String normalizeToCsvUrl(String url, int forcedGid) {
        if (url == null) return null;

        String u = url.trim();
        if (u.isEmpty()) return null;

        boolean isPublished = u.contains("/spreadsheets/d/e/");
        if (isPublished) {
            int gid = forcedGid >= 0 ? forcedGid : extractGid(u);

            // Convert pubhtml -> pub
            u = u.replace("/pubhtml", "/pub");

            // Ensure output=csv
            if (!containsParam(u, "output")) {
                u = appendParam(u, "output", "csv");
            } else {
                // If output exists but isn't csv, overwrite to csv
                u = setOrReplaceParam(u, "output", "csv");
            }

            // Ensure single=true (recommended)
            if (!containsParam(u, "single")) {
                u = appendParam(u, "single", "true");
            }

            // Apply gid if known
            if (gid >= 0) {
                if (!containsParam(u, "gid")) {
                    u = appendParam(u, "gid", String.valueOf(gid));
                } else {
                    u = setOrReplaceParam(u, "gid", String.valueOf(gid));
                }
            }

            return u;
        }

        ParsedSheetUrl parsed = parse(u);
        if (parsed == null) return null;

        int gid = forcedGid >= 0 ? forcedGid : parsed.gid;
        return buildCsvExportUrl(parsed.spreadsheetId, gid);
    }

    public static ParsedSheetUrl parse(String url) {
        if (url == null) return null;
        String u = url.trim();
        if (u.isEmpty()) return null;

        // Only parse normal links ".../spreadsheets/d/<ID>/..."
        // Do NOT parse published links ".../spreadsheets/d/e/<PUBLISHED_ID>/..."
        String marker = "/spreadsheets/d/";
        int idx = u.indexOf(marker);
        if (idx < 0) return null;

        int start = idx + marker.length();

        // If next segment is "e", it's a published link, not a normal spreadsheet ID
        if (start < u.length() && u.startsWith("e/", start)) {
            return null;
        }

        int end = u.indexOf("/", start);
        if (end < 0) end = u.length();

        String spreadsheetId = u.substring(start, end).trim();
        if (spreadsheetId.isEmpty()) return null;

        int gid = extractGid(u);
        return new ParsedSheetUrl(spreadsheetId, gid);
    }

    public static int extractGid(String url) {
        if (url == null) return -1;

        String u = url;

        // Try "gid=<number>" anywhere in the URL (works for ?gid=, &gid= and #gid=)
        int q = u.indexOf("gid=");
        if (q >= 0) {
            int start = q + 4;
            int end = start;
            while (end < u.length() && Character.isDigit(u.charAt(end))) {
                end++;
            }
            if (end > start) {
                try {
                    return Integer.parseInt(u.substring(start, end));
                } catch (Exception ignored) {}
            }
        }

        return -1;
    }

    public static String buildCsvExportUrl(String spreadsheetId, int gid) {
        String base = "https://docs.google.com/spreadsheets/d/" + spreadsheetId + "/export?format=csv";
        if (gid >= 0) {
            return base + "&gid=" + gid;
        }
        return base;
    }

    private static boolean containsParam(String url, String key) {
        if (url == null || key == null) return false;
        String needle = key + "=";
        return url.contains("?" + needle) || url.contains("&" + needle);
    }

    private static String appendParam(String url, String key, String value) {
        if (url == null) return null;
        String sep = url.contains("?") ? "&" : "?";
        return url + sep + key + "=" + value;
    }

    private static String setOrReplaceParam(String url, String key, String value) {
        if (url == null || key == null) return url;

        String q = key + "=";
        int idx = url.indexOf("?" + q);
        int idx2 = url.indexOf("&" + q);

        int p = -1;
        if (idx >= 0) p = idx + 1;      // position after '?'
        else if (idx2 >= 0) p = idx2 + 1; // position after '&'
        else return appendParam(url, key, value);

        int start = p + q.length();
        int end = start;
        while (end < url.length()) {
            char c = url.charAt(end);
            if (c == '&' || c == '#') break;
            end++;
        }

        return url.substring(0, start) + value + url.substring(end);
    }
}