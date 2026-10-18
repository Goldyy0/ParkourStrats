package me.texyle.startreminders.gui.hierarchy;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiSlot;

import me.texyle.startreminders.gui.GuiDraw;

public class GuiListSlot<T> extends GuiStyledSlot {

    public interface ILabelProvider<T> {
        String getLabel(T item);
    }

    public interface ISelectionHandler {
        void onSingleClick(int index);
        void onDoubleClick(int index);
    }

    private final List<T> items;
    private final ILabelProvider<T> labelProvider;
    private final ISelectionHandler selectionHandler;

    private int selectedIndex = -1;

    private int lastClickedIndex = -1;
    private long lastClickTimeMs = 0L;

    // Guards against duplicate elementClicked calls caused by input quirks.
    private int lastRawClickIndex = -1;
    private long lastRawClickTimeMs = 0L;

    private static final long DOUBLE_CLICK_WINDOW_MS = 320L;
    private static final long DUPLICATE_CLICK_GUARD_MS = 80L;

    private static final int COLOR_ROW_SELECTED = 0x55333333;

    // Leave a small gutter so clicks near the scrollbar don't feel weird.
    private static final int LIST_RIGHT_GUTTER_PX = 10;

    public GuiListSlot(Minecraft mc, int width, int height, int top, int bottom, int slotHeight,
                       List<T> items,
                       ILabelProvider<T> labelProvider,
                       ISelectionHandler selectionHandler) {
        super(mc, width, height, top, bottom, slotHeight);
        this.items = items;
        this.labelProvider = labelProvider;
        this.selectionHandler = selectionHandler;

        // Disable vanilla selection highlight (but keep list rendering & input)
    }

    /**
     * IMPORTANT: Expand list width so the clickable area is not just a narrow centered rectangle.
     * GuiSlot uses getListWidth() for both drawing origin (insideLeft) and click hit-tests.
     */
    @Override
    public int getListWidth() {
        int w = (this.right - this.left) - LIST_RIGHT_GUTTER_PX;
        return Math.max(0, w);
    }

    @Override
    protected int getSize() {
        return items != null ? items.size() : 0;
    }

    @Override
    protected void elementClicked(int index, boolean ignoredDoubleClickFlag, int mouseX, int mouseY) {
        long now = Minecraft.getSystemTime();

        if (index == lastRawClickIndex && (now - lastRawClickTimeMs) <= DUPLICATE_CLICK_GUARD_MS) {
            return;
        }
        lastRawClickIndex = index;
        lastRawClickTimeMs = now;

        selectedIndex = index;

        if (selectionHandler != null) {
            selectionHandler.onSingleClick(index);
        }

        boolean sameIndex = (index == lastClickedIndex);
        boolean withinWindow = (now - lastClickTimeMs) <= DOUBLE_CLICK_WINDOW_MS;
        boolean isDouble = sameIndex && withinWindow;

        lastClickedIndex = index;
        lastClickTimeMs = now;

        if (isDouble && selectionHandler != null) {
            selectionHandler.onDoubleClick(index);

            // Consume to avoid chain-opens from very fast repeated callbacks.
            lastClickedIndex = -1;
            lastClickTimeMs = 0L;
        }
    }

    @Override
    protected boolean isSelected(int index) {
        return index == selectedIndex;
    }

    @Override
    protected void drawSlot(int entryID, int insideLeft, int yPos, int insideSlotHeight, int mouseXIn, int mouseYIn) {
        if (entryID == selectedIndex) {
            GuiDraw.fillRect(this.left, yPos, this.right, yPos + this.slotHeight, COLOR_ROW_SELECTED);
        }

        if (items == null || entryID < 0 || entryID >= items.size()) {
            return;
        }

        T item = items.get(entryID);
        String label = (labelProvider != null) ? labelProvider.getLabel(item) : String.valueOf(item);
        if (label == null) {
            label = "";
        }

        // Center text within the visible list width (excluding scrollbar gutter).
        int listW = getListWidth();
        int maxTextW = Math.max(0, listW - 8);
        String trimmed = font.trimStringToWidth(label, maxTextW);
        int textW = font.getStringWidth(trimmed);

// Center by the actual slot/screen center, not by listW (which is shrunk by the scrollbar gutter)
        int centerX = (this.left + this.right) / 2;
        int textX = centerX - (textW / 2);

// Keep it inside the clickable content area (avoid drawing under the scrollbar)
        int contentLeft = this.left + 4;
        int contentRight = this.right - LIST_RIGHT_GUTTER_PX - 4;

        if (textX < contentLeft) textX = contentLeft;
        if (textX + textW > contentRight) textX = contentRight - textW;

        int textY = yPos + 2;
        font.drawString(trimmed, textX, textY, 0xFFFFFF, true);
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    public void setSelectedIndex(int idx) {
        this.selectedIndex = idx;
    }

    public T getSelectedItem() {
        if (items == null) return null;
        if (selectedIndex < 0 || selectedIndex >= items.size()) return null;
        return items.get(selectedIndex);
    }

}