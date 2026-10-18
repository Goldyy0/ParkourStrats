package me.texyle.startreminders.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.texyle.startreminders.data.MapSection;
import me.texyle.startreminders.data.ParkourMap;
import me.texyle.startreminders.gui.hierarchy.GuiConfirm;
import me.texyle.startreminders.gui.hierarchy.GuiTextPrompt;
import me.texyle.startreminders.persistence.BuiltinContainers;
import me.texyle.startreminders.reminders.SelectionState;
import me.texyle.startreminders.services.SectionService;
import me.texyle.startreminders.sheets.SectionSyncService;
import me.texyle.startreminders.utils.ChatUtils;

/**
 * The sections (named, colored ranges of jumps) of the current map, level by level.
 * Sections exist only for the user's own maps, not for Global or RestoredStrats.
 */
final class HubSectionsTab implements HubTab {

    private static final String[] ROMAN = { "", "I", "II", "III", "IV" };
    private static final int GAP = 8;
    private static final int FOOTER_H = 24;

    private final GuiHub hub;

    private int x;
    private int y;
    private int w;
    private int h;

    private int activeLevel = 1;
    private int shownLevels = -1;

    private UiSegmented levelSelector;
    private final UiList<MapSection> list;
    private final UiButton newButton;
    private final UiButton syncButton;
    private final UiButton editButton;
    private final UiButton deleteButton;
    private final List<UiWidget> widgets = new ArrayList<UiWidget>();

    HubSectionsTab(GuiHub hub, UiPopup popup) {
        this.hub = hub;

        list = new UiList<MapSection>(this::currentSections, s -> String.valueOf(s.getName()))
                .emptyText("No sections on this level yet")
                .renderer(this::drawRow)
                .onActivate(s -> editSection());

        newButton = new UiButton("+ New section", UiButton.Style.PRIMARY, this::newSection);
        syncButton = new UiButton("Sync from sheet...", UiButton.Style.SECONDARY, this::syncFromSheet);
        editButton = new UiButton("Edit", UiButton.Style.SECONDARY, this::editSection);
        deleteButton = new UiButton("Delete", UiButton.Style.DANGER, this::deleteSection);

        widgets.add(list);
        widgets.add(newButton);
        widgets.add(syncButton);
        widgets.add(editButton);
        widgets.add(deleteButton);
    }

    // -------------------------
    // Data
    // -------------------------

    private ParkourMap map() {
        return SelectionState.getSelectedMap();
    }

    /** True if the current map can have sections at all. */
    private boolean available() {
        ParkourMap map = map();
        return map != null && !BuiltinContainers.isProtectedServer(SelectionState.getSelectedServer())
                && map.getEffectiveSectionsCount() > 0;
    }

    private int levels() {
        ParkourMap map = map();
        return map != null ? Math.max(1, Math.min(4, map.getMaxSectionLevelsAllowed())) : 1;
    }

    private List<MapSection> currentSections() {
        ParkourMap map = map();
        if (map == null || !available()) return Collections.emptyList();
        ArrayList<MapSection> sections = map.getSectionsForLevel(activeLevel);
        return sections != null ? sections : Collections.<MapSection>emptyList();
    }

    // -------------------------
    // Layout
    // -------------------------

    @Override
    public void layout(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        rebuildLevelSelector();
        place();
    }

    @Override
    public void onShow() {
        list.clearSelection();
        rebuildLevelSelector();
        place();
    }

    private void rebuildLevelSelector() {
        int levels = levels();
        activeLevel = Math.max(1, Math.min(activeLevel, levels));

        if (levelSelector != null && shownLevels == levels) {
            levelSelector.setSelected(activeLevel - 1);
            return;
        }

        shownLevels = levels;
        String[] options = new String[levels];
        for (int i = 0; i < levels; i++) options[i] = "Level " + ROMAN[i + 1];

        levelSelector = new UiSegmented(options, activeLevel - 1, index -> {
            activeLevel = index + 1;
            list.clearSelection();
        });
    }

    private void place() {
        levelSelector.setBounds(x, y + 1, levelSelector.preferredWidth(), 18);

        int newW = newButton.preferredWidth();
        int syncW = syncButton.preferredWidth();
        newButton.setBounds(x + w - newW, y, newW, UiTheme.CONTROL_HEIGHT);
        syncButton.setBounds(newButton.x - GAP - syncW, y, syncW, UiTheme.CONTROL_HEIGHT);

        int listTop = y + UiTheme.CONTROL_HEIGHT + GAP;
        list.setBounds(x, listTop, w, h - UiTheme.CONTROL_HEIGHT - GAP - FOOTER_H - GAP);

        int footerY = y + h - FOOTER_H + 2;
        deleteButton.setBounds(x + w - 70, footerY, 70, UiTheme.CONTROL_HEIGHT);
        editButton.setBounds(deleteButton.x - GAP - 70, footerY, 70, UiTheme.CONTROL_HEIGHT);
    }

    // -------------------------
    // Drawing
    // -------------------------

    @Override
    public void draw(int mouseX, int mouseY, float partialTicks) {
        ParkourMap map = map();
        boolean available = available();
        boolean selected = list.getSelected() != null;

        editButton.enabled = selected;
        deleteButton.enabled = selected;

        if (!available) {
            drawUnavailable(map);
            return;
        }

        levelSelector.draw(mouseX, mouseY);
        syncButton.draw(mouseX, mouseY);
        newButton.draw(mouseX, mouseY);

        list.draw(mouseX, mouseY);
        editButton.draw(mouseX, mouseY);
        deleteButton.draw(mouseX, mouseY);

        MapSection sel = list.getSelected();
        int textY = y + h - FOOTER_H + 2 + 6;
        if (sel == null) {
            UiDraw.text("Select a section to edit it. Double-click opens it.", x + 2, textY, UiTheme.TEXT_MUTED);
        } else {
            UiDraw.text(UiDraw.fit(SectionChoices.label(activeLevel, sel), editButton.x - x - 16), x + 2, textY, UiTheme.TEXT);
        }
    }

