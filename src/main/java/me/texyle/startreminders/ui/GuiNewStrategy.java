package me.texyle.startreminders.ui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import me.texyle.startreminders.data.Jump;
import me.texyle.startreminders.data.MapSection;
import me.texyle.startreminders.data.ParkourMap;
import me.texyle.startreminders.data.ServerProfile;
import me.texyle.startreminders.persistence.BuiltinContainers;
import me.texyle.startreminders.persistence.DataStoreRepository;
import me.texyle.startreminders.reminders.Reminder;
import me.texyle.startreminders.reminders.SelectionState;
import me.texyle.startreminders.services.JumpService;
import me.texyle.startreminders.services.MapService;
import me.texyle.startreminders.services.SectionLayoutService;
import me.texyle.startreminders.services.ServerService;
import me.texyle.startreminders.ui.UiPopup.Entry;
import me.texyle.startreminders.utils.IdRules;

/**
 * First step of adding a strategy: name the jump and choose where it lives (server, map, optional section).
 * "Continue" opens the strategy editor.
 */
public class GuiNewStrategy extends UiScreen {

    private static final int ROW_H = 28;
    private static final int LABEL_W = 84;

    /** Where the editor returns after saving; null closes all screens. */
    private final GuiScreen after;

    private ServerProfile selectedServer;
    private ParkourMap selectedMap;

    private int selectedSectionLevel = 0; // 0 = none
    private MapSection selectedSection = null;

    private String errorText = "";

    // Survives screen rebuilds (resize, coming back from sub-dialogs).
    private String nameDraft = "";

    private UiTextField nameField;
    private UiDropdown serverDropdown;
    private UiDropdown mapDropdown;
    private UiDropdown sectionDropdown;
    private UiButton continueButton;

    public GuiNewStrategy(GuiScreen parent, GuiScreen after) {
        super(parent);
        this.after = after;

        selectedServer = SelectionState.getSelectedServer();
        selectedMap = SelectionState.getSelectedMap();

        if (selectedServer == null) {
            selectedMap = null;
        }

        // Disallow RestoredStrats as context for creating new jumps
        if (selectedServer != null && BuiltinContainers.isRestoredServer(selectedServer)) {
            selectedServer = null;
            selectedMap = null;
        }

        // If server is Global, force global map
        if (selectedServer != null && BuiltinContainers.isGlobalServer(selectedServer)) {
            selectedMap = BuiltinContainers.getGlobalMap();
        }

        // Restore the remembered section (only if server + map match)
        clearSection();
        StickySectionCache.Pick pick = StickySectionCache.restore(selectedServer, selectedMap);
        if (pick != null) {
            selectedSectionLevel = pick.levelOneBased;
            selectedSection = pick.section;
        }
    }

    // -------------------------
    // Layout
    // -------------------------

    @Override
    protected String getTitle() {
        return "New strategy";
    }

    @Override
    protected int getPanelWidth() {
        return 440;
    }

    @Override
    protected int getPanelHeight() {
        return 250;
    }

    @Override
    protected void build(int x, int y, int w, int h) {
        int fieldX = x + LABEL_W;
        int fieldW = w - LABEL_W;
        int rowY = y;

        nameField = add(new UiTextField(nameDraft, 48).placeholder("e.g. Jump 1").onChange(t -> {
            nameDraft = t;
            errorText = "";
        }), fieldX, rowY, fieldW, 20);
        nameField.setFocused(true);
        rowY += ROW_H;

        serverDropdown = add(new UiDropdown(popup, "", this::serverLabel, this::serverEntries), fieldX, rowY, fieldW, 20);
        rowY += ROW_H;

        mapDropdown = add(new UiDropdown(popup, "", this::mapLabel, this::mapEntries), fieldX, rowY, fieldW, 20);
        rowY += ROW_H;

        sectionDropdown = add(new UiDropdown(popup, "", this::sectionLabel, this::sectionEntries), fieldX, rowY, fieldW, 20);

        continueButton = new UiButton("Continue", UiButton.Style.PRIMARY, this::onNext);
        UiButton cancel = new UiButton("Cancel", UiButton.Style.SECONDARY, this::onBack);
        addFooter(cancel, continueButton);
    }

    @Override
    protected void drawContent(int mouseX, int mouseY, float partialTicks) {
        boolean global = BuiltinContainers.isGlobalServer(selectedServer);
        mapDropdown.enabled = selectedServer != null && !global;
        sectionDropdown.enabled = isSectionPickAllowed();
        continueButton.enabled = isReady();
        nameField.setInvalid(!nameDraft.trim().isEmpty() && !IdRules.isValidId(nameDraft));

        int rowY = contentY;
        label("JUMP NAME", rowY);
        rowY += ROW_H;
        label("SERVER", rowY);
        rowY += ROW_H;
        label("MAP", rowY);
        rowY += ROW_H;
        label("SECTION", rowY);

        if (!errorText.isEmpty()) {
            UiDraw.text(UiDraw.fit(errorText, contentW), contentX, footerY() - 16, UiTheme.DANGER_HOVER);
        }
    }

