package me.texyle.startreminders.gui;

import java.io.IOException;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.EnumChatFormatting;

import me.texyle.startreminders.data.Jump;
import me.texyle.startreminders.data.MapSection;
import me.texyle.startreminders.data.ParkourMap;
import me.texyle.startreminders.data.ServerProfile;
import me.texyle.startreminders.gui.hierarchy.GuiJumpList;
import me.texyle.startreminders.gui.hierarchy.GuiMapList;
import me.texyle.startreminders.gui.hierarchy.GuiPickSectionPreview;
import me.texyle.startreminders.gui.hierarchy.GuiServerList;
import me.texyle.startreminders.persistence.BuiltinContainers;
import me.texyle.startreminders.services.JumpService;
import me.texyle.startreminders.utils.ChatUtils;

public class GuiTransferJump extends GuiScreen {

    private static final int FIELD_WIDTH = 220;
    private static final int COLOR_TEXT = 0xFFFFFF;

    // Button IDs
    private static final int BTN_BACK = 20;
    private static final int BTN_TRANSFER = 5;
    private static final int BTN_PICK_SERVER = 6;
    private static final int BTN_PICK_MAP = 7;
    private static final int BTN_PICK_SECTION = 8;

    private final GuiScreen parent;
    private final ParkourMap fromMap;
    private final Jump jumpToMove;

    private GuiButton backButton;
    private GuiButton transferButton;
    private GuiButton pickServerButton;
    private GuiButton pickMapButton;
    private GuiButton pickSectionButton;

    private ServerProfile selectedServer;
    private ParkourMap selectedMap;

    // Optional target section (0/null = none)
    private int selectedSectionLevelOneBased = 0;
    private MapSection selectedSection = null;

    private String errorText = "";

    public GuiTransferJump(GuiScreen parent, ParkourMap fromMap, Jump jumpToMove) {
        this.parent = parent;
        this.fromMap = fromMap;
        this.jumpToMove = jumpToMove;
    }

    @Override
    public void initGui() {
        super.initGui();
        this.buttonList.clear();

        int yBase = this.height / 3;

        backButton = new GuiButton(BTN_BACK, 8, 8, 60, 20, "Back");
        this.buttonList.add(backButton);

        transferButton = new GuiButton(BTN_TRANSFER, 0, 0, "Transfer");
        transferButton.xPosition = (this.width - transferButton.width) / 2;
        transferButton.yPosition = this.height - 28;
        this.buttonList.add(transferButton);

        // ------------------------------------------------------------
        // Buttons row: move them UP (per your request)
        // ------------------------------------------------------------
        int btnRow1Y = yBase + 18;
        int btnRow2Y = yBase + 44;

        int fieldX = getFieldX();

        pickServerButton = new GuiButton(BTN_PICK_SERVER, fieldX, btnRow1Y, 110, 20, "Select server");
        this.buttonList.add(pickServerButton);

        pickMapButton = new GuiButton(BTN_PICK_MAP, fieldX + 110, btnRow1Y, 110, 20, "Select map");
        this.buttonList.add(pickMapButton);

        pickSectionButton = new GuiButton(BTN_PICK_SECTION, fieldX, btnRow2Y, FIELD_WIDTH, 20, "Select section");
        this.buttonList.add(pickSectionButton);

        updateButtonStates();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);

        int yBase = this.height / 3;

        String title = "Transfer jump to another list";
        this.drawCenteredString(this.fontRendererObj, title, this.width / 2, 14, COLOR_TEXT);

        String jumpLabel = (jumpToMove != null && jumpToMove.getId() != null) ? jumpToMove.getId() : "<null>";
        this.drawCenteredString(this.fontRendererObj,
                EnumChatFormatting.AQUA + "Jump: " + EnumChatFormatting.YELLOW + jumpLabel,
                this.width / 2, yBase - 10, COLOR_TEXT);

        boolean isGlobal = (selectedServer != null && BuiltinContainers.isGlobalServer(selectedServer));
        boolean isRestored = (selectedServer != null && BuiltinContainers.isRestoredServer(selectedServer));

        // ------------------------------------------------------------
        // Text fields BELOW the buttons (per your request)
        // ------------------------------------------------------------
        int textTopY = yBase + 78; // safely below button rows
        int lineH = 18;

