package me.texyle.startreminders.ui;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.MathHelper;
import org.lwjgl.input.Mouse;

import me.texyle.startreminders.data.Jump;
import me.texyle.startreminders.data.ParkourMap;
import me.texyle.startreminders.gui.StrategyFormat;
import me.texyle.startreminders.gui.hierarchy.GuiConfirm;
import me.texyle.startreminders.persistence.DataStoreRepository;
import me.texyle.startreminders.reminders.JumpPicker;
import me.texyle.startreminders.reminders.Reminder;
import me.texyle.startreminders.reminders.SelectionState;
import me.texyle.startreminders.reminders.UserPreferences;
import me.texyle.startreminders.services.JumpService;
import me.texyle.startreminders.services.StrategyService;
import me.texyle.startreminders.ui.UiPopup.Entry;
import me.texyle.startreminders.utils.ChatUtils;
import me.texyle.startreminders.utils.PlayerBlockPos;

/**
 * Creates a strategy for the selected jump, or edits the strategies it already has.
 * One form replaces the former separate "create" and "edit" screens; the preview shows how the text
 * will look in the world.
 */
public class GuiStrategyEditor extends UiScreen {

    private static final String[] NEW_LABELS = { "Position", "Facing", "Setup", "Strategy", "Strafe", "Turn", "Author", "Tips" };
    private static final int[] NEW_MAX_LENGTH = { 80, 80, 80, 80, 80, 80, 80, 160 };
    private static final String[] LEGACY_LABELS = { "Line 1", "Line 2", "Line 3", "Line 4", "Line 5" };

    private static final int LABEL_W = 72;
    private static final int PREVIEW_W = 210;

    /** Where "Save" returns to; null closes all screens. */
    private final GuiScreen after;
    private final boolean editMode;
    /** Set when opened from the editor's "Add another strategy": after saving, go back to the editor. */
    private boolean returnToEditor;
    private final boolean restored = SelectionState.isRestoredContext();

    private ArrayList<Reminder> reminders = new ArrayList<Reminder>();
    private int index = 0;

    private UiTextField[] fields;
    private UiTextField coordX;
    private UiTextField coordY;
    private UiTextField coordZ;
    private UiButton saveButton;
    private UiButton useButton;
    private UiButton moreButton;
    private UiDropdown strategyDropdown;

    // Values typed before a rebuild (window resize), restored by build().
    private String[] draft;
    private String[] draftCoords;

    private int previewX;
    private int previewW;

    // -------------------------
    // Factories
    // -------------------------

    /** Editor for a new strategy of the currently selected jump. */
    public static GuiStrategyEditor create(GuiScreen back, GuiScreen after) {
        return new GuiStrategyEditor(back, after, false);
    }

    /** Editor for one more strategy of the jump being edited; saving returns to that jump's editor. */
    private static GuiStrategyEditor createFromEditor(GuiScreen back, GuiScreen after) {
        GuiStrategyEditor editor = new GuiStrategyEditor(back, after, false);
        editor.returnToEditor = true;
        return editor;
    }

    /**
     * Editor for the strategies of the selected jump. When opened from a key (no {@code back} screen) the jump
     * is picked first, by the configured mode (nearest or crosshair).
     *
     * @return the screen to show, or null if there is nothing to open
     */
    public static GuiScreen forEdit(GuiScreen back, GuiScreen after) {
        if (back == null) {
            if (!pickJumpForKeybind()) {
                return null;
            }
        } else if (SelectionState.getSelectedJump() == null) {
            JumpPicker.selectNearestJumpToPlayer();
        }

        if (StrategyService.getReminderList().isEmpty()) {
            // No strategies yet: go straight to creating the first one.
            return new GuiStrategyEditor(back, after, false);
        }
        return new GuiStrategyEditor(back, after, true);
    }

    private static boolean pickJumpForKeybind() {
        if (!UserPreferences.isEditPickModeInCrosshair()) {
            JumpPicker.selectNearestJumpToPlayer();
            return SelectionState.getSelectedJump() != null;
        }

        Jump picked = JumpPicker.selectJumpInCrosshair(50.0D, 1.75D);
        if (picked == null) {
            // Do NOT fall back to nearest: the user asked for the crosshair.
            ChatUtils.warning("No strategies in visible range.");
            return false;
        }
        return true;
    }