    private void label(String text, int rowY) {
        UiDraw.text(text, contentX, rowY + 6, UiTheme.TEXT_MUTED);
    }

    // -------------------------
    // State
    // -------------------------

    private void clearSection() {
        selectedSectionLevel = 0;
        selectedSection = null;
    }

    private boolean isSectionPickAllowed() {
        if (selectedServer == null) return false;
        if (BuiltinContainers.isProtectedServer(selectedServer)) return false;
        return selectedMap != null;
    }

    private boolean isReady() {
        boolean okName = IdRules.isValidId(nameDraft.trim());
        if (BuiltinContainers.isGlobalServer(selectedServer)) {
            return okName;
        }
        return okName && selectedServer != null && selectedMap != null;
    }

    private String serverLabel() {
        return selectedServer != null ? selectedServer.getId() : "Select server";
    }

    private String mapLabel() {
        return selectedMap != null ? selectedMap.getId() : "Select map";
    }

    private String sectionLabel() {
        if (BuiltinContainers.isGlobalServer(selectedServer)) return "Not available in Global";
        if (selectedSection == null) return "None (optional)";
        return SectionChoices.label(selectedSectionLevel, selectedSection);
    }

    private List<Entry> serverEntries() {
        List<Entry> entries = new ArrayList<Entry>();
        for (final ServerProfile s : ServerService.getServers()) {
            // RestoredStrats cannot be used for creating new jumps.
            if (BuiltinContainers.isRestoredServer(s)) continue;
            entries.add(Entry.choice(String.valueOf(s.getId()), s == selectedServer, () -> chooseServer(s)));
        }
        return entries;
    }

    private List<Entry> mapEntries() {
        List<Entry> entries = new ArrayList<Entry>();
        if (selectedServer == null) return entries;

        for (final ParkourMap m : MapService.getMaps(selectedServer)) {
            entries.add(Entry.choice(String.valueOf(m.getId()), m == selectedMap, () -> chooseMap(m)));
        }
        return entries;
    }

    private List<Entry> sectionEntries() {
        return SectionChoices.entries(selectedMap, selectedSection, (level, section) -> {
            if (section == null) {
                clearSection();
                StickySectionCache.remember(selectedServer, selectedMap, 0, null);
            } else {
                selectedSectionLevel = level.intValue();
                selectedSection = section;
                StickySectionCache.remember(selectedServer, selectedMap, selectedSectionLevel, section);
            }
            errorText = "";
        });
    }

    private void chooseServer(ServerProfile server) {
        selectedServer = server;
        clearSection();
        StickySectionCache.clear();

        selectedMap = BuiltinContainers.isGlobalServer(server) ? BuiltinContainers.getGlobalMap() : null;

        SelectionState.setSelectedServer(server);
        SelectionState.setSelectedMap(selectedMap);
        SelectionState.setSelectedJump(null);
        errorText = "";
    }

    private void chooseMap(ParkourMap map) {
        selectedMap = map;
        clearSection();
        StickySectionCache.clear();

        SelectionState.setSelectedServer(selectedServer);
        SelectionState.setSelectedMap(map);
        SelectionState.setSelectedJump(null);
        errorText = "";
    }

    // -------------------------
    // Actions
    // -------------------------

    private void onNext() {
        if (!continueButton.enabled) return;

        String jumpId = nameDraft.trim();
        if (!IdRules.isValidId(jumpId)) {
            errorText = "Jump name is required.";
            return;
        }

        if (selectedServer == null) {
            errorText = "Server is required.";
            return;
        }

        if (BuiltinContainers.isRestoredServer(selectedServer)) {
            errorText = "RestoredStrats cannot be used for creating new jumps.";
            selectedServer = null;
            selectedMap = null;
            clearSection();
            StickySectionCache.clear();
            return;
        }

        if (BuiltinContainers.isGlobalServer(selectedServer)) {
            selectedMap = BuiltinContainers.getGlobalMap();
            clearSection();
            StickySectionCache.clear();
        }

        if (selectedMap == null) {
            errorText = "Map is required.";
            return;
        }

        SelectionState.setSelectedServer(selectedServer);
        SelectionState.setSelectedMap(selectedMap);

        Jump j = JumpService.getOrCreateJumpAtPlayer(selectedMap, jumpId);
        if (j == null) {
            errorText = "Failed to resolve jump.";
            return;
        }

        // If a section is selected, insert the jump at the end of it.
        if (hasValidSection()) {
            SectionLayoutService.insertJumpAtEndOfSection(selectedMap, selectedSectionLevel, selectedSection, j);

            // Keep the section remembered for repeated jump creation
            StickySectionCache.remember(selectedServer, selectedMap, selectedSectionLevel, selectedSection);
        }

        SelectionState.setSelectedJump(j);
        Minecraft.getMinecraft().displayGuiScreen(GuiStrategyEditor.create(this, after));
    }

    private boolean hasValidSection() {
        return selectedSection != null && selectedSectionLevel >= 1 && selectedSectionLevel <= ParkourMap.MAX_SECTION_LEVELS && isSectionPickAllowed();
    }

    @Override
    protected void onEnter() {
        onNext();
    }
}
