package me.texyle.startreminders.ui;

import java.util.ArrayList;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import me.texyle.startreminders.data.Jump;
import me.texyle.startreminders.data.ParkourMap;
import me.texyle.startreminders.gui.StrategyFormat;
import me.texyle.startreminders.persistence.BuiltinContainers;
import me.texyle.startreminders.reminders.Reminder;
import me.texyle.startreminders.reminders.SelectionState;
import me.texyle.startreminders.services.JumpService;
import me.texyle.startreminders.services.StrategyService;
import me.texyle.startreminders.utils.ChatUtils;

/**
 * Converts an old RestoredStrats entry into a proper Global jump with a strategy.
 * The old text is shown read-only on the left, the new format is edited on the right.
 */
public class GuiConvertLegacy extends UiScreen {

    private static final String[] NEW_LABELS = { "Position", "Facing", "Setup", "Strategy", "Strafe", "Turn", "Author", "Tips" };
    private static final int[] NEW_MAX_LENGTH = { 80, 80, 80, 80, 80, 80, 80, 160 };

    private static final int LABEL_W = 62;

    private final Jump legacyJump;
    private final String newJumpName;

    private UiTextField[] fields;
    private UiTextField[] legacyFields;
    private UiTextField coordX;
    private UiTextField coordY;
    private UiTextField coordZ;
    private UiButton saveButton;

    private int leftW;
    private int rowH;

    public GuiConvertLegacy(GuiScreen parent, Jump legacyJump, String newJumpName) {
        super(parent);
        this.legacyJump = legacyJump;
        this.newJumpName = (newJumpName != null) ? newJumpName.trim() : "";
    }

    @Override
    protected String getTitle() {
        return "Convert to Global: " + newJumpName;
    }

    @Override
    protected int getPanelWidth() {
        return 660;
    }

    @Override
    protected int getPanelHeight() {
        return 320;
    }

    @Override
    public void initGui() {
        if (newJumpName.isEmpty()) {
            Minecraft.getMinecraft().displayGuiScreen(parent);
            return;
        }
        super.initGui();
    }

    @Override
    protected void build(int x, int y, int w, int h) {
        leftW = (w - 12) / 2;
        int rightX = x + leftW + 12;
        int rightW = w - leftW - 12;

        rowH = Math.max(22, Math.min(26, h / 10));
        int fieldH = Math.max(16, Math.min(20, rowH - 4));
        int fieldX = rightX + LABEL_W;
        int fieldW = rightW - LABEL_W;

        int rowY = y + 24;

        // Old (read-only) side: the same rows as the new side, so both line up.
        int oldFieldX = x + LABEL_W;
        int oldFieldW = leftW - LABEL_W;
        int oldY = rowY;
        ArrayList<String> oldLines = getLegacyLines();
        UiTextField oldCoords = add(new UiTextField(legacyJump != null
                ? legacyJump.getX() + ", " + legacyJump.getY() + ", " + legacyJump.getZ() : "-", 40),
                oldFieldX, oldY, oldFieldW, fieldH);
        oldCoords.setReadOnly(true);
        oldY += rowH;
        String[] legacy = StrategyFormat.restoredEditorLines(oldLines);
        legacyFields = new UiTextField[legacy.length];
        for (int i = 0; i < legacy.length; i++) {
            legacyFields[i] = add(new UiTextField(legacy[i] != null ? legacy[i] : "", 200),
                    oldFieldX, oldY, oldFieldW, fieldH);
            legacyFields[i].setReadOnly(true);
            oldY += rowH;
        }

        int third = (fieldW - 8) / 3;
        int lx = legacyJump != null ? legacyJump.getX() : 0;
        int ly = legacyJump != null ? legacyJump.getY() : 0;
        int lz = legacyJump != null ? legacyJump.getZ() : 0;

        coordX = add(new UiTextField(Integer.toString(lx), 12), fieldX, rowY, third, fieldH);
        coordY = add(new UiTextField(Integer.toString(ly), 12), fieldX + third + 4, rowY, third, fieldH);
        coordZ = add(new UiTextField(Integer.toString(lz), 12), fieldX + (third + 4) * 2, rowY, third, fieldH);
        rowY += rowH;

        UiTextField[] previous = fields;
        fields = new UiTextField[NEW_LABELS.length];
        for (int i = 0; i < fields.length; i++) {
            fields[i] = add(new UiTextField(previous != null ? previous[i].getText() : "", NEW_MAX_LENGTH[i]),
                    fieldX, rowY, fieldW, fieldH);
            rowY += rowH;
        }
        fields[0].setFocused(true);

        saveButton = new UiButton("Convert", UiButton.Style.PRIMARY, this::save);
        addFooter(new UiButton("Cancel", UiButton.Style.SECONDARY, this::onBack), saveButton);
    }

