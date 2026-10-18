package me.texyle.startreminders.ui;

import java.util.function.IntConsumer;

/** A row of mutually exclusive options, e.g. "Nearest | Crosshair". */
public class UiSegmented extends UiWidget {

    private static final int PAD = 10;

    private final String[] options;
    private int selected;
    private final IntConsumer onSelect;

    public UiSegmented(String[] options, int selected, IntConsumer onSelect) {
        this.options = options;
        this.selected = selected;
        this.onSelect = onSelect;
        this.w = preferredWidth();
        this.h = 18;
    }

    public int preferredWidth() {
        int total = 0;
        for (String o : options) {
            total += UiDraw.width(o) + PAD * 2;
        }
        return total;
    }

    public void setSelected(int index) {
        this.selected = index;
    }

    @Override
    public void draw(int mouseX, int mouseY) {
        if (!visible) return;

        UiDraw.box(x, y, w, h, UiTheme.CONTROL_BG, UiTheme.CONTROL_BORDER);

        int cx = x;
        for (int i = 0; i < options.length; i++) {
            int segW = UiDraw.width(options[i]) + PAD * 2;
            boolean active = (i == selected);
            boolean hover = enabled && UiDraw.inside(mouseX, mouseY, cx, y, segW, h);

            if (active) {
                UiDraw.fill(cx + 1, y + 1, segW - 2, h - 2, UiTheme.ACCENT);
            } else if (hover) {
                UiDraw.fill(cx + 1, y + 1, segW - 2, h - 2, UiTheme.CONTROL_HOVER);
            }

            int color = !enabled ? UiTheme.TEXT_DISABLED : (active ? UiTheme.TEXT : UiTheme.TEXT_MUTED);
            UiDraw.text(options[i], cx + PAD, y + (h - UiDraw.font().FONT_HEIGHT) / 2 + 1, color);
            cx += segW;
        }
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (!visible || !enabled || button != 0 || !contains(mouseX, mouseY)) {
            return false;
        }

        int cx = x;
        for (int i = 0; i < options.length; i++) {
            int segW = UiDraw.width(options[i]) + PAD * 2;
            if (UiDraw.inside(mouseX, mouseY, cx, y, segW, h)) {
                if (i != selected) {
                    selected = i;
                    if (onSelect != null) onSelect.accept(i);
                }
                return true;
            }
            cx += segW;
        }
        return true;
    }
}