    private GuiStrategyEditor(GuiScreen back, GuiScreen after, boolean editMode) {
        super(back);
        this.after = after;
        this.editMode = editMode;

        if (editMode) {
            reminders = new ArrayList<Reminder>(StrategyService.getReminderList());
            Jump jump = SelectionState.getSelectedJump();
            if (jump != null) {
                int active = jump.getActiveReminderIndex();
                if (active >= 0 && active < reminders.size()) {
                    index = active;
                }
            }
        }
    }

    // -------------------------
    // Layout
    // -------------------------

    @Override
    protected String getTitle() {
        return editMode ? "Edit strategy" : "New strategy";
    }

    @Override
    protected int getPanelWidth() {
        return 600;
    }

    @Override
    protected int getPanelHeight() {
        return 330;
    }

    @Override
    protected void build(int x, int y, int w, int h) {
        rememberDraft();

        boolean showPreview = w >= 460;
        int formW = showPreview ? w - PREVIEW_W - 12 : w;
        previewX = x + formW + 12;
        previewW = showPreview ? PREVIEW_W : 0;

        String[] labels = restored ? LEGACY_LABELS : NEW_LABELS;
        int rows = 2 + labels.length + (hasStrategyRow() ? 1 : 0);
        int rowH = Math.max(22, Math.min(28, h / rows));
        int fieldH = Math.max(16, Math.min(20, rowH - 4));

        int fieldX = x + LABEL_W;
        int fieldW = formW - LABEL_W;

        int rowY = y;

        // Jump name (read-only) is drawn in drawContent at this row.
        rowY += rowH;

        if (hasStrategyRow()) {
            strategyDropdown = add(new UiDropdown(popup, "Strategy", this::strategyLabel, this::strategyEntries),
                    fieldX, rowY, Math.min(190, fieldW), fieldH);
            useButton = add(new UiButton("Use in game", UiButton.Style.SECONDARY, this::useCurrentStrategy),
                    fieldX + Math.min(190, fieldW) + 4, rowY, 90, fieldH);
            rowY += rowH;
        }

        // Coordinates
        int third = (fieldW - 12 - 82) / 3;
        coordX = add(new UiTextField(draftCoords != null ? draftCoords[0] : "0", 12), fieldX, rowY, third, fieldH);
        coordY = add(new UiTextField(draftCoords != null ? draftCoords[1] : "0", 12), fieldX + third + 4, rowY, third, fieldH);
        coordZ = add(new UiTextField(draftCoords != null ? draftCoords[2] : "0", 12), fieldX + (third + 4) * 2, rowY, third, fieldH);
        int positionX = fieldX + (third + 4) * 3;
        add(new UiButton("My position", UiButton.Style.SECONDARY, this::useMyPosition),
                positionX, rowY, fieldX + fieldW - positionX, fieldH);
        if (draftCoords == null) {
            applyInitialCoordinates();
        }
        rowY += rowH;

        fields = new UiTextField[labels.length];
        for (int i = 0; i < labels.length; i++) {
            int max = restored ? 160 : NEW_MAX_LENGTH[i];
            fields[i] = add(new UiTextField(draft != null ? draft[i] : "", max), fieldX, rowY, fieldW, fieldH);
            if (i == 0 && !restored) {
                // ">" right of the POSITION label: fills the field with where the player stands.
                add(new PositionFillButton(), contentX + UiDraw.width("POSITION") + 5, rowY, 12, fieldH);
            }
            rowY += rowH;
        }
        if (draft == null) {
            loadFields(editMode ? currentLines() : null);
            if (!editMode && !restored) {
                // A new strategy starts with the facing the player has right now.
                fields[1].setText(facingText());
            }
        }
        fields[0].setFocused(true);

        saveButton = new UiButton("Save", UiButton.Style.PRIMARY, this::save);
        UiButton cancel = new UiButton("Cancel", UiButton.Style.SECONDARY, this::onBack);
        if (editMode) {
            moreButton = new UiButton("More", UiButton.Style.GHOST, this::openMoreMenu);
            addFooter(moreButton, cancel, saveButton);
        } else {
            addFooter(cancel, saveButton);
        }

        // Last, so its slider is drawn above the form.
        add(new PlayerInfoWidget(), contentX, footerY(), Math.max(60, contentW - 250), UiTheme.CONTROL_HEIGHT);

        draft = null;
        draftCoords = null;
    }

