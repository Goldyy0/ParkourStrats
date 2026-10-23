package me.texyle.startreminders.data;

import java.util.ArrayList;

public class ParkourMap {
    private String id;
    private ArrayList<Jump> jumps = new ArrayList<Jump>();

    // Template mode: the create key places the jumps of this map one by one instead of opening a dialog.
    private boolean templateMode = false;

    // True hides the map in the game: its jumps are not drawn and not picked. Absent in old files = shown.
    private boolean hiddenInGame = false;

    // Which part of the linked sheet is imported; null = all of it.
    private SheetFilter sheetFilter;

    // Sheet sync metadata (Phase 3)
    private String sheetUrl = "";
    private int sheetGid = -1; // -1 means "not specified"
    private long lastSheetSyncMs = 0L;

    // -------------------------
    // Sections config (v1.6)
    // -------------------------
    private boolean sectionsEnabled = false;

    /** Deepest nesting of sections (levels I..V). */
    public static final int MAX_SECTION_LEVELS = 5;

    // 0 = none, otherwise 1..MAX_SECTION_LEVELS
    private int sectionsCount = 0;

    // Always conceptual length MAX_SECTION_LEVELS (I...V). Old JSON may have null or fewer entries.
    private String[] sectionNames;

    private static final String[] DEFAULT_SECTION_NAMES = new String[] {
            "Section", "Sub-section", "Area", "CP", "Part"
    };

    // -------------------------
    // Section items (Phase 2)
    // Stored per "level" (I..V)
    // -------------------------
    private ArrayList<MapSection> sectionsL1;
    private ArrayList<MapSection> sectionsL2;
    private ArrayList<MapSection> sectionsL3;
    private ArrayList<MapSection> sectionsL4;
    private ArrayList<MapSection> sectionsL5;

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

    public SheetFilter getSheetFilter() {
        return sheetFilter;
    }

    public void setSheetFilter(SheetFilter sheetFilter) {
        this.sheetFilter = sheetFilter;
    }

    public boolean isVisibleInGame() {
        return !hiddenInGame;
    }

    public void setVisibleInGame(boolean visible) {
        this.hiddenInGame = !visible;
    }

    public boolean isTemplateMode() {
        return templateMode;
    }

    public void setTemplateMode(boolean templateMode) {
        this.templateMode = templateMode;
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
        if (count > MAX_SECTION_LEVELS) count = MAX_SECTION_LEVELS;
        this.sectionsCount = count;
        if (count == 0) {
            this.sectionsEnabled = false;
        }
    }

    public int getEffectiveSectionsCount() {
        if (!sectionsEnabled) return 0;
        int c = sectionsCount;
        if (c < 1) c = 1;
        if (c > MAX_SECTION_LEVELS) c = MAX_SECTION_LEVELS;
        return c;
    }

    public String[] getSectionNamesSafe() {
        String[] out = new String[MAX_SECTION_LEVELS];

        // Start with defaults
        for (int i = 0; i < MAX_SECTION_LEVELS; i++) {
            out[i] = DEFAULT_SECTION_NAMES[i];
        }

        // Merge persisted values if present
        if (sectionNames != null) {
            for (int i = 0; i < MAX_SECTION_LEVELS && i < sectionNames.length; i++) {
                String v = sectionNames[i];
                if (v != null && v.trim().length() > 0) {
                    out[i] = v;
                }
            }
        }

        return out;
    }

    public String getSectionName(int idx) {
        if (idx < 0 || idx >= MAX_SECTION_LEVELS) return "";
        return getSectionNamesSafe()[idx];
    }

    public void setSectionName(int idx, String name) {
        if (idx < 0 || idx >= MAX_SECTION_LEVELS) return;

        if (this.sectionNames == null || this.sectionNames.length != MAX_SECTION_LEVELS) {
            this.sectionNames = getSectionNamesSafe();
        }

        this.sectionNames[idx] = (name != null) ? name : "";
    }

    public void setSectionNames(String[] names) {
        if (names == null) {
            this.sectionNames = null;
            return;
        }

        String[] out = new String[MAX_SECTION_LEVELS];
        for (int i = 0; i < MAX_SECTION_LEVELS; i++) {
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
        if (levelOneBased == 5) {
            if (sectionsL5 == null) sectionsL5 = new ArrayList<MapSection>();
            return sectionsL5;
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