        String serverText = (selectedServer != null && selectedServer.getId() != null)
                ? selectedServer.getId()
                : "<not selected>";

        String mapText;
        if (isGlobal) {
            ParkourMap gm = BuiltinContainers.getGlobalMap();
            mapText = (gm != null && gm.getId() != null) ? gm.getId() : "<Global>";
        } else if (selectedMap != null && selectedMap.getId() != null) {
            mapText = selectedMap.getId();
        } else {
            mapText = "<not selected>";
        }

        String sectionText;
        if (isGlobal || isRestored) {
            sectionText = "<not available>";
        } else if (selectedSection != null) {
            String sn = selectedSection.getName() != null ? selectedSection.getName() : "<unnamed>";
            sectionText = "L" + selectedSectionLevelOneBased + ": " + sn;
        } else {
            sectionText = "<not selected>";
        }

        this.fontRendererObj.drawString("Server:", getLabelX(), textTopY + (lineH * 0), COLOR_TEXT, true);
        this.fontRendererObj.drawString("Map:", getLabelX(), textTopY + (lineH * 1), COLOR_TEXT, true);
        this.fontRendererObj.drawString("Section:", getLabelX(), textTopY + (lineH * 2), COLOR_TEXT, true);

        this.fontRendererObj.drawString(EnumChatFormatting.YELLOW + serverText, getFieldX(), textTopY + (lineH * 0), COLOR_TEXT, true);
        this.fontRendererObj.drawString(EnumChatFormatting.YELLOW + mapText, getFieldX(), textTopY + (lineH * 1), COLOR_TEXT, true);
        this.fontRendererObj.drawString(EnumChatFormatting.YELLOW + sectionText, getFieldX(), textTopY + (lineH * 2), COLOR_TEXT, true);

