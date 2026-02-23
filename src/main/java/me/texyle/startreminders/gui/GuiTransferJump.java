package me.texyle.startreminders.gui;

import java.io.IOException;

import me.texyle.startreminders.data.Jump;
import me.texyle.startreminders.data.MapSection;
import me.texyle.startreminders.data.ParkourMap;
import me.texyle.startreminders.data.ServerProfile;
import me.texyle.startreminders.gui.hierarchy.GuiJumpList;
import me.texyle.startreminders.gui.hierarchy.GuiMapList;
import me.texyle.startreminders.gui.hierarchy.GuiPickSectionPreview;
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

        backButton = new GuiButton(20, 8, 8, 60, 20, "Back");
        this.buttonList.add(backButton);

        transferButton = new GuiButton(5, 0, 0, "Transfer");
        transferButton.xPosition = (this.width - transferButton.width) / 2;
        transferButton.yPosition = this.height - 28;
        this.buttonList.add(transferButton);

        // ------------------------------------------------------------
        // Buttons row: move them UP (per your request)
        // ------------------------------------------------------------
        int btnRow1Y = yBase + 18;
        int btnRow2Y = yBase + 44;

        int fieldX = getFieldX();

        pickServerButton = new GuiButton(6, fieldX, btnRow1Y, 110, 20, "Select server");
        this.buttonList.add(pickServerButton);

        pickMapButton = new GuiButton(7, fieldX + 110, btnRow1Y, 110, 20, "Select map");
        this.buttonList.add(pickMapButton);

        pickSectionButton = new GuiButton(8, fieldX, btnRow2Y, FIELD_WIDTH, 20, "Select section");
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

        boolean isGlobal = (selectedServer != null && ReminderManager.isGlobalServer(selectedServer));
        boolean isRestored = (selectedServer != null && ReminderManager.isRestoredServer(selectedServer));

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
            ParkourMap gm = ReminderManager.getGlobalMap();
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

        if (button.id == 20) {
            goBack();
            return;
        }

        if (button.id == 6) {
            Minecraft.getMinecraft().displayGuiScreen(
                    new GuiServerList(this, server -> {
                        selectedServer = server;

                        boolean isGlobal = (selectedServer != null && ReminderManager.isGlobalServer(selectedServer));
                        boolean isRestored = (selectedServer != null && ReminderManager.isRestoredServer(selectedServer));

                        // Reset section on server change
                        selectedSectionLevelOneBased = 0;
                        selectedSection = null;

                        if (isGlobal) {
                            // Global is allowed: force global map, no sections
                            selectedMap = ReminderManager.getGlobalMap();
                        } else if (isRestored) {
                            // Restored forced map
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
                // Global map is forced; no picker
                selectedMap = ReminderManager.getGlobalMap();
                selectedSectionLevelOneBased = 0;
                selectedSection = null;

                errorText = "";
                updateButtonStates();
                return;
            }

            if (ReminderManager.isRestoredServer(selectedServer)) {
                selectedMap = ReminderManager.getRestoredMap();
                selectedSectionLevelOneBased = 0;
                selectedSection = null;

                errorText = "";
                updateButtonStates();
                return;
            }

            Minecraft.getMinecraft().displayGuiScreen(
                    new GuiMapList(this, selectedServer, map -> {
                        selectedMap = map;

                        // Reset section on map change
                        selectedSectionLevelOneBased = 0;
                        selectedSection = null;

                        errorText = "";
                        updateButtonStates();
                    })
            );
            return;
        }

        if (button.id == 8) {
            if (selectedServer == null) {
                errorText = "Select a server first.";
                updateButtonStates();
                return;
            }

            if (ReminderManager.isGlobalServer(selectedServer)) {
                // Global has no sections
                selectedSectionLevelOneBased = 0;
                selectedSection = null;

                errorText = "Global has no sections.";
                updateButtonStates();
                return;
            }

            if (ReminderManager.isRestoredServer(selectedServer)) {
                selectedSectionLevelOneBased = 0;
                selectedSection = null;

                errorText = "Restored has no sections.";
                updateButtonStates();
                return;
            }

            if (selectedMap == null) {
                errorText = "Select a map first.";
                updateButtonStates();
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
            return;
        }

        if (button.id == 5) {
            if (jumpToMove == null || fromMap == null) {
                errorText = "Invalid source jump.";
                updateButtonStates();
                return;
            }

            if (selectedServer == null) {
                errorText = "Select a server first.";
                updateButtonStates();
                return;
            }

            boolean isGlobal = ReminderManager.isGlobalServer(selectedServer);
            boolean isRestored = ReminderManager.isRestoredServer(selectedServer);

            ParkourMap targetMap;
            if (isGlobal) {
                targetMap = ReminderManager.getGlobalMap();
            } else if (isRestored) {
                targetMap = ReminderManager.getRestoredMap();
            } else {
                targetMap = selectedMap;
            }

            if (targetMap == null) {
                errorText = "Select a map first.";
                updateButtonStates();
                return;
            }

            if (targetMap == fromMap) {
                errorText = "Target map must be different.";
                updateButtonStates();
                return;
            }

            int targetSectionLevel = (isGlobal || isRestored) ? 0 : selectedSectionLevelOneBased;
            MapSection targetSection = (isGlobal || isRestored) ? null : selectedSection;

            boolean ok = ReminderManager.transferJump(
                    fromMap,
                    jumpToMove,
                    selectedServer,
                    targetMap,
                    targetSectionLevel,
                    targetSection
            );

            if (!ok) {
                errorText = "Transfer failed.";
                updateButtonStates();
                return;
            }

            String target = EnumChatFormatting.YELLOW + selectedServer.getId()
                    + EnumChatFormatting.AQUA + " / " + EnumChatFormatting.YELLOW + targetMap.getId();

            if (!isGlobal && !isRestored && targetSection != null) {
                String sn = targetSection.getName() != null ? targetSection.getName() : "<unnamed>";
                target += EnumChatFormatting.AQUA + " / " + EnumChatFormatting.YELLOW + "L" + targetSectionLevel + ": " + sn;
            }

            sendClientChat(EnumChatFormatting.DARK_AQUA + "[ParkourStrats] " + EnumChatFormatting.AQUA
                    + "Jump transferred to " + target);

            Minecraft.getMinecraft().displayGuiScreen(new GuiJumpList(parent, targetMap, jumpToMove));
            return;
        }

        super.actionPerformed(button);
    }

    private void updateButtonStates() {
        boolean serverSelected = (selectedServer != null);
        boolean isGlobal = (selectedServer != null && ReminderManager.isGlobalServer(selectedServer));
        boolean isRestored = (selectedServer != null && ReminderManager.isRestoredServer(selectedServer));

        // Keep forced maps consistent
        if (isGlobal) {
            selectedMap = ReminderManager.getGlobalMap();
            selectedSectionLevelOneBased = 0;
            selectedSection = null;
        } else if (isRestored) {
            selectedMap = ReminderManager.getRestoredMap();
            selectedSectionLevelOneBased = 0;
            selectedSection = null;
        }

        if (pickMapButton != null) {
            pickMapButton.enabled = serverSelected && !isGlobal && !isRestored;
        }

        if (pickSectionButton != null) {
            pickSectionButton.enabled = (selectedServer != null && !isGlobal && !isRestored && selectedMap != null);

            if (isGlobal || isRestored) {
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

        ParkourMap targetMap = null;
        if (isGlobal) {
            targetMap = ReminderManager.getGlobalMap();
        } else if (isRestored) {
            targetMap = ReminderManager.getRestoredMap();
        } else {
            targetMap = selectedMap;
        }

        boolean ok = (selectedServer != null && fromMap != null && targetMap != null && targetMap != fromMap);

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