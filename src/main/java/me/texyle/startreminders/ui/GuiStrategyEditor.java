package me.texyle.startreminders.ui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiScreen;

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
        int rows = 2 + labels.length + (editMode ? 1 : 0);
        int rowH = Math.max(22, Math.min(28, h / rows));
        int fieldH = Math.max(16, Math.min(20, rowH - 4));

        int fieldX = x + LABEL_W;
        int fieldW = formW - LABEL_W;

        int rowY = y;

        // Jump name (read-only) is drawn in drawContent at this row.
        rowY += rowH;

        if (editMode) {
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
            rowY += rowH;
        }
        if (draft == null) {
            loadFields(editMode ? currentLines() : null);
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

    private static String summaryOf(Reminder r) {
        if (r == null || r.lines == null) return "(empty)";
        for (String s : r.lines) {
            if (s != null && s.trim().length() > 0) return s.trim();
        }
        return "(empty)";
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
        entries.add(Entry.item("Add another strategy", () ->
                Minecraft.getMinecraft().displayGuiScreen(GuiStrategyEditor.create(parent, after))));
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

        Minecraft.getMinecraft().displayGuiScreen(after);
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

        if (editMode) {
            boolean active = isCurrentActive();
            useButton.enabled = !active;
            useButton.setLabel(active ? "In use" : "Use in game");
        }

        String[] labels = restored ? LEGACY_LABELS : NEW_LABELS;
        int rows = 2 + labels.length + (editMode ? 1 : 0);
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

        if (editMode) {
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
