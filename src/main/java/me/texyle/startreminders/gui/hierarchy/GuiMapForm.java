package me.texyle.startreminders.gui.hierarchy;

import java.io.IOException;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.EnumChatFormatting;

import me.texyle.startreminders.utils.ChatUtils;

/**
 * Shared form for creating and editing a map: name, optional section levels (I..IV) and their names.
 * Subclasses supply the title, the initial values and what happens on submit.
 */
abstract class GuiMapForm extends GuiScreen {

    /** Initial form values. Null {@code sectionsEnabled} / {@code sectionsCount} keep the current ones. */
    protected static final class Prefill {
        final String mapName;
        final Boolean sectionsEnabled;
        final Integer sectionsCount;
        final String[] sectionNames; // 4 entries

        Prefill(String mapName, Boolean sectionsEnabled, Integer sectionsCount, String[] sectionNames) {
            this.mapName = mapName;
            this.sectionsEnabled = sectionsEnabled;
            this.sectionsCount = sectionsCount;
            this.sectionNames = sectionNames;
        }
    }

    private static final int BTN_SUBMIT = 1;
    private static final int BTN_CANCEL = 2;

    private static final int BTN_SECTIONS_TOGGLE = 10;
    private static final int BTN_ROMAN_I = 20;
    private static final int BTN_ROMAN_II = 21;
    private static final int BTN_ROMAN_III = 22;
    private static final int BTN_ROMAN_IV = 23;

    // Layout constants
    private static final int MAP_FIELD_W = 240;
    private static final int SECTION_LABEL_W = 80;
    private static final int SECTION_FIELD_W = 240;

    private final GuiScreen parent;

    private GuiTextField mapNameField;

    private boolean sectionsEnabled = false;
    private int sectionsCount = 1; // only meaningful when enabled

    private final GuiTextField[] sectionNameFields = new GuiTextField[4];

    private GuiButton btnSubmit;
    private GuiButton btnCancel;

    private GuiButton btnSectionsToggle;
    private GuiButton btnRomanI;
    private GuiButton btnRomanII;
    private GuiButton btnRomanIII;
    private GuiButton btnRomanIV;

    GuiMapForm(GuiScreen parent) {
        this.parent = parent;
    }

    // -------------------------
    // Subclass hooks
    // -------------------------

    protected abstract String getTitle();

    protected abstract String getSubmitLabel();

    protected abstract Prefill getPrefill();

    /** @return a message if the form cannot be submitted at all (missing context), else null */
    protected String getContextError() {
        return null;
    }

    /**
     * @param sectionsCount 0 when sections are disabled
     * @param sectionNames  null when sections are disabled
     * @return true on success
     */
    protected abstract boolean submit(String mapName, boolean sectionsEnabled, int sectionsCount, String[] sectionNames);

    protected abstract String getFailureMessage();

    // -------------------------
    // Layout
    // -------------------------

    private int getBaseY() {
        return this.height / 2 - 110;
    }

    private int getMapFieldX(int centerX) {
        return centerX - (MAP_FIELD_W / 2);
    }