    private void rememberDraft() {
        if (fields == null) return;
        draft = new String[fields.length];
        for (int i = 0; i < fields.length; i++) draft[i] = fields[i].getText();
        draftCoords = new String[] { coordX.getText(), coordY.getText(), coordZ.getText() };
    }

    private void applyInitialCoordinates() {
        int sx = 0;
        int sy = 0;
        int sz = 0;

        Jump jump = SelectionState.getSelectedJump();
        if (jump != null) {
            sx = jump.getX();
            sy = jump.getY();
            sz = jump.getZ();
        }

        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (sx == 0 && sy == 0 && sz == 0 && player != null) {
            sx = (int) Math.floor(player.posX);
            sy = (int) Math.floor(player.posY) + 1;
            sz = (int) Math.floor(player.posZ);
        }

        coordX.setText(Integer.toString(sx));
        coordY.setText(Integer.toString(sy));
        coordZ.setText(Integer.toString(sz));
    }

    private void useMyPosition() {
        EntityPlayerSP p = Minecraft.getMinecraft().thePlayer;
        if (p == null) return;

        coordX.setText(Integer.toString(PlayerBlockPos.x(p)));
        coordY.setText(Integer.toString(PlayerBlockPos.jumpY(p)));
        coordZ.setText(Integer.toString(PlayerBlockPos.z(p)));
    }

    // -------------------------
    // Strategies (edit mode)
    // -------------------------

    private ArrayList<String> currentLines() {
        return (index >= 0 && index < reminders.size()) ? reminders.get(index).lines : null;
    }

    private Reminder currentReminder() {
        return (index >= 0 && index < reminders.size()) ? reminders.get(index) : null;
    }

    /** The strategy picker (and "Use in game") exists only for the new format, which can hold several strategies. */
    private boolean hasStrategyRow() {
        return editMode && !restored;
    }

    private String strategyLabel() {
        return (index + 1) + " / " + reminders.size();
    }

    private List<Entry> strategyEntries() {
        List<Entry> entries = new ArrayList<Entry>();
        for (int i = 0; i < reminders.size(); i++) {
            final int target = i;
            entries.add(Entry.choice((i + 1) + ") " + summaryOf(reminders.get(i)), i == index, () -> showStrategy(target)));
        }
        return entries;
    }

    /** What a strategy is listed as: its Strategy line, so the entries can be told apart. */
    private static String summaryOf(Reminder r) {
        String strategy = ReminderColumns.field(r, 1).trim();
        return strategy.isEmpty() ? "(empty)" : strategy;
    }

    private void showStrategy(int newIndex) {
        if (newIndex < 0 || newIndex >= reminders.size()) return;
        index = newIndex;
        loadFields(currentLines());
    }

    private void useCurrentStrategy() {
        Reminder r = currentReminder();
        if (r != null) {
            StrategyService.setActiveReminder(r);
            ChatUtils.info("Selected strategy");
        }
    }

    private boolean isCurrentActive() {
        Jump jump = SelectionState.getSelectedJump();
        return jump != null && jump.getActiveReminderIndex() == index;
    }

    private void openMoreMenu() {
        List<Entry> entries = new ArrayList<Entry>();
        // The old format holds a single strategy per jump, so there is nothing to add there.
        if (!restored) {
            entries.add(Entry.item("Add another strategy", () ->
                    Minecraft.getMinecraft().displayGuiScreen(GuiStrategyEditor.createFromEditor(parent, after))));
        }
        entries.add(Entry.item("Back to sheet", this::backToSheet));
        entries.add(Entry.danger("Delete this strategy...", true, this::confirmDelete));

        popup.open(moreButton.x, moreButton.y - entries.size() * 18 - 12, 200, entries);
    }

    /** Leaves the editor for the sheet, with the edited jump selected and scrolled into view. */
    private void backToSheet() {
        GuiScreen sheet = (parent instanceof GuiHub) ? parent : new GuiHub(null);
        Minecraft.getMinecraft().displayGuiScreen(sheet);
    }

    private void confirmDelete() {
        final GuiScreen self = this;
        Minecraft.getMinecraft().displayGuiScreen(new GuiConfirm(self, "Delete strategy", "Delete this strategy?",
                new GuiConfirm.IConfirmHandler() {
                    @Override
                    public void onYes() {
                        deleteCurrent();
                    }

                    @Override
                    public void onNo() { }
                }));
    }

