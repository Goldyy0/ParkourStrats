package me.texyle.startreminders.gui;

import java.io.IOException;

import me.texyle.startreminders.data.ParkourMap;
import me.texyle.startreminders.reminders.ReminderManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.EnumChatFormatting;

public class GuiConfigurePlaceholder extends GuiScreen {

    private static final int FIELD_WIDTH = 260;

    private final GuiScreen parent;

    private GuiTextField urlField;

    private GuiButton backButton;
    private GuiButton syncButton;
    private GuiButton stopButton;

    private String statusText = "";

    public GuiConfigurePlaceholder(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        super.initGui();

        int yBase = this.height / 3;

        backButton = new GuiButton(1, 8, 8, 60, 20, "Back");
        this.buttonList.add(backButton);

        syncButton = new GuiButton(2, 0, 0, "Sync jumps");
        syncButton.width = 120;
        syncButton.xPosition = (this.width - FIELD_WIDTH) / 2;
        syncButton.yPosition = yBase + 58;
        this.buttonList.add(syncButton);

        stopButton = new GuiButton(3, 0, 0, "Stop Sync");
        stopButton.width = 120;
        stopButton.xPosition = (this.width - FIELD_WIDTH) / 2 + 140;
        stopButton.yPosition = yBase + 58;
        this.buttonList.add(stopButton);

        urlField = new GuiTextField(0, this.fontRendererObj, getFieldX(), yBase + 18, FIELD_WIDTH, 20);
        urlField.setFocused(true);
        urlField.setMaxStringLength(512);

        ParkourMap map = ReminderManager.getSelectedMap();
        if (map != null) {
            urlField.setText(map.getPlaceholderSheetUrl());
        }

        updateButtons();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);

        int yBase = this.height / 3;

        this.drawCenteredString(this.fontRendererObj, "Configure placeholder", this.width / 2, 14, 0xFFFFFF);
        this.fontRendererObj.drawString("Sheet URL:", getFieldX(), yBase + 6, 0xFFFFFF, true);

        if (statusText != null && !statusText.trim().isEmpty()) {
            this.drawCenteredString(this.fontRendererObj, statusText, this.width / 2, yBase + 95, 0xFFFFFF);
        }

        urlField.drawTextBox();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (urlField != null) {
            urlField.textboxKeyTyped(typedChar, keyCode);
        }

        if (keyCode == 1) { // ESC
            goBack();
            return;
        }

        super.keyTyped(typedChar, keyCode);
        updateButtons();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (urlField != null) {
            urlField.mouseClicked(mouseX, mouseY, mouseButton);
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (urlField != null) {
            urlField.updateCursorCounter();
        }
        updateButtons();
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button == null) return;

        if (button.id == 1) {
            goBack();
            return;
        }

        if (button.id == 2) { // Sync jumps
            ParkourMap map = ReminderManager.getSelectedMap();
            if (map == null) {
                statusText = EnumChatFormatting.RED + "Select a map first.";
                return;
            }

            String url = urlField != null ? urlField.getText() : "";
            url = url != null ? url.trim() : "";

            if (url.isEmpty()) {
                statusText = EnumChatFormatting.RED + "URL is required.";
                return;
            }

            statusText = EnumChatFormatting.AQUA + "Downloading CSV...";
            updateButtons();

            // Persist URL + enable mode immediately, list will arrive async
            ReminderManager.enablePlaceholderSyncForMap(map, url);

            ReminderManager.requestPlaceholderJumpListSync(map, new ReminderManager.IPlaceholderJumpSyncCallback() {
                @Override
                public void onSuccess(int count) {
                    statusText = EnumChatFormatting.GREEN + "Jump list loaded (" + count + ").";
                    // Return to parent so user can immediately use it
                    Minecraft.getMinecraft().displayGuiScreen(parent);
                }

                @Override
                public void onError(String errorMessage) {
                    statusText = EnumChatFormatting.RED + errorMessage;
                    updateButtons();
                }
            });

            return;
        }

        if (button.id == 3) { // Stop Sync
            ParkourMap map = ReminderManager.getSelectedMap();
            if (map != null) {
                ReminderManager.disablePlaceholderSyncForMap(map);
            }
            statusText = EnumChatFormatting.YELLOW + "Placeholder sync disabled.";
            Minecraft.getMinecraft().displayGuiScreen(parent);
            return;
        }

        super.actionPerformed(button);
    }

    private void updateButtons() {
        ParkourMap map = ReminderManager.getSelectedMap();

        boolean hasMap = (map != null);
        boolean hasUrl = (urlField != null && urlField.getText() != null && urlField.getText().trim().length() > 0);

        if (syncButton != null) syncButton.enabled = hasMap && hasUrl && !ReminderManager.isAnySheetSyncInProgress();
        if (stopButton != null) stopButton.enabled = hasMap && map.isPlaceholderSyncEnabled();
    }

    private void goBack() {
        if (parent != null) {
            Minecraft.getMinecraft().displayGuiScreen(parent);
        } else {
            Minecraft.getMinecraft().displayGuiScreen(null);
        }
    }

    private int getFieldX() {
        return (this.width - FIELD_WIDTH) / 2;
    }
}