    private int getSectionFullX(int centerX) {
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

        Prefill prefill = getPrefill();

        mapNameField = new GuiTextField(100, this.fontRendererObj, mapFieldX, baseY + 18, MAP_FIELD_W, 20);
        mapNameField.setFocused(true);
        mapNameField.setMaxStringLength(512);
        mapNameField.setText(prefill.mapName);

        if (prefill.sectionsEnabled != null) {
            sectionsEnabled = prefill.sectionsEnabled.booleanValue();
        }
        if (prefill.sectionsCount != null) {
            sectionsCount = prefill.sectionsCount.intValue();
        }

        for (int i = 0; i < 4; i++) {
            sectionNameFields[i] = new GuiTextField(200 + i, this.fontRendererObj, sectionFieldX, (baseY + 74) + 30 + (i * 22), SECTION_FIELD_W, 20);
            sectionNameFields[i].setMaxStringLength(512);
            sectionNameFields[i].setText(prefill.sectionNames[i]);
        }

        // Toggle "Include sections" (centered like map field)
        btnSectionsToggle = new GuiButton(BTN_SECTIONS_TOGGLE, mapFieldX, baseY + 48, MAP_FIELD_W, 20, getSectionsToggleLabel());

        // Roman buttons (centered)
        int romanSize = 28;
        int romanGap = 6;
        int rowW = (romanSize * 4) + (romanGap * 3);
        int romanStartX = centerX - (rowW / 2);
        int romanY = baseY + 74;

        btnRomanI = new GuiButton(BTN_ROMAN_I, romanStartX + (romanSize + romanGap) * 0, romanY, romanSize, 20, "I");
        btnRomanII = new GuiButton(BTN_ROMAN_II, romanStartX + (romanSize + romanGap) * 1, romanY, romanSize, 20, "II");
        btnRomanIII = new GuiButton(BTN_ROMAN_III, romanStartX + (romanSize + romanGap) * 2, romanY, romanSize, 20, "III");
        btnRomanIV = new GuiButton(BTN_ROMAN_IV, romanStartX + (romanSize + romanGap) * 3, romanY, romanSize, 20, "IV");

        // Bottom buttons fixed
        int buttonsY = this.height - 48;
        btnSubmit = new GuiButton(BTN_SUBMIT, centerX - 110, buttonsY, 100, 20, getSubmitLabel());
        btnCancel = new GuiButton(BTN_CANCEL, centerX + 10, buttonsY, 100, 20, "Cancel");

        this.buttonList.add(btnSectionsToggle);
        this.buttonList.add(btnRomanI);
        this.buttonList.add(btnRomanII);
        this.buttonList.add(btnRomanIII);
        this.buttonList.add(btnRomanIV);

        this.buttonList.add(btnSubmit);
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

        this.drawCenteredString(this.fontRendererObj, getTitle(), centerX, baseY - 12, 0xFFFFFF);

        // Map name label + centered field
        this.fontRendererObj.drawString("Map name:", mapFieldX, baseY + 6, 0xFFFFFF, true);
        mapNameField.drawTextBox();

        // Section labels + fields (same row)
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

    // -------------------------
    // Input
    // -------------------------

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button == null) return;

        switch (button.id) {
            case BTN_CANCEL:
                Minecraft.getMinecraft().displayGuiScreen(parent);
                return;
            case BTN_SECTIONS_TOGGLE:
                sectionsEnabled = !sectionsEnabled;
                if (!sectionsEnabled) {
                    sectionsCount = 1; // reset for next time
                }
                updateControlsVisibilityAndState();
                return;
            case BTN_ROMAN_I:
                setSectionsCount(1);
                return;
            case BTN_ROMAN_II:
                setSectionsCount(2);
                return;
            case BTN_ROMAN_III:
                setSectionsCount(3);
                return;
            case BTN_ROMAN_IV:
                setSectionsCount(4);
                return;
            case BTN_SUBMIT:
                onSubmit();
                return;
            default:
                break;
        }
    }

    private void setSectionsCount(int count) {
        sectionsCount = count;
        updateControlsVisibilityAndState();
    }

    private void onSubmit() {
        String contextError = getContextError();
        if (contextError != null) {
            sendError(contextError);
            return;
        }

        String name = (mapNameField.getText() != null) ? mapNameField.getText().trim() : "";
        if (name.length() == 0) {
            sendError("Map name cannot be empty.");
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

        if (!submit(name, sectionsEnabled, countToSave, names)) {
            sendError(getFailureMessage());
            return;
        }

        Minecraft.getMinecraft().displayGuiScreen(parent);
    }

    private void sendError(String message) {
        ChatUtils.sendChat(Minecraft.getMinecraft(), EnumChatFormatting.RED + message);
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
            actionPerformed(btnSubmit);
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
