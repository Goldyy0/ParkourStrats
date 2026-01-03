package me.texyle.startreminders.gui;

import java.io.IOException;
import java.util.ArrayList;

import me.texyle.startreminders.data.Jump;
import me.texyle.startreminders.data.ParkourMap;
import me.texyle.startreminders.reminders.Reminder;
import me.texyle.startreminders.reminders.ReminderManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

/**
 * Converts a legacy RestoredStrats entry into a new Global jump+strategy.
 * Left side shows legacy (read-only), right side is the new format editor.
 */
public class GuiConvertLegacy extends GuiScreen {

    private static final int COLOR_TEXT = 0xFFFFFF;
    private static final int COLOR_PANEL_BG = 0xAA0B0B0B;
    private static final int COLOR_PANEL_BORDER = 0xCC2A2A2A;

    private static final int BTN_BACK = 10;
    private static final int BTN_SAVE = 11;

    private final GuiScreen parent;
    private final Jump legacyJump;

    // Provided by caller (GuiJumpList) as step 1 result
    private final String newJumpName;

    // Legacy (left) fields - read only
    private GuiTextField legacyLine1;
    private GuiTextField legacyLine2;
    private GuiTextField legacyLine3;
    private GuiTextField legacyLine4;
    private GuiTextField legacyLine5;
    private GuiTextField legacyX;
    private GuiTextField legacyY;
    private GuiTextField legacyZ;

    // New (right) fields - editable
    private GuiTextField newX;
    private GuiTextField newY;
    private GuiTextField newZ;
    private GuiTextField newPosition;
    private GuiTextField newFacing;
    private GuiTextField newSetup;
    private GuiTextField newStrategy;
    private GuiTextField newStrafe;
    private GuiTextField newTurn;
    private GuiTextField newAuthor;
    private GuiTextField newTips;

    private GuiButton backButton;
    private GuiButton saveButton;

    public GuiConvertLegacy(GuiScreen parent, Jump legacyJump, String newJumpName) {
        this.parent = parent;
        this.legacyJump = legacyJump;
        this.newJumpName = (newJumpName != null) ? newJumpName.trim() : "";
    }

    @Override
    public void initGui() {
        super.initGui();

        if (newJumpName == null || newJumpName.trim().isEmpty()) {
            Minecraft.getMinecraft().displayGuiScreen(parent);
            return;
        }

        this.buttonList.clear();

        backButton = new GuiButton(BTN_BACK, 8, 8, 60, 20, "Back");
        saveButton = new GuiButton(BTN_SAVE, this.width / 2 - 50, this.height - 28, 100, 20, "Save");

        this.buttonList.add(backButton);
        this.buttonList.add(saveButton);

        createFields();
        updateSaveButtonState();
    }

