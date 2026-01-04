package me.texyle.startreminders.gui;

import java.io.IOException;

import me.texyle.startreminders.data.Jump;
import me.texyle.startreminders.data.ParkourMap;
import me.texyle.startreminders.data.ServerProfile;
import me.texyle.startreminders.gui.hierarchy.GuiMapList;
import me.texyle.startreminders.gui.hierarchy.GuiServerList;
import me.texyle.startreminders.reminders.Reminder;
import me.texyle.startreminders.reminders.ReminderManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.EnumChatFormatting;
import java.util.ArrayList;

public class GuiCreateJumpContext extends GuiScreen {

    private static final int FIELD_WIDTH = 220;

    private static final int COLOR_TEXT = 0xFFFFFF;
    private static final int COLOR_INVALID_OVERLAY = 0x55FF0000;

    private static final String PLACEHOLDER_TEXT = "PLACEHOLDER";

    private final GuiScreen parent;

    private GuiButton backButton;
    private GuiButton nextButton;
    private GuiButton createPlaceholderButton;
    private GuiButton pickServerButton;
    private GuiButton pickMapButton;

    private GuiTextField jumpNameField;

    private ServerProfile selectedServer;
    private ParkourMap selectedMap;

    private String errorText = "";

    public GuiCreateJumpContext() {
        this(null);
    }

    public GuiCreateJumpContext(GuiScreen parent) {
        this.parent = parent;

        this.selectedServer = ReminderManager.getSelectedServer();
        this.selectedMap = ReminderManager.getSelectedMap();

        if (this.selectedServer == null) {
            this.selectedMap = null;
        }

        // Disallow RestoredStrats as context for creating new jumps
        if (this.selectedServer != null && ReminderManager.isRestoredServer(this.selectedServer)) {
            this.selectedServer = null;
            this.selectedMap = null;
        }

        // If server is Global, force global map
        if (this.selectedServer != null && ReminderManager.isGlobalServer(this.selectedServer)) {
            this.selectedMap = ReminderManager.getGlobalMap();
        }
    }

    @Override
    public void initGui() {
        super.initGui();

        int yBase = this.height / 3;

        backButton = new GuiButton(20, 8, 8, 60, 20, "Back");
        this.buttonList.add(backButton);

        nextButton = new GuiButton(5, 0, 0, "Next");
        nextButton.xPosition = (this.width - nextButton.width) / 2;
        nextButton.yPosition = this.height - 28;
        this.buttonList.add(nextButton);

        pickServerButton = new GuiButton(6, 0, 0, "Select server");
        pickServerButton.width = 110;
        pickServerButton.xPosition = (this.width - FIELD_WIDTH) / 2;
        pickServerButton.yPosition = yBase + 34;
        this.buttonList.add(pickServerButton);

        pickMapButton = new GuiButton(7, 0, 0, "Select map");
        pickMapButton.width = 110;
        pickMapButton.xPosition = (this.width - FIELD_WIDTH) / 2 + 110;
        pickMapButton.yPosition = yBase + 34;
        this.buttonList.add(pickMapButton);

        // Create placeholder button – centered under "Map:"
        createPlaceholderButton = new GuiButton(8, 0, 0, "Create placeholder");
        createPlaceholderButton.width = 160;
        createPlaceholderButton.xPosition = (this.width - createPlaceholderButton.width) / 2;
        createPlaceholderButton.yPosition = yBase + 110;
        this.buttonList.add(createPlaceholderButton);

        jumpNameField = new GuiTextField(0, this.fontRendererObj, getFieldX(), yBase, FIELD_WIDTH, 20);
        jumpNameField.setFocused(true);
        jumpNameField.setMaxStringLength(48);

        updateButtonStates();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);

        String title = "Create strategy: choose Jump / Server / Map";
        this.drawCenteredString(this.fontRendererObj, title, this.width / 2, 14, COLOR_TEXT);

        int yBase = this.height / 3;

        this.fontRendererObj.drawString("Jump Name:", getLabelX(), yBase + 6, COLOR_TEXT, true);
        this.fontRendererObj.drawString("Server:", getLabelX(), yBase + 62, COLOR_TEXT, true);
        this.fontRendererObj.drawString("Map:", getLabelX(), yBase + 86, COLOR_TEXT, true);

