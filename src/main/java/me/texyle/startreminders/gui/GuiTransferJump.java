package me.texyle.startreminders.gui;

import java.io.IOException;

import me.texyle.startreminders.data.Jump;
import me.texyle.startreminders.data.ParkourMap;
import me.texyle.startreminders.data.ServerProfile;
import me.texyle.startreminders.gui.hierarchy.GuiMapList;
import me.texyle.startreminders.gui.hierarchy.GuiServerList;
import me.texyle.startreminders.reminders.ReminderManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

public class GuiTransferJump extends GuiScreen {

    private static final int FIELD_WIDTH = 220;

    private static final int COLOR_TEXT = 0xFFFFFF;

    private final GuiScreen parent;
    private final ParkourMap fromMap;
    private final Jump jumpToMove;

    private GuiButton backButton;
    private GuiButton transferButton;
    private GuiButton pickServerButton;
    private GuiButton pickMapButton;

    private ServerProfile selectedServer;
    private ParkourMap selectedMap;

    private String errorText = "";

    public GuiTransferJump(GuiScreen parent, ParkourMap fromMap, Jump jumpToMove) {
        this.parent = parent;
        this.fromMap = fromMap;
        this.jumpToMove = jumpToMove;
    }

    @Override
    public void initGui() {
        super.initGui();

        int yBase = this.height / 3;

        backButton = new GuiButton(20, 8, 8, 60, 20, "Back");
        this.buttonList.add(backButton);

        transferButton = new GuiButton(5, 0, 0, "Transfer");
        transferButton.xPosition = (this.width - transferButton.width) / 2;
        transferButton.yPosition = this.height - 28;
        this.buttonList.add(transferButton);

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
                    yBase + 130,
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

        if (button.id == 20) {
            goBack();
            return;
        }

        if (button.id == 6) {
            // Picker mode with RestoredStrats allowed (transfer use-case)
            Minecraft.getMinecraft().displayGuiScreen(
                    new GuiServerList(this, server -> {
                        selectedServer = server;

                        // Global -> force global map
                        if (selectedServer != null && ReminderManager.isGlobalServer(selectedServer)) {
                            selectedMap = ReminderManager.getGlobalMap();
                        } else if (selectedServer != null && ReminderManager.isRestoredServer(selectedServer)) {
                            selectedMap = ReminderManager.getRestoredMap();
                        } else {
                            selectedMap = null;
                        }

                        errorText = "";
                        updateButtonStates();
                    }, false)
            );
            return;
        }

        if (button.id == 7) {
            if (selectedServer == null) {
                errorText = "Select a server first.";
                updateButtonStates();
                return;
            }

            if (ReminderManager.isGlobalServer(selectedServer)) {
                selectedMap = ReminderManager.getGlobalMap();
                errorText = "";
                updateButtonStates();
                return;
            }

            if (ReminderManager.isRestoredServer(selectedServer)) {
                selectedMap = ReminderManager.getRestoredMap();
                errorText = "";
                updateButtonStates();
                return;
            }

            Minecraft.getMinecraft().displayGuiScreen(
                    new GuiMapList(this, selectedServer, map -> {
                        selectedMap = map;
                        errorText = "";
                        updateButtonStates();
                    })
            );
            return;
        }

        if (button.id == 5) {
            if (jumpToMove == null || fromMap == null) {
                errorText = "Invalid source jump.";
                updateButtonStates();
                return;
            }

            if (selectedServer == null || selectedMap == null) {
                errorText = "Select server and map first.";
                updateButtonStates();
                return;
            }

            // Disallow transferring to the same map (no-op)
            if (selectedMap == fromMap) {
                errorText = "Target map must be different.";
                updateButtonStates();
                return;
            }

            boolean ok = ReminderManager.transferJump(fromMap, jumpToMove, selectedServer, selectedMap);
            if (!ok) {
                errorText = "Transfer failed.";
                updateButtonStates();
                return;
            }

            sendClientChat(EnumChatFormatting.DARK_AQUA + "[ParkourStrats] " + EnumChatFormatting.AQUA
                    + "Jump transferred to " + EnumChatFormatting.YELLOW + selectedServer.getId()
                    + EnumChatFormatting.AQUA + " / " + EnumChatFormatting.YELLOW + selectedMap.getId());

            // Open the target list so the player immediately sees the moved jump
            Minecraft.getMinecraft().displayGuiScreen(new me.texyle.startreminders.gui.hierarchy.GuiJumpList(parent, selectedMap));
            return;
        }

        super.actionPerformed(button);
    }

    private void updateButtonStates() {
        boolean serverSelected = (selectedServer != null);
        boolean isGlobal = (selectedServer != null && ReminderManager.isGlobalServer(selectedServer));
        boolean isRestored = (selectedServer != null && ReminderManager.isRestoredServer(selectedServer));

        if (pickMapButton != null) {
            // For Global/Restored, map is forced, so map picking is disabled
            pickMapButton.enabled = serverSelected && !isGlobal && !isRestored;
        }

        boolean ok = (selectedServer != null && selectedMap != null && fromMap != null && selectedMap != fromMap);

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

    private void sendClientChat(String msg) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null || msg == null) return;
        mc.thePlayer.addChatMessage(new ChatComponentText(msg));
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