    private void createFields() {
        FontRenderer fr = mc.fontRendererObj;

        int mid = this.width / 2;
        int leftPanelX = 10;
        int rightPanelX = mid + 6;

        int panelTop = 34;
        int rowH = 22;

        int legacyFieldW = mid - 22;
        int newFieldW = this.width - rightPanelX - 10;

        // Legacy coords + lines (read-only)
        legacyX = new GuiTextField(1001, fr, leftPanelX + 80, panelTop + rowH * 1, 50, 20);
        legacyY = new GuiTextField(1002, fr, leftPanelX + 140, panelTop + rowH * 1, 50, 20);
        legacyZ = new GuiTextField(1003, fr, leftPanelX + 200, panelTop + rowH * 1, 50, 20);

        legacyLine1 = new GuiTextField(1011, fr, leftPanelX + 80, panelTop + rowH * 3, legacyFieldW - 90, 20);
        legacyLine2 = new GuiTextField(1012, fr, leftPanelX + 80, panelTop + rowH * 4, legacyFieldW - 90, 20);
        legacyLine3 = new GuiTextField(1013, fr, leftPanelX + 80, panelTop + rowH * 5, legacyFieldW - 90, 20);
        legacyLine4 = new GuiTextField(1014, fr, leftPanelX + 80, panelTop + rowH * 6, legacyFieldW - 90, 20);
        legacyLine5 = new GuiTextField(1015, fr, leftPanelX + 80, panelTop + rowH * 7, legacyFieldW - 90, 20);

        setReadOnly(legacyX);
        setReadOnly(legacyY);
        setReadOnly(legacyZ);
        setReadOnly(legacyLine1);
        setReadOnly(legacyLine2);
        setReadOnly(legacyLine3);
        setReadOnly(legacyLine4);
        setReadOnly(legacyLine5);

        // Fill legacy values
        int lx = (legacyJump != null) ? legacyJump.getX() : 0;
        int ly = (legacyJump != null) ? legacyJump.getY() : 0;
        int lz = (legacyJump != null) ? legacyJump.getZ() : 0;

        legacyX.setText(Integer.toString(lx));
        legacyY.setText(Integer.toString(ly));
        legacyZ.setText(Integer.toString(lz));

        ArrayList<String> legacyLines = getLegacyLines();

        legacyLine1.setText(getLegacyLineForDisplay(legacyLines, 0));
        legacyLine2.setText(getLegacyLineForDisplay(legacyLines, 1));
        legacyLine3.setText(getLegacyLineForDisplay(legacyLines, 2));
        legacyLine4.setText(getLegacyLineForDisplay(legacyLines, 3));
        legacyLine5.setText(getLegacyLineForDisplay(legacyLines, 4));

        // New editor (editable) - coords prefilled from legacy
        newX = new GuiTextField(2001, fr, rightPanelX + 90, panelTop + rowH * 1, 50, 20);
        newY = new GuiTextField(2002, fr, rightPanelX + 150, panelTop + rowH * 1, 50, 20);
        newZ = new GuiTextField(2003, fr, rightPanelX + 210, panelTop + rowH * 1, 50, 20);

        newX.setText(Integer.toString(lx));
        newY.setText(Integer.toString(ly));
        newZ.setText(Integer.toString(lz));

        int fieldX = rightPanelX + 90;
        int fieldYBase = panelTop + rowH * 3;

        newPosition = new GuiTextField(2010, fr, fieldX, fieldYBase + rowH * 0, newFieldW - 100, 20);
        newFacing = new GuiTextField(2011, fr, fieldX, fieldYBase + rowH * 1, newFieldW - 100, 20);
        newSetup = new GuiTextField(2012, fr, fieldX, fieldYBase + rowH * 2, newFieldW - 100, 20);
        newStrategy = new GuiTextField(2013, fr, fieldX, fieldYBase + rowH * 3, newFieldW - 100, 20);
        newStrafe = new GuiTextField(2014, fr, fieldX, fieldYBase + rowH * 4, newFieldW - 100, 20);
        newTurn = new GuiTextField(2015, fr, fieldX, fieldYBase + rowH * 5, newFieldW - 100, 20);
        newAuthor = new GuiTextField(2016, fr, fieldX, fieldYBase + rowH * 6, newFieldW - 100, 20);
        newTips = new GuiTextField(2017, fr, fieldX, fieldYBase + rowH * 7, newFieldW - 100, 20);

        newPosition.setMaxStringLength(80);
        newFacing.setMaxStringLength(80);
        newSetup.setMaxStringLength(80);
        newStrategy.setMaxStringLength(80);
        newStrafe.setMaxStringLength(80);
        newTurn.setMaxStringLength(80);
        newAuthor.setMaxStringLength(80);
        newTips.setMaxStringLength(160);

        newPosition.setFocused(true);
    }

    private static void setReadOnly(GuiTextField f) {
        if (f == null) return;
        f.setCanLoseFocus(true);
        f.setEnabled(false);
    }

