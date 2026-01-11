package me.texyle.startreminders.data;

import java.util.ArrayList;

public class ParkourMap {
    private String id;
    private ArrayList<Jump> jumps = new ArrayList<Jump>();

    // Sheet sync metadata (Phase 3)
    private String sheetUrl = "";
    private int sheetGid = -1; // -1 means "not specified"
    private long lastSheetSyncMs = 0L;

    // Placeholder helper sync (CreateJumpContext helper)
    private String placeholderSheetUrl = "";
    private boolean placeholderSyncEnabled = false;
    private int placeholderJumpIndex = 0;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public ArrayList<Jump> getJumps() {
        return jumps;
    }

    public void setJumps(ArrayList<Jump> jumps) {
        this.jumps = jumps;
    }

    public String getSheetUrl() {
        return sheetUrl != null ? sheetUrl : "";
    }

    public void setSheetUrl(String sheetUrl) {
        this.sheetUrl = sheetUrl != null ? sheetUrl : "";
    }

    public int getSheetGid() {
        return sheetGid;
    }

    public void setSheetGid(int sheetGid) {
        this.sheetGid = sheetGid;
    }

    public long getLastSheetSyncMs() {
        return lastSheetSyncMs;
    }

    public void setLastSheetSyncMs(long lastSheetSyncMs) {
        this.lastSheetSyncMs = lastSheetSyncMs;
    }

    public boolean hasSheetConfigured() {
        return getSheetUrl().trim().length() > 0;
    }

    // -------------------------
    // Placeholder sync helpers
    // -------------------------

    public String getPlaceholderSheetUrl() {
        return placeholderSheetUrl != null ? placeholderSheetUrl : "";
    }

    public void setPlaceholderSheetUrl(String url) {
        this.placeholderSheetUrl = url != null ? url : "";
    }

    public boolean isPlaceholderSyncEnabled() {
        return placeholderSyncEnabled;
    }

    public void setPlaceholderSyncEnabled(boolean enabled) {
        this.placeholderSyncEnabled = enabled;
    }

    public int getPlaceholderJumpIndex() {
        return placeholderJumpIndex;
    }

    public void setPlaceholderJumpIndex(int idx) {
        this.placeholderJumpIndex = Math.max(0, idx);
    }
}