        if (errorText != null && !errorText.trim().isEmpty()) {
            this.drawCenteredString(this.fontRendererObj,
                    EnumChatFormatting.RED + errorText,
                    this.width / 2,
                    textTopY + (lineH * 4),
                    COLOR_TEXT);
        }
    }

    private int getFieldX() {
        return (this.width - FIELD_WIDTH) / 2;
    }

    private int getLabelX() {
        return getFieldX() - 70;
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button == null) return;

        switch (button.id) {
            case BTN_BACK:
                goBack();
                return;
            case BTN_PICK_SERVER:
                onPickServer();
                return;
            case BTN_PICK_MAP:
                onPickMap();
                return;
            case BTN_PICK_SECTION:
                onPickSection();
                return;
            case BTN_TRANSFER:
                onTransfer();
                return;
            default:
                super.actionPerformed(button);
        }
    }

    private void clearSection() {
        selectedSectionLevelOneBased = 0;
        selectedSection = null;
    }

    /** Reports a problem under the form and refreshes the buttons. */
    private void fail(String message) {
        errorText = message;
        updateButtonStates();
    }

    /** Global and RestoredStrats have one fixed map each; for other servers this is the picked map. */
    private ParkourMap getTargetMap() {
        if (BuiltinContainers.isProtectedServer(selectedServer)) {
            return BuiltinContainers.getForcedMap(selectedServer);
        }
        return selectedMap;
    }

    private void onPickServer() {
        Minecraft.getMinecraft().displayGuiScreen(
                new GuiServerList(this, server -> {
                    selectedServer = server;

                    // Reset section on server change
                    clearSection();

                    // Global / Restored force their own map; otherwise the map must be picked again
                    selectedMap = BuiltinContainers.getForcedMap(selectedServer);

                    errorText = "";
                    updateButtonStates();
                }, false)
        );
    }

    private void onPickMap() {
        if (selectedServer == null) {
            fail("Select a server first.");
            return;
        }

        if (BuiltinContainers.isProtectedServer(selectedServer)) {
            // Map is forced; no picker
            selectedMap = BuiltinContainers.getForcedMap(selectedServer);
            clearSection();

            errorText = "";
            updateButtonStates();
            return;
        }

        Minecraft.getMinecraft().displayGuiScreen(
                new GuiMapList(this, selectedServer, map -> {
                    selectedMap = map;

                    // Reset section on map change
                    clearSection();

                    errorText = "";
                    updateButtonStates();
                })
        );
    }

    private void onPickSection() {
        if (selectedServer == null) {
            fail("Select a server first.");
            return;
        }

        if (BuiltinContainers.isGlobalServer(selectedServer)) {
            clearSection();
            fail("Global has no sections.");
            return;
        }

        if (BuiltinContainers.isRestoredServer(selectedServer)) {
            clearSection();
            fail("Restored has no sections.");
            return;
        }

        if (selectedMap == null) {
            fail("Select a map first.");
            return;
        }

        Minecraft.getMinecraft().displayGuiScreen(
                new GuiPickSectionPreview(this, selectedMap, new GuiPickSectionPreview.ISectionPickHandler() {
                    @Override
                    public void onPick(int levelOneBased, MapSection section) {
                        selectedSectionLevelOneBased = levelOneBased;
                        selectedSection = section;

                        errorText = "";
                        updateButtonStates();
                    }
                })
        );
    }

    private void onTransfer() {
        if (jumpToMove == null || fromMap == null) {
            fail("Invalid source jump.");
            return;
        }

        if (selectedServer == null) {
            fail("Select a server first.");
            return;
        }

        boolean isProtected = BuiltinContainers.isProtectedServer(selectedServer);

        ParkourMap targetMap = getTargetMap();

        if (targetMap == null) {
            fail("Select a map first.");
            return;
        }

        if (targetMap == fromMap) {
            fail("Target map must be different.");
            return;
        }

        int targetSectionLevel = isProtected ? 0 : selectedSectionLevelOneBased;
        MapSection targetSection = isProtected ? null : selectedSection;

        boolean ok = JumpService.transferJump(
                fromMap,
                jumpToMove,
                selectedServer,
                targetMap,
                targetSectionLevel,
                targetSection
        );

        if (!ok) {
            fail("Transfer failed.");
            return;
        }

        String target = EnumChatFormatting.YELLOW + selectedServer.getId()
                + EnumChatFormatting.AQUA + " / " + EnumChatFormatting.YELLOW + targetMap.getId();

        if (!isProtected && targetSection != null) {
            String sn = targetSection.getName() != null ? targetSection.getName() : "<unnamed>";
            target += EnumChatFormatting.AQUA + " / " + EnumChatFormatting.YELLOW + "L" + targetSectionLevel + ": " + sn;
        }

        ChatUtils.info("Jump transferred to " + target);

        Minecraft.getMinecraft().displayGuiScreen(new GuiJumpList(parent, targetMap, jumpToMove));
    }

    private void updateButtonStates() {
        boolean serverSelected = (selectedServer != null);
        boolean isGlobal = BuiltinContainers.isGlobalServer(selectedServer);
        boolean isProtected = BuiltinContainers.isProtectedServer(selectedServer);

        // Keep forced maps consistent
        if (isProtected) {
            selectedMap = BuiltinContainers.getForcedMap(selectedServer);
            clearSection();
        }

        if (pickMapButton != null) {
            pickMapButton.enabled = serverSelected && !isProtected;
        }

        if (pickSectionButton != null) {
            pickSectionButton.enabled = (serverSelected && !isProtected && selectedMap != null);

            if (isProtected) {
                pickSectionButton.displayString = "Select section (N/A)";
            } else if (selectedMap == null) {
                pickSectionButton.displayString = "Select section";
            } else if (selectedSection != null) {
                String sn = selectedSection.getName() != null ? selectedSection.getName() : "<unnamed>";
                pickSectionButton.displayString = "Section: L" + selectedSectionLevelOneBased + " " + sn;
            } else {
                pickSectionButton.displayString = "Select section";
            }
        }

        ParkourMap targetMap = getTargetMap();

        boolean ok = (serverSelected && fromMap != null && targetMap != null && targetMap != fromMap);

        if (transferButton != null) {
            transferButton.enabled = ok;
        }
    }

    private void goBack() {
        if (parent != null) {
            Minecraft.getMinecraft().displayGuiScreen(parent);
        } else {
            Minecraft.getMinecraft().displayGuiScreen(null);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1) { // Esc
            goBack();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }
}