    private ArrayList<String> getLegacyLines() {
        if (legacyJump == null) return new ArrayList<String>();
        ArrayList<Reminder> rs = legacyJump.getReminders();
        if (rs == null || rs.isEmpty()) return new ArrayList<String>();

        int idx = legacyJump.getActiveReminderIndex();
        if (idx < 0 || idx >= rs.size()) idx = 0;

        Reminder r = rs.get(idx);
        if (r == null || r.lines == null) return new ArrayList<String>();
        return r.lines;
    }

    private static String getLine(ArrayList<String> lines, int i) {
        if (lines == null || i < 0 || i >= lines.size()) return "";
        String s = lines.get(i);
        return (s != null) ? s : "";
    }

    private static String getLegacyLineForDisplay(ArrayList<String> lines, int displayIndex) {
        if (lines == null || displayIndex < 0) {
            return "";
        }

        // If the legacy entry was normalized into the new 8-line format:
        // [Position, Facing, Setup, Strategy, Strafe, Turn, Author, Tips]
        // then legacy "Line 1..5" should display indices 2..6 (offset +2).
        if (lines.size() >= 8) {
            int realIndex = displayIndex + 2;
            return getLine(lines, realIndex);
        }

        // Otherwise, fallback to old behavior.
        return getLine(lines, displayIndex);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();

        drawTitle();

        int mid = this.width / 2;
        drawPanel(10, 34, mid - 6, this.height - 40);
        drawPanel(mid + 6, 34, this.width - 10, this.height - 40);

        drawLegacyLabels();
        drawNewLabels();

        drawFields();

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawTitle() {
        String t = "Convert legacy -> Global (Jump: " + EnumChatFormatting.YELLOW + newJumpName + EnumChatFormatting.RESET + ")";
        GlStateManager.pushMatrix();
        GlStateManager.scale(2, 2, 2);
        int w = this.fontRendererObj.getStringWidth(t);
        int x = (this.width - (w * 2)) / 4;
        this.fontRendererObj.drawString(t, x, 6, COLOR_TEXT, true);
        GlStateManager.popMatrix();
    }

    private void drawPanel(int left, int top, int right, int bottom) {
        drawRect(left, top, right, bottom, COLOR_PANEL_BG);
        drawRect(left, top, left + 1, bottom, COLOR_PANEL_BORDER);
        drawRect(right - 1, top, right, bottom, COLOR_PANEL_BORDER);
    }

    private void drawLegacyLabels() {
        int leftPanelX = 10;
        int panelTop = 34;
        int rowH = 22;

        fontRendererObj.drawString(EnumChatFormatting.AQUA + "Legacy (read-only)", leftPanelX + 6, panelTop + 6, COLOR_TEXT, true);

        fontRendererObj.drawString("Coords:", leftPanelX + 6, panelTop + rowH * 1 + 6, COLOR_TEXT, true);

        fontRendererObj.drawString("Line 1:", leftPanelX + 6, panelTop + rowH * 3 + 6, COLOR_TEXT, true);
        fontRendererObj.drawString("Line 2:", leftPanelX + 6, panelTop + rowH * 4 + 6, COLOR_TEXT, true);
        fontRendererObj.drawString("Line 3:", leftPanelX + 6, panelTop + rowH * 5 + 6, COLOR_TEXT, true);
        fontRendererObj.drawString("Line 4:", leftPanelX + 6, panelTop + rowH * 6 + 6, COLOR_TEXT, true);
        fontRendererObj.drawString("Line 5:", leftPanelX + 6, panelTop + rowH * 7 + 6, COLOR_TEXT, true);
    }

    private void drawNewLabels() {
        int mid = this.width / 2;
        int rightPanelX = mid + 6;
        int panelTop = 34;
        int rowH = 22;

        fontRendererObj.drawString(EnumChatFormatting.GREEN + "New (editable)", rightPanelX + 6, panelTop + 6, COLOR_TEXT, true);
        fontRendererObj.drawString("Coords:", rightPanelX + 6, panelTop + rowH * 1 + 6, COLOR_TEXT, true);

        int baseY = panelTop + rowH * 3;
        fontRendererObj.drawString("Position:", rightPanelX + 6, baseY + rowH * 0 + 6, COLOR_TEXT, true);
        fontRendererObj.drawString("Facing:", rightPanelX + 6, baseY + rowH * 1 + 6, COLOR_TEXT, true);
        fontRendererObj.drawString("Setup:", rightPanelX + 6, baseY + rowH * 2 + 6, COLOR_TEXT, true);
        fontRendererObj.drawString("Strategy:", rightPanelX + 6, baseY + rowH * 3 + 6, COLOR_TEXT, true);
        fontRendererObj.drawString("Strafe:", rightPanelX + 6, baseY + rowH * 4 + 6, COLOR_TEXT, true);
        fontRendererObj.drawString("Turn:", rightPanelX + 6, baseY + rowH * 5 + 6, COLOR_TEXT, true);
        fontRendererObj.drawString("Author:", rightPanelX + 6, baseY + rowH * 6 + 6, COLOR_TEXT, true);
        fontRendererObj.drawString("Tips:", rightPanelX + 6, baseY + rowH * 7 + 6, COLOR_TEXT, true);
    }

    private void drawFields() {
        // Legacy fields
        legacyX.drawTextBox();
        legacyY.drawTextBox();
        legacyZ.drawTextBox();

        legacyLine1.drawTextBox();
        legacyLine2.drawTextBox();
        legacyLine3.drawTextBox();
        legacyLine4.drawTextBox();
        legacyLine5.drawTextBox();

        // New fields
        newX.drawTextBox();
        newY.drawTextBox();
        newZ.drawTextBox();

        newPosition.drawTextBox();
        newFacing.drawTextBox();
        newSetup.drawTextBox();
        newStrategy.drawTextBox();
        newStrafe.drawTextBox();
        newTurn.drawTextBox();
        newAuthor.drawTextBox();
        newTips.drawTextBox();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        // New editor typing
        newPosition.textboxKeyTyped(typedChar, keyCode);
        newFacing.textboxKeyTyped(typedChar, keyCode);
        newSetup.textboxKeyTyped(typedChar, keyCode);
        newStrategy.textboxKeyTyped(typedChar, keyCode);
        newStrafe.textboxKeyTyped(typedChar, keyCode);
        newTurn.textboxKeyTyped(typedChar, keyCode);
        newAuthor.textboxKeyTyped(typedChar, keyCode);
        newTips.textboxKeyTyped(typedChar, keyCode);

        // Coords - allow digits, backspace, minus
        if (Character.isDigit(typedChar) || keyCode == 14 || typedChar == '-') {
            newX.textboxKeyTyped(typedChar, keyCode);
            newY.textboxKeyTyped(typedChar, keyCode);
            newZ.textboxKeyTyped(typedChar, keyCode);
        }

        super.keyTyped(typedChar, keyCode);
        updateSaveButtonState();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        newX.mouseClicked(mouseX, mouseY, mouseButton);
        newY.mouseClicked(mouseX, mouseY, mouseButton);
        newZ.mouseClicked(mouseX, mouseY, mouseButton);

        newPosition.mouseClicked(mouseX, mouseY, mouseButton);
        newFacing.mouseClicked(mouseX, mouseY, mouseButton);
        newSetup.mouseClicked(mouseX, mouseY, mouseButton);
        newStrategy.mouseClicked(mouseX, mouseY, mouseButton);
        newStrafe.mouseClicked(mouseX, mouseY, mouseButton);
        newTurn.mouseClicked(mouseX, mouseY, mouseButton);
        newAuthor.mouseClicked(mouseX, mouseY, mouseButton);
        newTips.mouseClicked(mouseX, mouseY, mouseButton);

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void updateScreen() {
        newX.updateCursorCounter();
        newY.updateCursorCounter();
        newZ.updateCursorCounter();

        newPosition.updateCursorCounter();
        newFacing.updateCursorCounter();
        newSetup.updateCursorCounter();
        newStrategy.updateCursorCounter();
        newStrafe.updateCursorCounter();
        newTurn.updateCursorCounter();
        newAuthor.updateCursorCounter();
        newTips.updateCursorCounter();

        super.updateScreen();
        updateSaveButtonState();
    }

    private void updateSaveButtonState() {
        if (saveButton == null) return;

        boolean coordsOk = canParseInt(newX) && canParseInt(newY) && canParseInt(newZ);

        // New rule: no specific fields required, but at least one must be non-empty.
        boolean hasAnyText =
                hasText(newPosition) ||
                        hasText(newFacing) ||
                        hasText(newSetup) ||
                        hasText(newStrategy) ||
                        hasText(newStrafe) ||
                        hasText(newTurn) ||
                        hasText(newAuthor) ||
                        hasText(newTips);

        saveButton.enabled = coordsOk && hasAnyText;
    }

    private static boolean hasText(GuiTextField f) {
        return f != null && f.getText() != null && f.getText().trim().length() > 0;
    }

    private static boolean canParseInt(GuiTextField f) {
        if (f == null || f.getText() == null) return false;
        String s = f.getText().trim();
        if (s.isEmpty() || "-".equals(s)) return false;
        try {
            Integer.parseInt(s);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == BTN_BACK) {
            Minecraft.getMinecraft().displayGuiScreen(parent);
            return;
        }

        if (button.id == BTN_SAVE) {
            if (saveButton == null || !saveButton.enabled) {
                return;
            }

            ParkourMap globalMap = ReminderManager.getGlobalMap();
            if (globalMap == null) {
                sendChatError("Global map is not available.");
                return;
            }

            int x = Integer.parseInt(newX.getText().trim());
            int y = Integer.parseInt(newY.getText().trim());
            int z = Integer.parseInt(newZ.getText().trim());

            Jump j = ReminderManager.getOrCreateJumpByNameAndCoords(globalMap, newJumpName, x, y, z);
            if (j == null) {
                sendChatError("Failed to create Global jump.");
                return;
            }

            ReminderManager.setSelectedMap(globalMap);
            ReminderManager.setSelectedJump(j);

            // Persist jump-level coords
            j.setX(x);
            j.setY(y);
            j.setZ(z);

            // New format lines:
            // [Position, Facing, Setup, Strategy, Strafe, Turn, Author, Tips]
            ArrayList<String> lines = new ArrayList<String>();
            lines.add(safe(newPosition.getText()));
            lines.add(safe(newFacing.getText()));
            lines.add(safe(newSetup.getText()));
            lines.add(safe(newStrategy.getText()));
            lines.add(safe(newStrafe.getText()));
            lines.add(safe(newTurn.getText()));
            lines.add(safe(newAuthor.getText()));
            lines.add(safe(newTips.getText()));

            ReminderManager.createReminder(lines, x, y, z);

            // NEW: Remove the original legacy jump from RestoredStrats after a successful conversion.
            boolean removed = ReminderManager.removeJumpFromRestoredStrats(legacyJump);
            if (!removed) {
                sendChatError("Converted, but failed to remove the legacy entry from RestoredStrats.");
            } else {
                sendChatInfo("Converted and removed legacy entry from RestoredStrats.");
            }

            Minecraft.getMinecraft().displayGuiScreen(parent);
            return;
        }

        super.actionPerformed(button);
    }

    private static String safe(String s) {
        return (s != null) ? s.trim() : "";
    }

    private static void sendChatError(String msg) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null) return;
        mc.thePlayer.addChatMessage(new ChatComponentText(
                EnumChatFormatting.DARK_AQUA + "[ParkourStrats] " + EnumChatFormatting.RED + msg
        ));
    }

    private static void sendChatInfo(String msg) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null) return;
        mc.thePlayer.addChatMessage(new ChatComponentText(
                EnumChatFormatting.DARK_AQUA + "[ParkourStrats] " + EnumChatFormatting.AQUA + msg
        ));
    }
}