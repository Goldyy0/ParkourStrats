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

    // -------------------------
    // Sections config (v1.6)
    // -------------------------
    private boolean sectionsEnabled = false;

    // 0 = none, otherwise 1..4
    private int sectionsCount = 0;

    // Always conceptual length 4 (I...IV). Old JSON may have null.
    private String[] sectionNames;

    private static final String[] DEFAULT_SECTION_NAMES = new String[] {
            "Section", "Sub-section", "Area", "CP"
    };

    // -------------------------
    // Section items (Phase 2)
    // Stored per "level" (I..IV)
    // -------------------------
    private ArrayList<MapSection> sectionsL1;
    private ArrayList<MapSection> sectionsL2;
    private ArrayList<MapSection> sectionsL3;
    private ArrayList<MapSection> sectionsL4;

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

    // -------------------------
    // Sections config API (null-safe)
    // -------------------------

    public boolean isSectionsEnabled() {
        return sectionsEnabled;
    }

    public void setSectionsEnabled(boolean enabled) {
        this.sectionsEnabled = enabled;
        if (!enabled) {
            this.sectionsCount = 0;
        } else if (this.sectionsCount <= 0) {
            this.sectionsCount = 1;
        }
    }

    public int getSectionsCountRaw() {
        return sectionsCount;
    }

    public void setSectionsCountRaw(int count) {
        if (count < 0) count = 0;
        if (count > 4) count = 4;
        this.sectionsCount = count;
        if (count == 0) {
            this.sectionsEnabled = false;
        }
    }

    public int getEffectiveSectionsCount() {
        if (!sectionsEnabled) return 0;
        int c = sectionsCount;
        if (c < 1) c = 1;
        if (c > 4) c = 4;
        return c;
    }

    public String[] getSectionNamesSafe() {
        String[] out = new String[4];

        // Start with defaults
        for (int i = 0; i < 4; i++) {
            out[i] = DEFAULT_SECTION_NAMES[i];
        }

        // Merge persisted values if present
        if (sectionNames != null) {
            for (int i = 0; i < 4 && i < sectionNames.length; i++) {
                String v = sectionNames[i];
                if (v != null && v.trim().length() > 0) {
                    out[i] = v;
                }
            }
        }

        return out;
    }

    public String getSectionName(int idx) {
        if (idx < 0 || idx > 3) return "";
        return getSectionNamesSafe()[idx];
    }

    public void setSectionName(int idx, String name) {
        if (idx < 0 || idx > 3) return;

        if (this.sectionNames == null || this.sectionNames.length != 4) {
            this.sectionNames = getSectionNamesSafe();
        }

        this.sectionNames[idx] = (name != null) ? name : "";
    }

    public void setSectionNames(String[] names) {
        if (names == null) {
            this.sectionNames = null;
            return;
        }

        String[] out = new String[4];
        for (int i = 0; i < 4; i++) {
            out[i] = (i < names.length && names[i] != null) ? names[i] : "";
        }
        this.sectionNames = out;
    }

    // -------------------------
    // Section items API (Phase 2)
    // -------------------------

    private ArrayList<MapSection> ensureList(int levelOneBased) {
        if (levelOneBased == 1) {
            if (sectionsL1 == null) sectionsL1 = new ArrayList<MapSection>();
            return sectionsL1;
        }
        if (levelOneBased == 2) {
            if (sectionsL2 == null) sectionsL2 = new ArrayList<MapSection>();
            return sectionsL2;
        }
        if (levelOneBased == 3) {
            if (sectionsL3 == null) sectionsL3 = new ArrayList<MapSection>();
            return sectionsL3;
        }
        if (levelOneBased == 4) {
            if (sectionsL4 == null) sectionsL4 = new ArrayList<MapSection>();
            return sectionsL4;
        }
        return new ArrayList<MapSection>();
    }

    public ArrayList<MapSection> getSectionsForLevel(int levelOneBased) {
        // Return a live list; caller may modify it.
        return ensureList(levelOneBased);
    }

    public int getMaxSectionLevelsAllowed() {
        return getEffectiveSectionsCount();
    }
}