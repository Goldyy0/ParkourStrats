package me.texyle.startreminders.sheets;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Downloads a Google Sheet on a background thread. The CSV export provides every row; the Apps Script endpoint
 * adds the section cells (merged cells filled in) and their colors when it can be reached.
 */
public final class SheetSyncManager {

    public interface Callback {
        /** Called on the download thread. */
        void onSuccess(SheetData data);

        /** Called on the download thread. */
        void onError(String errorMessage);
    }

    private static final String SCRIPT_ENDPOINT =
            "https://script.google.com/macros/s/AKfycbyTqmi25lLkfqAqfmcNGDYK7IRwBU-40XWaAwIBNFfkX45LuH-cTjwi2OzMCAw5ElYk/exec";

    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final int READ_TIMEOUT_MS = 15000;
    private static final int MAX_REDIRECTS = 5;
    private static final int MAX_BODY_BYTES = 8 * 1024 * 1024;

    private static final AtomicBoolean running = new AtomicBoolean(false);

    private SheetSyncManager() {}

    public static boolean isSyncInProgress() {
        return running.get();
    }

    public static void fetchAsync(final String sheetUrl, final int forcedGid, final Callback cb) {
        if (!running.compareAndSet(false, true)) {
            cb.onError("Sync already in progress.");
            return;
        }

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                SheetData data = null;
                String error = null;
                try {
                    String url = sheetUrl != null ? sheetUrl.trim() : "";
                    if (url.isEmpty()) {
                        error = "No sheet URL.";
                    } else {
                        Outcome outcome = download(url, forcedGid);
                        data = outcome.data;
                        error = outcome.error;
                    }
                } catch (Throwable t) {
                    error = "Failed to download the sheet.";
                }

                // Free the slot before reporting, so the callback may start the next sync.
                running.set(false);

                if (data != null) {
                    cb.onSuccess(data);
                } else {
                    cb.onError(error != null ? error : "Failed to download the sheet.");
                }
            }
        }, "StratReminders-SheetSync");
        thread.setDaemon(true);
        thread.start();
    }

    private static final class Outcome {
        final SheetData data;
        final String error;

        Outcome(SheetData data, String error) {
            this.data = data;
            this.error = error;
        }
    }

    private static Outcome download(String url, int forcedGid) {
        // 1) The sheet itself, as CSV: every row, nothing else.
        String csvUrl = SheetUrlUtils.normalizeToCsvUrl(url, forcedGid);
        if (csvUrl == null || csvUrl.trim().isEmpty()) {
            return new Outcome(null, "Invalid Google Sheets URL.");
        }

        SheetData data;
        try {
            HttpResult res = get(csvUrl);
            if (res.code != 200) {
                String hint = (res.code == 401 || res.code == 403 || res.code == 404)
                        ? " The sheet is probably private; share it with 'anyone with the link'." : "";
                return new Outcome(null, "Download failed (HTTP " + res.code + ")." + hint);
            }

            String body = res.body.trim();
            String type = res.contentType != null ? res.contentType.toLowerCase() : "";
            if (body.isEmpty()) {
                return new Outcome(null, "The sheet is empty or not shared.");
            }
            if (type.contains("text/html") || body.toLowerCase().startsWith("<!doctype html") || body.toLowerCase().startsWith("<html")) {
                return new Outcome(null, "The sheet download returned a web page, not data. Share the sheet with "
                        + "'anyone with the link' or publish it as CSV.");
            }

            data = SheetData.fromCsv(res.body);
            if (data.error != null) {
                return new Outcome(null, data.error);
            }
        } catch (IOException ex) {
            return new Outcome(null, "Failed to download the sheet (" + ex.getClass().getSimpleName() + ").");
        }

        // 2) The sheet script, for the section columns: merged cells expanded, and their colors.
        if (data.cols.jump > 0) {
            data.warning = addSectionsFromScript(data, url);
        }
        return new Outcome(data, null);
    }

    /** @return why the script could not be used (the sheet is still imported, without section colors), or null */
    private static String addSectionsFromScript(SheetData data, String url) {
        try {
            HttpResult res = get(SCRIPT_ENDPOINT + "?sheetUrl=" + URLEncoder.encode(url, "UTF-8"));
            if (res.code != 200 || !res.body.trim().startsWith("{")) {
                return "The sheet script answered with HTTP " + res.code + "; section colors were not used.";
            }

            SheetData.ScriptRows rows = SheetData.parseScript(res.body);
            if (rows == null) {
                return "The sheet script returned no usable rows; section colors were not used.";
            }
            return data.mergeSections(rows);
        } catch (IOException ex) {
            return "The sheet script could not be reached; section colors were not used.";
        }
    }

    private static final class HttpResult {
        final int code;
        final String contentType;
        final String body;

        HttpResult(int code, String contentType, String body) {
            this.code = code;
            this.contentType = contentType;
            this.body = body;
        }
    }

    private static HttpResult get(String url) throws IOException {
        String current = url;

        for (int i = 0; i <= MAX_REDIRECTS; i++) {
            HttpURLConnection conn = (HttpURLConnection) new URL(current).openConnection();
            try {
                conn.setRequestMethod("GET");
                conn.setInstanceFollowRedirects(false);
                conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
                conn.setReadTimeout(READ_TIMEOUT_MS);
                conn.setUseCaches(false);
                conn.setRequestProperty("User-Agent", "StratReminders/1.0");
                conn.setRequestProperty("Accept-Encoding", "identity");

                int code = conn.getResponseCode();
                if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                    String location = conn.getHeaderField("Location");
                    if (location == null || location.trim().isEmpty()) {
                        return new HttpResult(code, null, "");
                    }
                    current = new URL(new URL(current), location).toString();
                    continue;
                }

                InputStream raw = code == 200 ? conn.getInputStream() : conn.getErrorStream();
                String body = raw != null ? readUtf8(new BufferedInputStream(raw)) : "";
                return new HttpResult(code, conn.getHeaderField("Content-Type"), body);
            } finally {
                conn.disconnect();
            }
        }

        throw new IOException("Too many redirects");
    }

    private static String readUtf8(InputStream in) throws IOException {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > MAX_BODY_BYTES) {
                    throw new IOException("Response too large");
                }
            }
            return new String(out.toByteArray(), "UTF-8");
        } finally {
            in.close();
        }
    }
}
