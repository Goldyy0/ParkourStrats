package me.texyle.startreminders.ui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.EnumChatFormatting;

import me.texyle.startreminders.data.Jump;
import me.texyle.startreminders.data.MapSection;
import me.texyle.startreminders.data.ParkourMap;
import me.texyle.startreminders.data.ServerProfile;
import me.texyle.startreminders.persistence.BuiltinContainers;
import me.texyle.startreminders.services.JumpService;
import me.texyle.startreminders.services.MapService;
import me.texyle.startreminders.services.ServerService;
import me.texyle.startreminders.ui.UiPopup.Entry;
import me.texyle.startreminders.utils.ChatUtils;

/** Moves a jump (with its strategies) to another server / map, optionally into a section. */
public class GuiTransferJump extends UiScreen {

    private static final int ROW_H = 28;
    private static final int LABEL_W = 84;

    private final ParkourMap fromMap;
    private final Jump jumpToMove;

    private ServerProfile selectedServer;
    private ParkourMap selectedMap;

    private int selectedSectionLevel = 0;
    private MapSection selectedSection = null;

    private String errorText = "";

    private UiDropdown mapDropdown;
    private UiDropdown sectionDropdown;
    private UiButton transferButton;

    public GuiTransferJump(GuiScreen parent, ParkourMap fromMap, Jump jumpToMove) {
        super(parent);
        this.fromMap = fromMap;
        this.jumpToMove = jumpToMove;
    }

    @Override
    protected String getTitle() {
        return "Transfer jump";
    }

    @Override
    protected int getPanelWidth() {
        return 440;
    }

    @Override
    protected int getPanelHeight() {
        return 230;
    }

    @Override
    protected void build(int x, int y, int w, int h) {
        int fieldX = x + LABEL_W;
        int fieldW = w - LABEL_W;
        int rowY = y + ROW_H;

        add(new UiDropdown(popup, "", this::serverLabel, this::serverEntries).valueColor(() -> SpecialColors.of(selectedServer)),
                fieldX, rowY, fieldW, 20);
        rowY += ROW_H;
        mapDropdown = add(new UiDropdown(popup, "", this::mapLabel, this::mapEntries).valueColor(() -> SpecialColors.of(getTargetMap())),
                fieldX, rowY, fieldW, 20);
        rowY += ROW_H;
        sectionDropdown = add(new UiDropdown(popup, "", this::sectionLabel,
                () -> SectionChoices.entries(selectedMap, selectedSection, this::pickSection)), fieldX, rowY, fieldW, 20);

        transferButton = new UiButton("Transfer", UiButton.Style.PRIMARY, this::onTransfer);
        addFooter(new UiButton("Cancel", UiButton.Style.SECONDARY, this::onBack), transferButton);
    }

    @Override
    protected void drawContent(int mouseX, int mouseY, float partialTicks) {
        boolean isProtected = BuiltinContainers.isProtectedServer(selectedServer);

        // Keep forced maps consistent
        if (isProtected) {
            selectedMap = BuiltinContainers.getForcedMap(selectedServer);
            clearSection();
        }

        mapDropdown.enabled = selectedServer != null && !isProtected;
        sectionDropdown.enabled = selectedServer != null && !isProtected && selectedMap != null;

        ParkourMap target = getTargetMap();
        transferButton.enabled = selectedServer != null && fromMap != null && target != null && target != fromMap;

        String jumpName = (jumpToMove != null && jumpToMove.getId() != null) ? jumpToMove.getId() : "<null>";
        UiDraw.text("JUMP", contentX, contentY + 6, UiTheme.TEXT_MUTED);
        UiDraw.text(UiDraw.fit(jumpName, contentW - LABEL_W), contentX + LABEL_W, contentY + 6, UiTheme.TEXT);

        UiDraw.text("SERVER", contentX, contentY + ROW_H + 6, UiTheme.TEXT_MUTED);
        UiDraw.text("MAP", contentX, contentY + ROW_H * 2 + 6, UiTheme.TEXT_MUTED);
        UiDraw.text("SECTION", contentX, contentY + ROW_H * 3 + 6, UiTheme.TEXT_MUTED);

        if (!errorText.isEmpty()) {
            UiDraw.text(UiDraw.fit(errorText, contentW), contentX, footerY() - 16, UiTheme.DANGER_HOVER);
        }
    }