    private void drawUnavailable(ParkourMap map) {
        int cx = x + w / 2;
        int cy = y + h / 2;

        String title;
        String hint;
        if (map == null) {
            title = "No map selected";
            hint = "Pick a map in the Strategies tab first.";
        } else if (BuiltinContainers.isProtectedServer(SelectionState.getSelectedServer())) {
            title = "Sections are not available here";
            hint = "Global and RestoredStrats have no sections.";
        } else {
            title = "This map has no sections";
            hint = "Turn them on in Library, under the map's \"Edit map...\".";
        }

        UiDraw.centered(title, cx, cy - 10, UiTheme.TEXT_MUTED);
        UiDraw.centered(hint, cx, cy + 4, UiTheme.TEXT_DISABLED);
    }

    private void drawRow(MapSection s, int rx, int ry, int rw, int rh, boolean selected) {
        int textY = ry + (rh - 8) / 2;
        int cx = rx + 8;

        String prefix = parentPathPrefix(s);
        if (!prefix.isEmpty()) {
            UiDraw.text(prefix, cx, textY, selected ? UiTheme.TEXT : UiTheme.TEXT_MUTED);
            cx += UiDraw.width(prefix);
        }

        int square = 10;
        UiDraw.box(cx, ry + (rh - square) / 2, square, square, s.getColorArgb() | 0xFF000000, UiTheme.CONTROL_BORDER);

        String name = s.getName() != null ? s.getName() : "";
        String range = (s.getStartJumpIndex() < 0 || s.getEndJumpIndex() < 0)
                ? " (Not set)"
                : " (" + (s.getStartJumpIndex() + 1) + " - " + (s.getEndJumpIndex() + 1) + ")";
        UiDraw.text(UiDraw.fit(name + range, rw - (cx - rx) - square - 24), cx + square + 8, textY, UiTheme.TEXT);
    }

    /** "Area 1/S1: " - the names of the sections of the upper levels that contain this one. */
    private String parentPathPrefix(MapSection section) {
        ParkourMap map = map();
        if (map == null || section == null || activeLevel <= 1) return "";

        int start = section.getStartJumpIndex();
        int end = section.getEndJumpIndex();
        if (start < 0 || end < 0) return "";

        StringBuilder sb = new StringBuilder();
        int upper = Math.min(activeLevel - 1, levels());

        for (int lvl = 1; lvl <= upper; lvl++) {
            ArrayList<MapSection> parents = map.getSectionsForLevel(lvl);
            if (parents == null) continue;

            for (MapSection p : parents) {
                if (p == null) continue;

                int a = p.getStartJumpIndex();
                int b = p.getEndJumpIndex();
                if (a < 0 || b < 0) continue;
                if (b < a) {
                    int tmp = a;
                    a = b;
                    b = tmp;
                }

                if (start >= a && end <= b) {
                    String n = p.getName() != null ? p.getName().trim() : "";
                    if (!n.isEmpty()) {
                        if (sb.length() > 0) sb.append("/");
                        sb.append(n);
                    }
                    break;
                }
            }
        }

        return sb.length() == 0 ? "" : sb + ": ";
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (!available()) {
            return false;
        }
        if (levelSelector.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        for (int i = widgets.size() - 1; i >= 0; i--) {
            if (widgets.get(i).mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseWheel(int mouseX, int mouseY, int delta) {
        return available() && list.mouseWheel(mouseX, mouseY, delta);
    }

    // -------------------------
    // Actions
    // -------------------------

    private void newSection() {
        hub.open(new GuiEditSection(hub, map(), activeLevel, null));
    }

    private void editSection() {
        MapSection sel = list.getSelected();
        if (sel != null) {
            hub.open(new GuiEditSection(hub, map(), activeLevel, sel));
        }
    }

    private void deleteSection() {
        final MapSection sel = list.getSelected();
        if (sel == null) return;

        String name = (sel.getName() != null) ? sel.getName().trim() : "";
        if (name.isEmpty()) name = "<unnamed>";

        hub.open(new GuiConfirm(hub, "Delete section", "Delete section \"" + name + "\"?",
                new GuiConfirm.IConfirmHandler() {
                    @Override
                    public void onYes() {
                        SectionService.deleteSection(map(), activeLevel, sel);
                        list.clearSelection();
                    }

                    @Override
                    public void onNo() { }
                }));
    }

    private void syncFromSheet() {
        final ParkourMap map = map();
        if (map == null) return;

        hub.open(new GuiTextPrompt(hub, "Sync sections: paste spreadsheet URL", map.getSheetUrl(),
                new GuiTextPrompt.IResultHandler() {
                    @Override
                    public void onConfirm(String text) {
                        String url = (text != null) ? text.trim() : "";

                        // The same URL is used for sheet sync and section sync.
                        map.setSheetUrl(url);

                        boolean ok = SectionSyncService.syncSectionsFromAppsScript(map, url);
                        if (ok) {
                            ChatUtils.info("Sections synced from spreadsheet.");
                        } else {
                            // SectionSyncService already posts a detailed error; add a generic one only if it did not.
                            String err = SectionSyncService.getLastSectionSyncError();
                            if (err == null || err.trim().isEmpty()) {
                                ChatUtils.error("Section sync failed.");
                            }
                        }
                        list.clearSelection();
                    }

                    @Override
                    public void onCancel() { }
                }));
    }
}
