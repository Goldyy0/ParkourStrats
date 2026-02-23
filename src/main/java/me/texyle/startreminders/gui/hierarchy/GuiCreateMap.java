package me.texyle.startreminders.gui.hierarchy;

import java.io.IOException;

import me.texyle.startreminders.data.ServerProfile;
import me.texyle.startreminders.reminders.ReminderManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

public class GuiCreateMap extends GuiScreen {

    private static final int BTN_CREATE = 1;
    private static final int BTN_CANCEL = 2;

    private static final int BTN_SECTIONS_TOGGLE = 10;
    private static final int BTN_ROMAN_I = 20;
    private static final int BTN_ROMAN_II = 21;
    private static final int BTN_ROMAN_III = 22;
    private static final int BTN_ROMAN_IV = 23;

    private final GuiScreen parent;
    private final ServerProfile server;

    private GuiTextField mapNameField;

    private boolean sectionsEnabled = false;
    private int sectionsCount = 1; // only meaningful when enabled

    private GuiTextField[] sectionNameFields = new GuiTextField[4];

    private GuiButton btnCreate;
    private GuiButton btnCancel;

    private GuiButton btnSectionsToggle;
    private GuiButton btnRomanI;
    private GuiButton btnRomanII;
    private GuiButton btnRomanIII;
    private GuiButton btnRomanIV;

    // Layout constants
    private static final int MAP_FIELD_W = 240;
    private static final int SECTION_LABEL_W = 80;
    private static final int SECTION_FIELD_W = 240;

    public GuiCreateMap(GuiScreen parent, ServerProfile server) {
        this.parent = parent;
        this.server = server;
    }

    private int getBaseY() {
        // All content except Create/Cancel is positioned relative to this
        return this.height / 2 - 110;
    }

    private int getMapFieldX(int centerX) {
        // Map name input stays centered (do NOT use section layout offsets)
        return centerX - (MAP_FIELD_W / 2);
    }

    private int getSectionFullX(int centerX) {
        // Section label+field block is centered as a whole
        int fullW = SECTION_LABEL_W + SECTION_FIELD_W;
        return centerX - (fullW / 2);
    }

    private int getSectionFieldX(int centerX) {
        return getSectionFullX(centerX) + SECTION_LABEL_W;
    }

    @Override
    public void initGui() {
        super.initGui();

        int centerX = this.width / 2;
        int baseY = getBaseY();

        int mapFieldX = getMapFieldX(centerX);
        int sectionFieldX = getSectionFieldX(centerX);

        mapNameField = new GuiTextField(100, this.fontRendererObj, mapFieldX, baseY + 18, MAP_FIELD_W, 20);
        mapNameField.setFocused(true);
        mapNameField.setMaxStringLength(512);

        // Toggle "Include sections" (keep centered like map field)
        btnSectionsToggle = new GuiButton(BTN_SECTIONS_TOGGLE, mapFieldX, baseY + 48, MAP_FIELD_W, 20, getSectionsToggleLabel());

        // Roman buttons (square-ish) - centered
        int romanSize = 28;
        int romanGap = 6;
        int rowW = (romanSize * 4) + (romanGap * 3);
        int romanStartX = centerX - (rowW / 2);
        int romanY = baseY + 74;

        btnRomanI = new GuiButton(BTN_ROMAN_I, romanStartX + (romanSize + romanGap) * 0, romanY, romanSize, 20, "I");
        btnRomanII = new GuiButton(BTN_ROMAN_II, romanStartX + (romanSize + romanGap) * 1, romanY, romanSize, 20, "II");
        btnRomanIII = new GuiButton(BTN_ROMAN_III, romanStartX + (romanSize + romanGap) * 2, romanY, romanSize, 20, "III");
        btnRomanIV = new GuiButton(BTN_ROMAN_IV, romanStartX + (romanSize + romanGap) * 3, romanY, romanSize, 20, "IV");

        // Section name fields (defaults) - use sectionFieldX (label space reserved on the left)
        String[] defaults = new String[] { "Section", "Sub-section", "Area", "CP" };
        for (int i = 0; i < 4; i++) {
            sectionNameFields[i] = new GuiTextField(200 + i, this.fontRendererObj, sectionFieldX, romanY + 30 + (i * 22), SECTION_FIELD_W, 20);
            sectionNameFields[i].setMaxStringLength(512);
            sectionNameFields[i].setText(defaults[i]);
        }

        // Bottom buttons fixed
        int buttonsY = this.height - 48;

        btnCreate = new GuiButton(BTN_CREATE, centerX - 110, buttonsY, 100, 20, "Create");
        btnCancel = new GuiButton(BTN_CANCEL, centerX + 10, buttonsY, 100, 20, "Cancel");

        this.buttonList.add(btnSectionsToggle);
        this.buttonList.add(btnRomanI);
        this.buttonList.add(btnRomanII);
        this.buttonList.add(btnRomanIII);
        this.buttonList.add(btnRomanIV);

        this.buttonList.add(btnCreate);
        this.buttonList.add(btnCancel);

        updateControlsVisibilityAndState();
    }

