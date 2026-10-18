package me.texyle.startreminders.ui;

import java.util.function.Supplier;

/** Static or dynamic text. */
public class UiLabel extends UiWidget {

    private final Supplier<String> text;
    private int color;
    private boolean centered;

    public UiLabel(String text, int color) {
        this(() -> text, color);
    }

    public UiLabel(Supplier<String> text, int color) {
        this.text = text;
        this.color = color;
        this.h = UiDraw.font().FONT_HEIGHT;
    }

    public UiLabel centered() {
        this.centered = true;
        return this;
    }

    public void setColor(int color) {
        this.color = color;
    }

    @Override
    public void draw(int mouseX, int mouseY) {
        if (!visible) return;

        String s = text.get();
        if (s == null || s.isEmpty()) return;

        String shown = UiDraw.fit(s, w > 0 ? w : 10000);
        if (centered) {
            UiDraw.centered(shown, x + w / 2, y, color);
        } else {
            UiDraw.text(shown, x, y, color);
        }
    }
}
