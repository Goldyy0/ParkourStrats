package me.texyle.startreminders.ui;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import org.lwjgl.input.Mouse;

import me.texyle.startreminders.gui.GuiScissor;

/** Scrollable list with one selected row. Rows show text unless a custom renderer is set. */
public class UiList<T> extends UiWidget {

    /** Draws one row; the list has already drawn the hover / selection background. */
    public interface RowRenderer<T> {
        void draw(T item, int x, int y, int w, int h, boolean selected);
    }

    private static final int ROW_H = 22;
    private static final long DOUBLE_CLICK_MS = 320L;
    private static final int BAR_W = 8; // clickable width of the scrollbar

    private final Supplier<List<T>> items;
    private final Function<T, String> label;
    private RowRenderer<T> renderer;

    private int selected = -1;
    private int scroll = 0;

    private Consumer<T> onSelect;
    private Consumer<T> onActivate;

    private int lastClickedIndex = -1;
    private long lastClickMs = 0L;

    private String emptyText = "";

    // Scrollbar dragging (polled in draw, like the table's scrollbars)
    private boolean draggingBar = false;
    private int grabOffsetY = 0;

    public UiList(Supplier<List<T>> items, Function<T, String> label) {
        this.items = items;
        this.label = label;
    }

    public UiList<T> onSelect(Consumer<T> handler) {
        this.onSelect = handler;
        return this;
    }

    /** Called on double-click or when Enter is pressed on the selection. */
    public UiList<T> onActivate(Consumer<T> handler) {
        this.onActivate = handler;
        return this;
    }

    public UiList<T> renderer(RowRenderer<T> renderer) {
        this.renderer = renderer;
        return this;
    }

    public UiList<T> emptyText(String text) {
        this.emptyText = text;
        return this;
    }

    public T getSelected() {
        List<T> list = items.get();
        return (list != null && selected >= 0 && selected < list.size()) ? list.get(selected) : null;
    }

    /** Selects the item by reference (no events fired). */
    public void setSelected(T item) {
        List<T> list = items.get();
        selected = -1;
        if (list == null) return;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) == item) {
                selected = i;
                ensureVisible(i);
                return;
            }
        }
    }

    public void clearSelection() {
        selected = -1;
    }

    private int size() {
        List<T> list = items.get();
        return list != null ? list.size() : 0;
    }

    private int maxScroll() {
        return Math.max(0, size() * ROW_H - (h - 2));
    }

    private void ensureVisible(int index) {
        int top = index * ROW_H;
        int bottom = top + ROW_H;
        if (top < scroll) scroll = top;
        if (bottom > scroll + h - 2) scroll = bottom - (h - 2);
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
    }

    @Override
    public void draw(int mouseX, int mouseY) {
        if (!visible) return;

        UiDraw.box(x, y, w, h, UiTheme.CARD_BG, UiTheme.CARD_BORDER);

        List<T> list = items.get();
        if (list == null || list.isEmpty()) {
            if (!emptyText.isEmpty()) {
                UiDraw.centered(emptyText, x + w / 2, y + h / 2 - 4, UiTheme.TEXT_DISABLED);
            }
            return;
        }

        updateBarDrag(mouseY);
        scroll = Math.max(0, Math.min(scroll, maxScroll()));

        // Rows that are partly scrolled out must not be drawn outside the box.
        GuiScissor.enable(Minecraft.getMinecraft(), x + 1, y + 1, w - 2, h - 2);

        int first = scroll / ROW_H;
        int rowY = y + 1 - (scroll % ROW_H);
        boolean scrollbar = maxScroll() > 0;
        int rowW = w - 2 - (scrollbar ? 6 : 0);

        for (int i = first; i < list.size() && rowY < y + h - 1; i++, rowY += ROW_H) {
            int top = Math.max(rowY, y + 1);
            int bottom = Math.min(rowY + ROW_H, y + h - 1);
            if (bottom <= top) continue;

            boolean isSelected = (i == selected);
            boolean hover = UiDraw.inside(mouseX, mouseY, x + 1, top, rowW, bottom - top);

            if (isSelected) {
                UiDraw.fill(x + 1, top, rowW, bottom - top, UiTheme.ACCENT);
            } else if (hover) {
                UiDraw.fill(x + 1, top, rowW, bottom - top, UiTheme.CONTROL_HOVER);
            }

            T item = list.get(i);
            if (renderer != null) {
                renderer.draw(item, x + 1, rowY, rowW, ROW_H, isSelected);
            } else {
                String text = label.apply(item);
                UiDraw.text(UiDraw.fit(text, rowW - 14), x + 8, rowY + (ROW_H - 8) / 2, UiTheme.TEXT);
            }
        }

        GuiScissor.disable();

        if (scrollbar) {
            boolean hot = draggingBar || UiDraw.inside(mouseX, mouseY, x + w - BAR_W, y + 1, BAR_W - 1, h - 2);
            UiDraw.fill(x + w - 6, y + 1, 5, h - 2, 0x33000000);
            UiDraw.fill(x + w - 6, thumbY(), 5, thumbH(), hot ? UiTheme.ACCENT_HOVER : UiTheme.ACCENT);
        }
    }

    private int thumbH() {
        int trackH = h - 2;
        return Math.max(16, trackH * trackH / Math.max(1, size() * ROW_H));
    }

    private int thumbY() {
        int trackH = h - 2;
        int max = maxScroll();
        return y + 1 + (max <= 0 ? 0 : (int) ((trackH - thumbH()) * (scroll / (double) max)));
    }

    /** Moves the scroll position so the thumb follows the mouse; stops when the button is released. */
    private void updateBarDrag(int mouseY) {
        if (!draggingBar) return;

        if (!Mouse.isButtonDown(0)) {
            draggingBar = false;
            return;
        }

        int trackH = h - 2;
        int free = trackH - thumbH();
        if (free <= 0) return;

        double ratio = (mouseY - grabOffsetY - (y + 1)) / (double) free;
        ratio = Math.max(0.0, Math.min(1.0, ratio));
        scroll = (int) Math.round(ratio * maxScroll());
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (!visible || !enabled || !contains(mouseX, mouseY)) return false;
        if (button != 0) return true;

        // Press on the scrollbar: grab the thumb (or jump it to the cursor) and drag.
        if (maxScroll() > 0 && mouseX >= x + w - BAR_W) {
            int thumbTop = thumbY();
            grabOffsetY = (mouseY >= thumbTop && mouseY < thumbTop + thumbH()) ? mouseY - thumbTop : thumbH() / 2;
            draggingBar = true;
            updateBarDrag(mouseY);
            return true;
        }

        int index = (mouseY - y - 1 + scroll) / ROW_H;
        if (index < 0 || index >= size()) return true;

        long now = Minecraft.getSystemTime();
        boolean isDouble = (index == lastClickedIndex) && (now - lastClickMs) <= DOUBLE_CLICK_MS;

        selected = index;
        lastClickedIndex = index;
        lastClickMs = now;

        T item = getSelected();
        if (onSelect != null) onSelect.accept(item);

        if (isDouble) {
            lastClickedIndex = -1;
            if (onActivate != null) onActivate.accept(item);
        }
        return true;
    }

    @Override
    public boolean mouseWheel(int mouseX, int mouseY, int delta) {
        if (!visible || !contains(mouseX, mouseY) || delta == 0) return false;

        scroll += (delta > 0) ? -ROW_H : ROW_H;
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
        return true;
    }
}
