package me.texyle.startreminders.ui;

import java.util.function.IntConsumer;

/** Top navigation of a window: text tabs with an accent underline on the active one. */
public class UiTabBar extends UiWidget {

    private static final int PAD = 14;

    private final String[] tabs;
    private int selected;
    private final IntConsumer onSelect;

    public UiTabBar(String[] tabs, int selected, IntConsumer onSelect) {
        this.tabs = tabs;
        this.selected = selected;
        this.onSelect = onSelect;
        this.h = 24;
    }

    public void setSelected(int index) {
        this.selected = index;
    }

    @Override
    public void draw(int mouseX, int mouseY) {
        if (!visible) return;

        UiDraw.fill(x, y + h - 1, w, 1, UiTheme.PANEL_BORDER);

        int cx = x;
        for (int i = 0; i < tabs.length; i++) {
            int tabW = UiDraw.width(tabs[i].toUpperCase()) + PAD * 2;
            boolean active = (i == selected);
            boolean hover = UiDraw.inside(mouseX, mouseY, cx, y, tabW, h);

            if (hover && !active) {
                UiDraw.fill(cx, y, tabW, h - 1, UiTheme.CONTROL_BG);
            }

            int color = active ? UiTheme.TEXT : (hover ? UiTheme.TEXT : UiTheme.TEXT_MUTED);
            UiDraw.text(tabs[i].toUpperCase(), cx + PAD, y + (h - UiDraw.font().FONT_HEIGHT) / 2, color);

            if (active) {
                UiDraw.fill(cx, y + h - 2, tabW, 2, UiTheme.ACCENT);
            }
            cx += tabW;
        }
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (!visible || button != 0 || !contains(mouseX, mouseY)) {
            return false;
        }

        int cx = x;
        for (int i = 0; i < tabs.length; i++) {
            int tabW = UiDraw.width(tabs[i].toUpperCase()) + PAD * 2;
            if (UiDraw.inside(mouseX, mouseY, cx, y, tabW, h)) {
                if (i != selected) {
                    selected = i;
                    if (onSelect != null) onSelect.accept(i);
                }
                return true;
            }
            cx += tabW;
        }
        return false;
    }
}