    @Override
    protected void drawContent(int mouseX, int mouseY, float partialTicks) {
        coordX.setInvalid(!parsable(coordX));
        coordY.setInvalid(!parsable(coordY));
        coordZ.setInvalid(!parsable(coordZ));
        saveButton.enabled = parsable(coordX) && parsable(coordY) && parsable(coordZ);

        int rightX = contentX + leftW + 12;
        int cardH = contentH;

        // Old (read-only) side
        UiDraw.card(contentX, contentY, leftW, cardH, "Old strategy");

        int fieldH = Math.max(16, Math.min(20, rowH - 4));
        int labelOffset = (fieldH - 8) / 2 + 1;
        int oldRowY = contentY + 24;
        UiDraw.text("COORDS", contentX + UiTheme.PADDING, oldRowY + labelOffset, UiTheme.TEXT_MUTED);
        oldRowY += rowH;
        for (int i = 0; i < legacyFields.length; i++) {
            UiDraw.text("LINE " + (i + 1), contentX + UiTheme.PADDING, oldRowY + labelOffset, UiTheme.TEXT_MUTED);
            oldRowY += rowH;
        }

        // New (editable) side
        UiDraw.card(rightX, contentY, contentW - leftW - 12, cardH, "New strategy");

        int offset = (Math.max(16, Math.min(20, rowH - 4)) - 8) / 2 + 1;
        int rowY = contentY + 24;
        UiDraw.text("COORDS", rightX + UiTheme.PADDING, rowY + offset, UiTheme.TEXT_MUTED);
        rowY += rowH;
        for (String label : NEW_LABELS) {
            UiDraw.text(label.toUpperCase(), rightX + UiTheme.PADDING, rowY + offset, UiTheme.TEXT_MUTED);
            rowY += rowH;
        }
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

    private static boolean parsable(UiTextField f) {
        String s = f.getText().trim();
        if (s.isEmpty() || "-".equals(s)) return false;
        try {
            Integer.parseInt(s);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void save() {
        if (!saveButton.enabled) return;

        ParkourMap globalMap = BuiltinContainers.getGlobalMap();
        if (globalMap == null) {
            ChatUtils.error("Global map is not available.");
            return;
        }

        int x = Integer.parseInt(coordX.getText().trim());
        int y = Integer.parseInt(coordY.getText().trim());
        int z = Integer.parseInt(coordZ.getText().trim());

        Jump j = JumpService.getOrCreateJumpByNameAndCoords(globalMap, newJumpName, x, y, z);
        if (j == null) {
            ChatUtils.error("Failed to create Global jump.");
            return;
        }

        SelectionState.setSelectedMap(globalMap);
        SelectionState.setSelectedJump(j);

        // Persist jump-level coords
        j.setX(x);
        j.setY(y);
        j.setZ(z);

        // New format lines: [Position, Facing, Setup, Strategy, Strafe, Turn, Author, Tips]
        ArrayList<String> lines = new ArrayList<String>();
        for (UiTextField f : fields) {
            lines.add(f.getText().trim());
        }

        StrategyService.createReminder(lines, x, y, z);

        // Remove the original legacy jump from RestoredStrats after a successful conversion.
        boolean removed = JumpService.removeJumpFromRestoredStrats(legacyJump);
        if (!removed) {
            ChatUtils.error("Converted, but failed to remove the legacy entry from RestoredStrats.");
        } else {
            ChatUtils.info("Converted and removed legacy entry from RestoredStrats.");
        }

        Minecraft.getMinecraft().displayGuiScreen(parent);
    }

    @Override
    protected void onEnter() {
        save();
    }
}
