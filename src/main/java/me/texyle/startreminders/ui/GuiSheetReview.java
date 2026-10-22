package me.texyle.startreminders.ui;

import java.io.IOException;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

import me.texyle.startreminders.data.Jump;
import me.texyle.startreminders.data.ParkourMap;
import me.texyle.startreminders.data.SheetFilter;
import me.texyle.startreminders.sheets.SheetData;
import me.texyle.startreminders.sheets.SheetImportService;

/**
 * The step between downloading a sheet and importing it: the sheet in the familiar table, where the first and last
 * jump to take can be marked and single jumps (a row that only separates things, notes at the end) unchecked.
 * The result is remembered by the map, so syncing later takes the same part of the sheet.
 */
public class GuiSheetReview extends UiScreen implements UiJumpTable.Review {

    /** Receives the filter when the player confirms; null means "everything". */
    public interface Confirm {
        void onConfirm(SheetFilter filter);
    }

    private final String title;
    private final String confirmLabel;
    private final Confirm confirm;

    private final ParkourMap preview;
    private final List<Jump> jumps;
    private final Map<Jump, Integer> indexOf = new IdentityHashMap<Jump, Integer>();

    private final Set<String> unchecked = new HashSet<String>();
    private int startIdx;
    private int endIdx;

    private final UiJumpTable table = new UiJumpTable();
    private UiButton startButton;
    private UiButton endButton;

    public GuiSheetReview(GuiScreen parent, SheetData data, SheetFilter initial, String title, String confirmLabel,
                          Confirm confirm) {
        super(parent);
        this.title = title;
        this.confirmLabel = confirmLabel;
        this.confirm = confirm;

        preview = SheetImportService.preview(data);
        jumps = preview.getJumps();
        for (int i = 0; i < jumps.size(); i++) indexOf.put(jumps.get(i), Integer.valueOf(i));

        startIdx = 0;
        endIdx = Math.max(0, jumps.size() - 1);
        if (initial != null) {
            int s = indexOfKey(initial.getStartKey());
            int e = indexOfKey(initial.getEndKey());
            if (s >= 0) startIdx = s;
            if (e >= 0) endIdx = e;
            if (startIdx > endIdx) {
                startIdx = 0;
                endIdx = Math.max(0, jumps.size() - 1);
            }
            unchecked.addAll(initial.getExcludedKeys());
        }
    }

    // -------------------------
    // Layout
    // -------------------------

    @Override
    protected String getTitle() {
        return title;
    }

    @Override
    protected int getPanelWidth() {
        return this.width - 40;
    }

    @Override
    protected int getPanelHeight() {
        return this.height - 30;
    }

    @Override
    protected void build(int x, int y, int w, int h) {
        table.setPopup(popup);
        table.setReview(this);
        table.setMap(preview);
        table.setEmptyText("The sheet has no jumps.");
        add(table, x, y, w, h - 4);
        if (table.getSelectedJump() == null && !jumps.isEmpty()) {
            table.selectJump(jumps.get(startIdx));
        }

        startButton = new UiButton("Start here", UiButton.Style.SECONDARY, this::startHere);
        endButton = new UiButton("End here", UiButton.Style.SECONDARY, this::endHere);
        UiButton reset = new UiButton("Reset", UiButton.Style.SECONDARY, this::reset);
        UiButton cancel = new UiButton("Cancel", UiButton.Style.SECONDARY, this::onBack);
        UiButton ok = new UiButton(confirmLabel, UiButton.Style.PRIMARY, this::finish);
        addFooter(startButton, endButton, reset, cancel, ok);
    }

    @Override
    protected void drawContent(int mouseX, int mouseY, float partialTicks) {
        boolean selected = table.getSelectedJump() != null;
        startButton.enabled = selected;
        endButton.enabled = selected;

        int included = 0;
        for (Jump jump : jumps) {
            if (isIncluded(jump)) included++;
        }

        String text = "Importing " + included + " of " + jumps.size() + " jumps";
        if (startIdx > 0 || endIdx < jumps.size() - 1) {
            text += "   (from #" + (startIdx + 1) + " to #" + (endIdx + 1) + ")";
        }
        int room = contentW - 5 * 80 - 40;
        UiDraw.text(UiDraw.fit(text, Math.max(60, room)), contentX, footerY() + (UiTheme.CONTROL_HEIGHT - 8) / 2 + 1, UiTheme.TEXT_MUTED);
    }

    // -------------------------
    // Actions
    // -------------------------

    private void startHere() {
        Integer index = indexOf.get(table.getSelectedJump());
        if (index == null) return;

        startIdx = index.intValue();
        if (endIdx < startIdx) endIdx = jumps.size() - 1;
    }

    private void endHere() {
        Integer index = indexOf.get(table.getSelectedJump());
        if (index == null) return;

        endIdx = index.intValue();
        if (startIdx > endIdx) startIdx = 0;
    }

    private void reset() {
        startIdx = 0;
        endIdx = Math.max(0, jumps.size() - 1);
        unchecked.clear();
    }

    private void finish() {
        SheetFilter filter = new SheetFilter();
        if (startIdx > 0) filter.setStartKey(keyOf(startIdx));
        if (endIdx < jumps.size() - 1) filter.setEndKey(keyOf(endIdx));
        for (Jump jump : jumps) {
            if (unchecked.contains(jump.getSheetRowKey())) filter.getExcludedKeys().add(jump.getSheetRowKey());
        }

        Minecraft.getMinecraft().displayGuiScreen(parent);
        confirm.onConfirm(filter.isEmpty() ? null : filter);
    }

    // -------------------------
    // UiJumpTable.Review
    // -------------------------

    @Override
    public boolean isIncluded(Jump jump) {
        return isChecked(jump) && isInCrop(jump);
    }

    @Override
    public boolean isChecked(Jump jump) {
        return !unchecked.contains(jump.getSheetRowKey());
    }

    private boolean isInCrop(Jump jump) {
        Integer index = indexOf.get(jump);
        return index != null && index.intValue() >= startIdx && index.intValue() <= endIdx;
    }

    @Override
    public boolean isCropStart(Jump jump) {
        Integer index = indexOf.get(jump);
        return index != null && startIdx > 0 && index.intValue() == startIdx;
    }

    @Override
    public boolean isCropEnd(Jump jump) {
        Integer index = indexOf.get(jump);
        return index != null && endIdx < jumps.size() - 1 && index.intValue() == endIdx;
    }

    @Override
    public void toggle(Jump jump) {
        String key = jump.getSheetRowKey();
        if (!unchecked.remove(key)) unchecked.add(key);
    }

    // -------------------------
    // Helpers
    // -------------------------

    private String keyOf(int index) {
        return jumps.get(index).getSheetRowKey();
    }

    private int indexOfKey(String key) {
        if (key == null || key.isEmpty()) return -1;
        for (int i = 0; i < jumps.size(); i++) {
            if (key.equals(jumps.get(i).getSheetRowKey())) return i;
        }
        return -1;
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (!popup.isOpen() && keyCode != Keyboard.KEY_ESCAPE) {
            if (keyCode == Keyboard.KEY_SPACE && table.getSelectedJump() != null) {
                toggle(table.getSelectedJump());
                return;
            }
            if (table.keyTyped(keyCode)) return;
        }
        super.keyTyped(typedChar, keyCode);
    }
}