    // -------------------------
    // State
    // -------------------------

    private void clearSection() {
        selectedSectionLevel = 0;
        selectedSection = null;
    }

    /** Global and RestoredStrats have one fixed map each; for other servers this is the picked map. */
    private ParkourMap getTargetMap() {
        if (BuiltinContainers.isProtectedServer(selectedServer)) {
            return BuiltinContainers.getForcedMap(selectedServer);
        }
        return selectedMap;
    }

    private String serverLabel() {
        return selectedServer != null ? selectedServer.getId() : "Select server";
    }

    private String mapLabel() {
        ParkourMap target = getTargetMap();
        return target != null ? target.getId() : "Select map";
    }

    private String sectionLabel() {
        if (BuiltinContainers.isProtectedServer(selectedServer)) return "Not available";
        if (selectedSection == null) return "None (optional)";
        return SectionChoices.label(selectedSectionLevel, selectedSection);
    }

    private List<Entry> serverEntries() {
        List<Entry> entries = new ArrayList<Entry>();
        for (final ServerProfile s : ServerService.getServers()) {
            entries.add(Entry.choice(String.valueOf(s.getId()), s == selectedServer, () -> {
                selectedServer = s;
                clearSection();
                // Global / Restored force their own map; otherwise the map must be picked again
                selectedMap = BuiltinContainers.getForcedMap(s);
                errorText = "";
            }).colored(SpecialColors.of(s)));
        }
        return entries;
    }

    private List<Entry> mapEntries() {
        List<Entry> entries = new ArrayList<Entry>();
        if (selectedServer == null) return entries;

        for (final ParkourMap m : MapService.getMaps(selectedServer)) {
            entries.add(Entry.choice(String.valueOf(m.getId()), m == selectedMap, () -> {
                selectedMap = m;
                clearSection();
                errorText = "";
            }).colored(SpecialColors.of(m)));
        }
        return entries;
    }

    private void pickSection(Integer level, MapSection section) {
        selectedSectionLevel = level.intValue();
        selectedSection = section;
        errorText = "";
    }

    // -------------------------
    // Action
    // -------------------------

    private void onTransfer() {
        if (jumpToMove == null || fromMap == null) {
            errorText = "Invalid source jump.";
            return;
        }

        if (selectedServer == null) {
            errorText = "Select a server first.";
            return;
        }

        boolean isProtected = BuiltinContainers.isProtectedServer(selectedServer);
        ParkourMap targetMap = getTargetMap();

        if (targetMap == null) {
            errorText = "Select a map first.";
            return;
        }

        if (targetMap == fromMap) {
            errorText = "Target map must be different.";
            return;
        }

        int targetSectionLevel = isProtected ? 0 : selectedSectionLevel;
        MapSection targetSection = isProtected ? null : selectedSection;

        boolean ok = JumpService.transferJump(fromMap, jumpToMove, selectedServer, targetMap, targetSectionLevel, targetSection);
        if (!ok) {
            errorText = "Transfer failed.";
            return;
        }

        String target = EnumChatFormatting.YELLOW + selectedServer.getId()
                + EnumChatFormatting.AQUA + " / " + EnumChatFormatting.YELLOW + targetMap.getId();

        if (!isProtected && targetSection != null) {
            target += EnumChatFormatting.AQUA + " / " + EnumChatFormatting.YELLOW
                    + SectionChoices.label(targetSectionLevel, targetSection);
        }

        ChatUtils.info("Jump transferred to " + target);

        // transferJump selected the jump in its new place; the parent picks that selection up.
        Minecraft.getMinecraft().displayGuiScreen(parent);
    }

    @Override
    protected void onEnter() {
        if (transferButton.enabled) {
            onTransfer();
        }
    }
}