    private String getSectionsToggleLabel() {
        return (sectionsEnabled ? "[x] " : "[ ] ") + "Include sections";
    }

    private void updateControlsVisibilityAndState() {
        btnSectionsToggle.displayString = getSectionsToggleLabel();

        boolean enabled = sectionsEnabled;

        // Highlight selected count by disabling that button (simple visual cue)
        btnRomanI.enabled = enabled && sectionsCount != 1;
        btnRomanII.enabled = enabled && sectionsCount != 2;
        btnRomanIII.enabled = enabled && sectionsCount != 3;
        btnRomanIV.enabled = enabled && sectionsCount != 4;

        for (int i = 0; i < 4; i++) {
            sectionNameFields[i].setVisible(enabled && i < sectionsCount);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();

        int centerX = this.width / 2;
        int baseY = getBaseY();

        int mapFieldX = getMapFieldX(centerX);
        int sectionFullX = getSectionFullX(centerX);

        this.drawCenteredString(this.fontRendererObj, "Create map", centerX, baseY - 12, 0xFFFFFF);

        // Map name label + centered field
        this.fontRendererObj.drawString("Map name:", mapFieldX, baseY + 6, 0xFFFFFF, true);
        mapNameField.drawTextBox();

        // Section fields labels (same row as their text fields)
        if (sectionsEnabled) {
            int romanY = baseY + 74;
            int fieldStartY = romanY + 30;

            int labelX = sectionFullX;

            for (int i = 0; i < sectionsCount; i++) {
                String label = "Section " + (i + 1) + ":";
                int y = fieldStartY + (i * 22);

                this.fontRendererObj.drawString(label, labelX, y + 6, 0xFFFFFF, true);
                sectionNameFields[i].drawTextBox();
            }
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button == null) return;

        if (button.id == BTN_CANCEL) {
            Minecraft.getMinecraft().displayGuiScreen(parent);
            return;
        }

        if (button.id == BTN_SECTIONS_TOGGLE) {
            sectionsEnabled = !sectionsEnabled;
            if (!sectionsEnabled) {
                sectionsCount = 1; // reset to a sane default for next time
            }
            updateControlsVisibilityAndState();
            return;
        }

        if (button.id == BTN_ROMAN_I) { sectionsCount = 1; updateControlsVisibilityAndState(); return; }
        if (button.id == BTN_ROMAN_II) { sectionsCount = 2; updateControlsVisibilityAndState(); return; }
        if (button.id == BTN_ROMAN_III) { sectionsCount = 3; updateControlsVisibilityAndState(); return; }
        if (button.id == BTN_ROMAN_IV) { sectionsCount = 4; updateControlsVisibilityAndState(); return; }

        if (button.id == BTN_CREATE) {
            String id = (mapNameField.getText() != null) ? mapNameField.getText().trim() : "";
            if (id.length() == 0) {
                sendClientChat(EnumChatFormatting.RED + "Map name cannot be empty.");
                return;
            }

            String[] names = null;
            int countToSave = 0;

            if (sectionsEnabled) {
                countToSave = sectionsCount;
                names = new String[4];
                for (int i = 0; i < 4; i++) {
                    String v = sectionNameFields[i].getText();
                    names[i] = (v != null) ? v.trim() : "";
                }
            }

            boolean ok = ReminderManager.createMap(server, id, sectionsEnabled, countToSave, names);
            if (!ok) {
                sendClientChat(EnumChatFormatting.RED + "Create failed. Name may be invalid or already exists.");
                return;
            }

            Minecraft.getMinecraft().displayGuiScreen(parent);
        }
    }

    private void sendClientChat(String msg) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null || msg == null) return;
        mc.thePlayer.addChatMessage(new ChatComponentText(msg));
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        // Esc
        if (keyCode == 1) {
            Minecraft.getMinecraft().displayGuiScreen(parent);
            return;
        }

        mapNameField.textboxKeyTyped(typedChar, keyCode);

        if (sectionsEnabled) {
            for (int i = 0; i < sectionsCount; i++) {
                sectionNameFields[i].textboxKeyTyped(typedChar, keyCode);
            }
        }

        // Enter
        if (keyCode == 28) {
            actionPerformed(btnCreate);
            return;
        }

        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        mapNameField.mouseClicked(mouseX, mouseY, mouseButton);

        if (sectionsEnabled) {
            for (int i = 0; i < sectionsCount; i++) {
                sectionNameFields[i].mouseClicked(mouseX, mouseY, mouseButton);
            }
        }

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        mapNameField.updateCursorCounter();

        if (sectionsEnabled) {
            for (int i = 0; i < sectionsCount; i++) {
                sectionNameFields[i].updateCursorCounter();
            }
        }
    }
}