    private void deleteCurrent() {
        StrategyService.deleteReminder(currentReminder());

        reminders = new ArrayList<Reminder>(StrategyService.getReminderList());
        if (reminders.isEmpty()) {
            // Nothing left: continue with a fresh strategy for the same jump.
            Minecraft.getMinecraft().displayGuiScreen(GuiStrategyEditor.create(parent, after));
            return;
        }

        index = 0;
        loadFields(currentLines());
    }

    // -------------------------
    // Fields <-> lines
    // -------------------------

    /** Fills the form from a strategy's lines (null = empty form). */
    private void loadFields(ArrayList<String> lines) {
        if (restored) {
            String[] v = StrategyFormat.restoredEditorLines(lines);
            for (int i = 0; i < fields.length; i++) fields[i].setText(v[i]);
            return;
        }

        for (int i = 0; i < fields.length; i++) fields[i].setText("");
        if (lines == null) return;

        /*
         * Supported formats:
         * 1) New (8): [Position, Facing, Setup, Strategy, Strafe, Turn, Author, Tips]
         * 2) Previous (6): [Setup, Strategy, Strafe, Turn, Author, Tips]
         * 3) Legacy (<=5): [Strategy, Strafe, Turn, Author, Tips] (no Setup)
         */
        if (lines.size() >= 8) {
            for (int i = 0; i < 8; i++) fields[i].setText(StrategyFormat.orEmpty(lines.get(i)));
        } else if (lines.size() >= 6) {
            for (int i = 0; i < 6; i++) fields[2 + i].setText(StrategyFormat.orEmpty(lines.get(i)));
        } else {
            for (int i = 0; i < lines.size(); i++) fields[3 + i].setText(StrategyFormat.orEmpty(lines.get(i)));
        }
    }

    private String trimmed(int i) {
        return fields[i].getText().trim();
    }

