package me.texyle.startreminders.gui.hierarchy;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.EnumChatFormatting;

import me.texyle.startreminders.data.ParkourMap;
import me.texyle.startreminders.ui.UiButton;
import me.texyle.startreminders.ui.UiDraw;
import me.texyle.startreminders.ui.UiScreen;
import me.texyle.startreminders.ui.UiSegmented;
import me.texyle.startreminders.ui.UiTextField;
import me.texyle.startreminders.ui.UiTheme;
import me.texyle.startreminders.ui.UiToggle;
import me.texyle.startreminders.utils.ChatUtils;

/**
 * Shared form for creating and editing a map: name, optional section levels (I..IV) and their names.
 * Subclasses supply the title, the initial values and what happens on submit.
 */
abstract class GuiMapForm extends UiScreen {

    /** Initial form values. Null {@code sectionsEnabled} / {@code sectionsCount} keep the defaults. */
    protected static final class Prefill {
        final String mapName;
        final Boolean sectionsEnabled;
        final Integer sectionsCount;
        final String[] sectionNames; // one per level

        Prefill(String mapName, Boolean sectionsEnabled, Integer sectionsCount, String[] sectionNames) {
            this.mapName = mapName;
            this.sectionsEnabled = sectionsEnabled;
            this.sectionsCount = sectionsCount;
            this.sectionNames = sectionNames;
        }
    }

    private static final String[] ROMAN = { "I", "II", "III", "IV", "V" };
    private static final int ROW_H = 30;
    private static final int LABEL_W = 110;

    // Form state; survives screen rebuilds (window resize).
    private boolean initialized = false;
    private String mapName = "";
    private boolean sectionsEnabled = false;
    private int sectionsCount = 1;
    private final String[] sectionNames = new String[ParkourMap.MAX_SECTION_LEVELS];

    private UiTextField nameField;
    private UiSegmented countChoice;
    private final UiTextField[] nameFields = new UiTextField[ParkourMap.MAX_SECTION_LEVELS];

    GuiMapForm(GuiScreen parent) {
        super(parent);
    }

    // -------------------------
    // Subclass hooks
    // -------------------------

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

    @Override
    protected int getPanelWidth() {
        return 440;
    }

    @Override
    protected int getPanelHeight() {
        return 372;
    }

    private void initializeOnce() {
        if (initialized) return;
        initialized = true;

        Prefill prefill = getPrefill();
        mapName = prefill.mapName;
        if (prefill.sectionsEnabled != null) sectionsEnabled = prefill.sectionsEnabled.booleanValue();
        if (prefill.sectionsCount != null) sectionsCount = prefill.sectionsCount.intValue();
        for (int i = 0; i < ParkourMap.MAX_SECTION_LEVELS; i++) {
            sectionNames[i] = prefill.sectionNames[i];
        }
    }

    @Override
    protected void build(int x, int y, int w, int h) {
        initializeOnce();

        int fieldX = x + LABEL_W;
        int fieldW = w - LABEL_W;
        int rowY = y + 2;

        nameField = add(new UiTextField(mapName, 512).placeholder("Map name").onChange(t -> mapName = t), fieldX, rowY, fieldW, 20);
        nameField.setFocused(true);
        rowY += ROW_H;

        add(new UiToggle(sectionsEnabled, on -> sectionsEnabled = on.booleanValue()), fieldX, rowY + 2, 32, 16);
        rowY += ROW_H;

        countChoice = new UiSegmented(ROMAN, sectionsCount - 1, index -> sectionsCount = index + 1);
        add(countChoice, fieldX, rowY + 1, countChoice.preferredWidth(), 18);
        rowY += ROW_H;

        for (int i = 0; i < ParkourMap.MAX_SECTION_LEVELS; i++) {
            final int index = i;
            nameFields[i] = add(new UiTextField(sectionNames[i], 512).onChange(t -> sectionNames[index] = t),
                    fieldX, rowY, fieldW, 20);
            rowY += 28;
        }

        addFooter(
                new UiButton("Cancel", UiButton.Style.SECONDARY, this::onBack),
                new UiButton(getSubmitLabel(), UiButton.Style.PRIMARY, this::onSubmit));
    }

    @Override
    protected void drawContent(int mouseX, int mouseY, float partialTicks) {
        countChoice.visible = sectionsEnabled;
        for (int i = 0; i < ParkourMap.MAX_SECTION_LEVELS; i++) {
            nameFields[i].visible = sectionsEnabled && i < sectionsCount;
        }

        int rowY = contentY + 2;
        UiDraw.text("MAP NAME", contentX, rowY + 6, UiTheme.TEXT_MUTED);
        rowY += ROW_H;
        UiDraw.text("SECTIONS", contentX, rowY + 5, UiTheme.TEXT_MUTED);
        UiDraw.text(sectionsEnabled ? "On" : "Off", contentX + LABEL_W + 40, rowY + 5, UiTheme.TEXT);
        rowY += ROW_H;

        if (sectionsEnabled) {
            UiDraw.text("LEVELS", contentX, rowY + 5, UiTheme.TEXT_MUTED);
            rowY += ROW_H;
            for (int i = 0; i < sectionsCount; i++) {
                UiDraw.text("LEVEL " + ROMAN[i], contentX, rowY + 6, UiTheme.TEXT_MUTED);
                rowY += 28;
            }
        }
    }

    // -------------------------
    // Submit
    // -------------------------

    private void onSubmit() {
        String contextError = getContextError();
        if (contextError != null) {
            sendError(contextError);
            return;
        }

        String name = mapName != null ? mapName.trim() : "";
        if (name.length() == 0) {
            sendError("Map name cannot be empty.");
            return;
        }

        String[] names = null;
        int countToSave = 0;

        if (sectionsEnabled) {
            countToSave = sectionsCount;
            names = new String[ParkourMap.MAX_SECTION_LEVELS];
            for (int i = 0; i < ParkourMap.MAX_SECTION_LEVELS; i++) {
                String v = sectionNames[i];
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
    protected void onEnter() {
        onSubmit();
    }
}
