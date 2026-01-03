package me.texyle.startreminders.sheets;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public final class SheetSyncManager {

    public interface ISyncCallback {
        void onSuccess(String csvText, String cachePath);
        void onError(String errorMessage);
    }

    private static boolean syncInProgress = false;

    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final int READ_TIMEOUT_MS = 12000;
    private static final int MAX_REDIRECTS = 5;

    // Set to false to disable HTTP debug logs
    private static final boolean DEBUG = false;

    private SheetSyncManager() {}

    public static boolean isSyncInProgress() {
        return syncInProgress;
    }

    public static void fetchCsvAsync(final String sheetUrl, final int forcedGid, final String cacheFilePath, final ISyncCallback cb) {
        if (syncInProgress) {
            if (cb != null) cb.onError("Sync already in progress.");
            return;
        }

        syncInProgress = true;

        new Thread(() -> {
            try {
                String urlTrimmed = sheetUrl != null ? sheetUrl.trim() : "";
                if (urlTrimmed.isEmpty()) {
                    fail(cb, "Invalid Google Sheets URL.");
                    return;
                }

                String csvUrl = SheetUrlUtils.normalizeToCsvUrl(urlTrimmed, forcedGid);
                if (csvUrl == null || csvUrl.trim().isEmpty()) {
                    fail(cb, "Invalid Google Sheets URL (unable to derive CSV export URL).");
                    return;
                }

                HttpResult res = httpGetFollowRedirects(csvUrl);

                if (res == null || res.body == null || res.body.trim().isEmpty()) {
                    fail(cb, "Empty response. The sheet might be private/unpublished, or the URL is invalid.");
                    return;
                }

                String bodyTrim = res.body.trim();
                String ct = res.contentType != null ? res.contentType : "";

                boolean looksHtml = ct.toLowerCase().contains("text/html")
                        || bodyTrim.startsWith("<!DOCTYPE html")
                        || bodyTrim.startsWith("<html")
                        || bodyTrim.startsWith("<HTML")
                        || bodyTrim.startsWith("<!doctype html");

                if (looksHtml) {
                    String msg =
                            "Sheet download returned HTML (not CSV).\n"
                                    + "Final URL: " + (res.finalUrl != null ? res.finalUrl : "<unknown>") + "\n"
                                    + "Content-Type: " + (res.contentType != null ? res.contentType : "<unknown>") + "\n"
                                    + "Tip: Use a published CSV link that contains 'output=csv' (Publish to web → CSV), "
                                    + "or paste a normal /edit link and let the mod convert it automatically.";
                    fail(cb, msg);
                    return;
                }

                if (cacheFilePath != null && !cacheFilePath.trim().isEmpty()) {
                    writeToFile(cacheFilePath, res.body);
                }

                if (cb != null) cb.onSuccess(res.body, cacheFilePath);

            } catch (Exception ex) {
                fail(cb, "Failed to download CSV.");
            } finally {
                syncInProgress = false;
            }
        }, "StratReminders-SheetSync").start();
    }

    private static void fail(ISyncCallback cb, String msg) {
        if (cb != null) cb.onError(msg);
    }

    private static final class HttpResult {
        final int code;
        final String finalUrl;
        final String contentType;
        final String location;
        final String body;

        HttpResult(int code, String finalUrl, String contentType, String location, String body) {
            this.code = code;
            this.finalUrl = finalUrl;
            this.contentType = contentType;
            this.location = location;
            this.body = body;
        }
    }

    private static HttpResult httpGetFollowRedirects(String url) throws Exception {
        String current = url;

        for (int i = 0; i <= MAX_REDIRECTS; i++) {
            HttpURLConnection conn = null;
            InputStream is = null;

            try {
                conn = (HttpURLConnection) new URL(current).openConnection();
                conn.setRequestMethod("GET");
                conn.setInstanceFollowRedirects(false);
                conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
                conn.setReadTimeout(READ_TIMEOUT_MS);

                conn.setRequestProperty("User-Agent", "StratReminders/1.0");
                conn.setRequestProperty("Accept", "text/csv,*/*");
                conn.setRequestProperty("Accept-Encoding", "identity");

                int code = conn.getResponseCode();
                String contentType = conn.getHeaderField("Content-Type");
                String location = conn.getHeaderField("Location");

                if (DEBUG) {
                    System.out.println("[ParkourStrats] HTTP " + code + " URL=" + current);
                    System.out.println("[ParkourStrats] Content-Type=" + contentType);
                    System.out.println("[ParkourStrats] Location=" + location);
                }

                if (isRedirect(code)) {
                    String loc = location;
                    if (loc == null || loc.trim().isEmpty()) {
                        return new HttpResult(code, current, contentType, null, "");
                    }
                    URL base = new URL(current);
                    URL next = new URL(base, loc);
                    current = next.toString();
                    continue;
                }

                InputStream raw;
                if (code != 200) {
                    raw = conn.getErrorStream();
                    if (raw == null) {
                        return new HttpResult(code, current, contentType, location, "");
                    }
                } else {
                    raw = conn.getInputStream();
                }

                is = new BufferedInputStream(raw);
                String body = readAllUtf8(is);

                if (DEBUG) {
                    String prefix = body.substring(0, Math.min(200, body.length()))
                            .replace("\n", "\\n")
                            .replace("\r", "\\r");
                    System.out.println("[ParkourStrats] Body prefix=" + prefix);
                }

                return new HttpResult(code, current, contentType, location, body);

            } finally {
                try { if (is != null) is.close(); } catch (Exception ignored) {}
                try { if (conn != null) conn.disconnect(); } catch (Exception ignored) {}
            }
        }

        return null;
    }

    private static boolean isRedirect(int code) {
        return code == 301 || code == 302 || code == 303 || code == 307 || code == 308;
    }

    private static String readAllUtf8(InputStream is) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        return new String(out.toByteArray(), "UTF-8");
    }

    private static void writeToFile(String path, String content) {
        try {
            File f = new File(path);
            File parent = f.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();

            FileOutputStream out = new FileOutputStream(f);
            out.write(content.getBytes("UTF-8"));
            out.flush();
            out.close();
        } catch (Exception ignored) {}
    }
}