    private boolean coordinatesValid() {
        return parsable(coordX) && parsable(coordY) && parsable(coordZ);
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

    private boolean hasAnyText() {
        for (UiTextField f : fields) {
            if (f.getText().trim().length() > 0) return true;
        }
        return false;
    }

    private boolean canSave() {
        // The RestoredStrats editor only requires valid coordinates; the new editor also needs some text.
        return coordinatesValid() && (restored || hasAnyText());
    }

    // -------------------------
    // Save
    // -------------------------

    private void save() {
        if (!canSave()) return;

        int x = Integer.parseInt(coordX.getText().trim());
        int y = Integer.parseInt(coordY.getText().trim());
        int z = Integer.parseInt(coordZ.getText().trim());

        if (editMode) {
            updateReminder(x, y, z);
        } else if (restored) {
            saveLegacyStrategy(x, y, z);
        } else {
            saveNewStrategy(x, y, z);
        }

        GuiScreen next = after;
        if (returnToEditor && !editMode && !restored) {
            // Back to the jump's editor, showing the strategy that was just added (the last one).
            GuiStrategyEditor editor = new GuiStrategyEditor(parent, after, true);
            if (!editor.reminders.isEmpty()) editor.index = editor.reminders.size() - 1;
            next = editor;
        }
        Minecraft.getMinecraft().displayGuiScreen(next);
    }

    private void saveLegacyStrategy(int x, int y, int z) {
        ParkourMap map = SelectionState.getSelectedMap();
        if (map == null) {
            return;
        }

        Jump sel = SelectionState.getSelectedJump();
        if (sel == null) {
            String newJumpId = "Jump" + (JumpService.getJumps(map).size() + 1);
            sel = JumpService.getOrCreateJumpByNameAndCoords(map, newJumpId, x, y, z);
            SelectionState.setSelectedJump(sel);
        }

        if (sel != null) {
            sel.setX(x);
            sel.setY(y);
            sel.setZ(z);
        }

        ArrayList<String> lines = new ArrayList<String>();
        for (int i = 0; i < fields.length; i++) lines.add(trimmed(i));

        StrategyService.createReminder(lines, x, y, z);
    }

    private void saveNewStrategy(int x, int y, int z) {
        Jump jump = SelectionState.getSelectedJump();
        if (jump != null) {
            jump.setX(x);
            jump.setY(y);
            jump.setZ(z);
        }

        ArrayList<String> lines = new ArrayList<String>();
        for (int i = 0; i < 8; i++) lines.add(trimmed(i));

        StrategyService.createReminder(lines, x, y, z);
    }

    private void updateReminder(int x, int y, int z) {
        Reminder reminder = currentReminder();
        if (reminder == null) return;

        ArrayList<String> out;
        if (restored) {
            out = StrategyFormat.applyRestoredEdit(reminder.lines,
                    trimmed(0), trimmed(1), trimmed(2), trimmed(3), trimmed(4));
        } else {
            // New format: [Position, Facing, Setup, Strategy, Strafe, Turn, Author, Tips]
            out = new ArrayList<String>();
            for (int i = 0; i < 8; i++) out.add(trimmed(i));
        }
        reminder.lines = out;

        // Persist ONLY jump-level coordinates (shared within jump container).
        Jump jump = SelectionState.getSelectedJump();
        if (jump != null) {
            jump.setX(x);
            jump.setY(y);
            jump.setZ(z);
        }

        ChatUtils.info("Strategy updated");
        DataStoreRepository.save();
    }

    // -------------------------
    // Drawing
    // -------------------------

    @Override
    protected void drawContent(int mouseX, int mouseY, float partialTicks) {
        saveButton.enabled = canSave();
        coordX.setInvalid(!parsable(coordX));
        coordY.setInvalid(!parsable(coordY));
        coordZ.setInvalid(!parsable(coordZ));

        if (hasStrategyRow()) {
            boolean active = isCurrentActive();
            useButton.enabled = !active;
            useButton.setLabel(active ? "In use" : "Use in game");
        }

        String[] labels = restored ? LEGACY_LABELS : NEW_LABELS;
        int rows = 2 + labels.length + (hasStrategyRow() ? 1 : 0);
        int rowH = Math.max(22, Math.min(28, contentH / rows));
        int fieldH = Math.max(16, Math.min(20, rowH - 4));
        int textOffset = (fieldH - 8) / 2 + 1;

        int rowY = contentY;
        Jump jump = SelectionState.getSelectedJump();
        UiDraw.text("JUMP", contentX, rowY + textOffset, UiTheme.TEXT_MUTED);
        String name = (jump != null && jump.getId() != null) ? jump.getId() : "<none>";
        UiDraw.text(UiDraw.fit(name, contentW - LABEL_W - (previewW > 0 ? PREVIEW_W + 12 : 0)),
                contentX + LABEL_W, rowY + textOffset, UiTheme.TEXT);
        rowY += rowH;

        if (hasStrategyRow()) {
            UiDraw.text("STRATEGY", contentX, rowY + textOffset, UiTheme.TEXT_MUTED);
            rowY += rowH;
        }

        UiDraw.text("COORDS", contentX, rowY + textOffset, UiTheme.TEXT_MUTED);
        rowY += rowH;

        for (String label : labels) {
            UiDraw.text(label.toUpperCase(), contentX, rowY + textOffset, UiTheme.TEXT_MUTED);
            rowY += rowH;
        }

        if (previewW > 0) {
            drawPreview(jump);
        }

    }

    /** The player's facing as shown in F3, one decimal, e.g. "-12.3". */
    private static String facingText() {
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (player == null) return "";
        return decimalString(MathHelper.wrapAngleTo180_float(player.rotationYaw), 1, RoundingMode.HALF_UP);
    }

    /** Only the three decimals of X and Z, e.g. "x.345 z.890"; Y is left out. */
    private static String positionText() {
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (player == null) return "";
        return "x" + fraction(player.posX, 3) + " z" + fraction(player.posZ, 3);
    }

    private void fillPosition() {
        final String position = positionText();
        if (position.isEmpty()) return;

        if (fields[0].getText().trim().isEmpty()) {
            fields[0].setText(position);
            return;
        }

        Minecraft.getMinecraft().displayGuiScreen(new GuiConfirm(this, "Overwrite position",
                "POSITION is not empty. Overwrite it?", new GuiConfirm.IConfirmHandler() {
                    @Override
                    public void onYes() {
                        fields[0].setText(position);
                    }

                    @Override
                    public void onNo() { }
                }));
    }

    /** The small ">" next to the POSITION label. */
    private final class PositionFillButton extends UiWidget {
        @Override
        public void draw(int mouseX, int mouseY) {
            boolean hover = contains(mouseX, mouseY);
            UiDraw.text(">", x + 2, y + (h - 8) / 2 + 1, hover ? UiTheme.TEXT : UiTheme.TEXT_MUTED);
        }

        @Override
        public boolean mouseClicked(int mouseX, int mouseY, int button) {
            if (button != 0 || !contains(mouseX, mouseY)) return false;
            fillPosition();
            return true;
        }
    }

    /**
     * Bottom-left: where the player stands right now, in the form parkour cares about: only the decimals of X and Z
     * (".nnn"), the block height, and the facing. The arrow opens a slider that adds up to five more decimals to
     * every value; the choice is a global setting.
     */
    private final class PlayerInfoWidget extends UiWidget {

        private static final int SLIDER_W = 150;
        private static final int SLIDER_H = 20;
        private static final int TRACK_PAD = 10;
        private static final int TRACK_W = 84;

        private boolean open;
        private boolean dragging;
        private int arrowX;

        @Override
        public void draw(int mouseX, int mouseY) {
            EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
            if (player == null) return;

            int extra = UserPreferences.getPlayerInfoExtraDecimals();
            String[][] parts = {
                    { "x: ", fraction(player.posX, 3 + extra) },
                    { "y: ", decimalString(player.posY, 2 + extra, RoundingMode.FLOOR) },
                    { "z: ", fraction(player.posZ, 3 + extra) },
                    { "F: ", decimalString(MathHelper.wrapAngleTo180_float(player.rotationYaw), 1 + extra, RoundingMode.HALF_UP) }
            };

            int tx = x;
            int ty = y + (UiTheme.CONTROL_HEIGHT - 8) / 2;
            for (String[] part : parts) {
                UiDraw.text(part[0], tx, ty, UiTheme.TEXT_MUTED);
                tx += UiDraw.width(part[0]);
                UiDraw.text(part[1], tx, ty, UiTheme.TEXT);
                tx += UiDraw.width(part[1]) + 10;
            }

            arrowX = tx;
            boolean hover = UiDraw.inside(mouseX, mouseY, arrowX - 3, y, 14, UiTheme.CONTROL_HEIGHT);
            UiDraw.text(">", arrowX, ty, open || hover ? UiTheme.TEXT : UiTheme.TEXT_MUTED);

            if (open) drawSlider(mouseX);
        }

        // Horizontally centered on the screen, just above the readout's row. It must not follow the arrow, whose
        // position changes with the slider value (that made it jump away from the mouse while dragging).
        private int sliderX() {
            return (GuiStrategyEditor.this.width - SLIDER_W) / 2;
        }

        private int sliderY() {
            return y - SLIDER_H - 4;
        }

        private void drawSlider(int mouseX) {
            int bx = sliderX();
            int by = sliderY();
            UiDraw.box(bx, by, SLIDER_W, SLIDER_H, UiTheme.PANEL_BG | 0xFF000000, UiTheme.CONTROL_BORDER);

            int trackX = bx + TRACK_PAD;
            int trackY = by + SLIDER_H / 2;

            if (dragging) {
                if (Mouse.isButtonDown(0)) {
                    setFromMouse(mouseX, trackX);
                } else {
                    dragging = false;
                }
            }

            int value = UserPreferences.getPlayerInfoExtraDecimals();
            UiDraw.fill(trackX, trackY, TRACK_W, 2, UiTheme.CARD_BORDER);
            UiDraw.fill(trackX, trackY, TRACK_W * value / 5, 2, UiTheme.ACCENT);
            for (int i = 0; i <= 5; i++) {
                UiDraw.fill(trackX + TRACK_W * i / 5, trackY - 3, 1, 8, i <= value ? UiTheme.ACCENT_HOVER : UiTheme.CONTROL_BORDER);
            }
            int thumbX = trackX + TRACK_W * value / 5;
            UiDraw.box(thumbX - 3, trackY - 6, 6, 14, UiTheme.ACCENT, UiTheme.ACCENT_HOVER);

            UiDraw.text("+" + value, bx + SLIDER_W - 26, by + (SLIDER_H - 8) / 2, UiTheme.TEXT);
        }

        private void setFromMouse(int mouseX, int trackX) {
            double t = (mouseX - trackX) / (double) TRACK_W;
            int value = (int) Math.round(Math.max(0.0, Math.min(1.0, t)) * 5);
            if (value != UserPreferences.getPlayerInfoExtraDecimals()) {
                UserPreferences.setPlayerInfoExtraDecimals(value);
            }
        }

        @Override
        public boolean mouseClicked(int mouseX, int mouseY, int button) {
            if (button != 0) return false;

            if (UiDraw.inside(mouseX, mouseY, arrowX - 3, y, 14, UiTheme.CONTROL_HEIGHT)) {
                open = !open;
                dragging = false;
                return true;
            }

            if (open && UiDraw.inside(mouseX, mouseY, sliderX(), sliderY(), SLIDER_W, SLIDER_H)) {
                int trackX = sliderX() + TRACK_PAD;
                if (mouseX >= trackX - 6 && mouseX <= trackX + TRACK_W + 6) {
                    dragging = true;
                    setFromMouse(mouseX, trackX);
                }
                return true;
            }

            if (open) {
                // A click anywhere else closes the slider and is not passed on, so it cannot hit a button by accident.
                open = false;
                dragging = false;
                return true;
            }
            return false;
        }
    }

    /** The decimals of a coordinate with the given number of digits, e.g. -12.3456 with 3 gives ".345". */
    private static String fraction(double value, int digits) {
        String full = decimalString(Math.abs(value), digits, RoundingMode.DOWN);
        return full.substring(full.indexOf('.'));
    }

    private static String decimalString(double value, int digits, RoundingMode mode) {
        return new BigDecimal(value).setScale(digits, mode).toPlainString();
    }

    /** Approximates the in-world text: position, facing and setup on one line, then strategy, strafe, turn. */
    private void drawPreview(Jump jump) {
        int ph = Math.min(contentH, 200);
        UiDraw.card(previewX, contentY, previewW, ph, "Preview");

        int areaX = previewX + UiTheme.PADDING;
        int areaY = contentY + 24;
        int areaW = previewW - UiTheme.PADDING * 2;
        int areaH = ph - 24 - UiTheme.PADDING;

        UiDraw.fill(areaX, areaY, areaW, areaH, 0xFF262C38);
        UiDraw.fill(areaX, areaY + areaH * 2 / 3, areaW, areaH - areaH * 2 / 3, 0xFF2F3A2C);

        String body = UserPreferences.getGlobalInWorldTextColor();
        List<String> lines = new ArrayList<String>();

        if (!restored && UserPreferences.isGlobalShowJumpNameEnabled() && jump != null && jump.getId() != null) {
            lines.add(UserPreferences.getGlobalInWorldJumpNameColor() + jump.getId());
        }

        if (restored) {
            for (int i = 0; i < fields.length; i++) {
                if (!trimmed(i).isEmpty()) lines.add(body + trimmed(i));
            }
        } else {
            StringBuilder first = new StringBuilder();
            append(first, trimmed(0));
            if (!trimmed(1).isEmpty()) append(first, "F: " + trimmed(1));
            append(first, trimmed(2));
            if (first.length() > 0) lines.add(body + first);
            if (!trimmed(3).isEmpty()) lines.add(body + trimmed(3));
            if (!trimmed(4).isEmpty()) lines.add(body + "Strafe: " + trimmed(4));
            if (!trimmed(5).isEmpty()) lines.add(body + "Turn: " + trimmed(5));
        }

        if (lines.isEmpty()) {
            UiDraw.centered("Type to see a preview", areaX + areaW / 2, areaY + areaH / 2 - 4, UiTheme.TEXT_DISABLED);
            return;
        }

        int lineH = UiDraw.font().FONT_HEIGHT + 3;
        int cy = areaY + Math.max(4, (areaH - lines.size() * lineH) / 2);
        for (String line : lines) {
            UiDraw.centered(UiDraw.fit(line, areaW - 6), areaX + areaW / 2, cy, 0xFFFFFFFF);
            cy += lineH;
        }
    }

    private static void append(StringBuilder sb, String part) {
        if (part.isEmpty()) return;
        if (sb.length() > 0) sb.append(' ');
        sb.append(part);
    }

    @Override
    protected void onEnter() {
        save();
    }

    @Override
    protected void onBack() {
        Minecraft.getMinecraft().displayGuiScreen(parent);
    }
}