        String serverText = (selectedServer != null && selectedServer.getId() != null)
                ? selectedServer.getId()
                : "<not selected>";
        String mapText = (selectedMap != null && selectedMap.getId() != null)
                ? selectedMap.getId()
                : "<not selected>";

        this.fontRendererObj.drawString(EnumChatFormatting.YELLOW + serverText,
                getFieldX(), yBase + 62, COLOR_TEXT, true);
        this.fontRendererObj.drawString(EnumChatFormatting.YELLOW + mapText,
                getFieldX(), yBase + 86, COLOR_TEXT, true);

        if (errorText != null && !errorText.trim().isEmpty()) {
            this.drawCenteredString(this.fontRendererObj,
                    EnumChatFormatting.RED + errorText,
                    this.width / 2,
                    yBase + 150,
                    COLOR_TEXT);
        }

        drawValidationOverlays();
        jumpNameField.drawTextBox();
    }

    private void drawValidationOverlays() {
        if (!hasValidId(jumpNameField.getText())) {
            drawFieldOverlay(jumpNameField);
        }
    }

    private void drawFieldOverlay(GuiTextField field) {
        if (field == null) {
            return;
        }
        int left = field.xPosition - 1;
        int top = field.yPosition - 1;
        int right = field.xPosition + field.width + 1;
        int bottom = field.yPosition + field.height + 1;
        drawRect(left, top, right, bottom, COLOR_INVALID_OVERLAY);
    }

    private int getFieldX() {
        return (this.width - FIELD_WIDTH) / 2;
    }

    private int getLabelX() {
        return getFieldX() - 70;
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        jumpNameField.textboxKeyTyped(typedChar, keyCode);

        if (keyCode == 28) { // Enter
            if (nextButton != null && nextButton.enabled) {
                actionPerformed(nextButton);
                return;
            }
        }

        if (keyCode == 1) { // Esc
            goBack();
            return;
        }

        super.keyTyped(typedChar, keyCode);
        updateButtonStates();
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (jumpNameField != null) {
            jumpNameField.updateCursorCounter();
        }
        updateButtonStates();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (jumpNameField != null) {
            jumpNameField.mouseClicked(mouseX, mouseY, mouseButton);
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private static int getPlayerBlockX(EntityPlayerSP p) {
        return (int) Math.floor(p.posX);
    }

    private static int getPlayerBlockYPlusOne(EntityPlayerSP p) {
        return (int) Math.floor(p.posY) + 2;
    }

    private static int getPlayerBlockZ(EntityPlayerSP p) {
        return (int) Math.floor(p.posZ);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button == null) {
            return;
        }

        if (button.id == 20) {
            goBack();
            return;
        }

        if (button.id == 8) {
            createPlaceholderJump();
            return;
        }

        // ===== EXISTING LOGIC BELOW (UNCHANGED) =====

        if (button.id == 6) {
            Minecraft.getMinecraft().displayGuiScreen(
                    new GuiServerList(this, server -> {
                        if (server != null && ReminderManager.isRestoredServer(server)) {
                            selectedServer = null;
                            selectedMap = null;

                            ReminderManager.setSelectedServer(null);
                            ReminderManager.setSelectedMap(null);
                            ReminderManager.setSelectedJump(null);

                            errorText = "RestoredStrats cannot be used for creating new jumps.";
                            updateButtonStates();
                            return;
                        }

                        selectedServer = server;

                        if (selectedServer != null && ReminderManager.isGlobalServer(selectedServer)) {
                            selectedMap = ReminderManager.getGlobalMap();
                        } else {
                            selectedMap = null;
                        }

                        ReminderManager.setSelectedServer(server);
                        ReminderManager.setSelectedMap(selectedMap);
                        ReminderManager.setSelectedJump(null);

                        errorText = "";
                        updateButtonStates();
                    }, false)
            );
            return;
        }

        if (button.id == 7) {
            if (selectedServer == null) {
                errorText = "Select a server first.";
                return;
            }

            if (ReminderManager.isGlobalServer(selectedServer)) {
                selectedMap = ReminderManager.getGlobalMap();
                ReminderManager.setSelectedMap(selectedMap);
                errorText = "";
                updateButtonStates();
                return;
            }

            Minecraft.getMinecraft().displayGuiScreen(
                    new GuiMapList(this, selectedServer, map -> {
                        selectedMap = map;

                        ReminderManager.setSelectedServer(selectedServer);
                        ReminderManager.setSelectedMap(map);
                        ReminderManager.setSelectedJump(null);

                        errorText = "";
                        updateButtonStates();
                    })
            );
            return;
        }

        if (button.id == 5) {
            if (!nextButton.enabled) {
                return;
            }

            String jumpId = safeId(jumpNameField.getText());
            if (!hasValidId(jumpId)) {
                errorText = "Jump Name is required.";
                updateButtonStates();
                return;
            }

            if (selectedServer == null) {
                errorText = "Server is required.";
                updateButtonStates();
                return;
            }

            if (ReminderManager.isRestoredServer(selectedServer)) {
                errorText = "RestoredStrats cannot be used for creating new jumps.";
                selectedServer = null;
                selectedMap = null;
                updateButtonStates();
                return;
            }

            if (ReminderManager.isGlobalServer(selectedServer)) {
                selectedMap = ReminderManager.getGlobalMap();
            }

            if (selectedMap == null) {
                errorText = "Map is required.";
                updateButtonStates();
                return;
            }

            ReminderManager.setSelectedServer(selectedServer);
            ReminderManager.setSelectedMap(selectedMap);

            EntityPlayerSP p = Minecraft.getMinecraft().thePlayer;

            int x = 0;
            int y = 0;
            int z = 0;

            if (p != null) {
                x = getPlayerBlockX(p);
                y = getPlayerBlockYPlusOne(p);
                z = getPlayerBlockZ(p);
            }

            Jump j = ReminderManager.getOrCreateJumpByNameAndCoords(selectedMap, jumpId, x, y, z);

            if (j == null) {
                errorText = "Failed to resolve jump.";
                updateButtonStates();
                return;
            }

            ReminderManager.setSelectedJump(j);
            Minecraft.getMinecraft().displayGuiScreen(new GuiCreateReminder(this));
            return;
        }

        super.actionPerformed(button);
    }

    private void createPlaceholderJump() {
        String jumpId = safeId(jumpNameField.getText());
        if (!hasValidId(jumpId) || selectedServer == null || selectedMap == null) {
            return;
        }

        EntityPlayerSP p = Minecraft.getMinecraft().thePlayer;

        int x = 0;
        int y = 0;
        int z = 0;

        if (p != null) {
            x = getPlayerBlockX(p);
            y = getPlayerBlockYPlusOne(p);
            z = getPlayerBlockZ(p);
        }

        Jump j = ReminderManager.getOrCreateJumpByNameAndCoords(selectedMap, jumpId, x, y, z);
        if (j == null) {
            errorText = "Failed to create placeholder jump.";
            return;
        }

        ArrayList<String> lines = new ArrayList<String>(8);

        // New format (8): [Position, Facing, Setup, Strategy, Strafe, Turn, Author, Tips]
        for (int i = 0; i < 8; i++) {
            lines.add("");
        }

        // Force placeholder into Setup only (index 2)
        lines.set(2, PLACEHOLDER_TEXT);

        Reminder r = new Reminder(lines);
        j.getReminders().add(r);

        ReminderManager.saveToFile();
        goBack();
    }

    private void goBack() {
        if (parent != null) {
            Minecraft.getMinecraft().displayGuiScreen(parent);
        } else {
            Minecraft.getMinecraft().displayGuiScreen(null);
        }
    }

    private void updateButtonStates() {
        boolean serverSelected = (selectedServer != null);
        boolean isGlobal = (selectedServer != null && ReminderManager.isGlobalServer(selectedServer));

        if (pickMapButton != null) {
            pickMapButton.enabled = serverSelected && !isGlobal;
        }

        boolean okName = hasValidId(safeId(jumpNameField != null ? jumpNameField.getText() : ""));

        boolean ok;
        if (isGlobal) {
            ok = okName && serverSelected;
        } else {
            ok = okName && serverSelected && selectedMap != null;
        }

        if (nextButton != null) {
            nextButton.enabled = ok;
        }

        if (createPlaceholderButton != null) {
            createPlaceholderButton.enabled = ok;
        }
    }

    private static boolean hasValidId(String id) {
        if (id == null) return false;
        String trimmed = id.trim();
        if (trimmed.isEmpty()) return false;
        return trimmed.length() <= 48;
    }

    private static String safeId(String s) {
        return s != null ? s.trim() : "";
    }
}