package me.texyle.startreminders.ui;

import java.util.function.IntConsumer;

import org.lwjgl.input.Mouse;

/** A whole-number slider with its current value written at the right end; drag it or click on the track. */
public class UiSlider extends UiWidget {

    private static final int VALUE_W = 70;
    private static final int THUMB_W = 6;

    private final int min;
    private final int max;
    private final String suffix;
    private final IntConsumer onChange;

    private int value;
    private boolean dragging;

    public UiSlider(int min, int max, int initial, String suffix, IntConsumer onChange) {
        this.min = min;
        this.max = max;
        this.suffix = suffix;
        this.onChange = onChange;
        this.value = Math.max(min, Math.min(max, initial));
        this.w = 180;
        this.h = 16;
    }

    /** Moves the slider without calling the listener (the setting was changed elsewhere). */
    public void setValue(int value) {
        this.value = Math.max(min, Math.min(max, value));
    }

    private int trackX() {
        return x + THUMB_W / 2;
    }

    private int trackW() {
        return w - VALUE_W - THUMB_W;
    }

    @Override
    public void draw(int mouseX, int mouseY) {
        if (!visible) return;

        if (dragging) {
            if (Mouse.isButtonDown(0)) {
                setFromMouse(mouseX);
            } else {
                dragging = false;
            }
        }

        int trackX = trackX();
        int trackW = trackW();
        int trackY = y + h / 2 - 1;
        int filled = trackW * (value - min) / Math.max(1, max - min);

        UiDraw.fill(trackX, trackY, trackW, 2, UiTheme.CARD_BORDER);
        UiDraw.fill(trackX, trackY, filled, 2, UiTheme.ACCENT);
        boolean hot = dragging || (enabled && contains(mouseX, mouseY) && mouseX < x + w - VALUE_W);
        UiDraw.box(trackX + filled - THUMB_W / 2, y + 1, THUMB_W, h - 2,
                hot ? UiTheme.ACCENT_HOVER : UiTheme.ACCENT, UiTheme.ACCENT_HOVER);

        UiDraw.text(value + suffix, x + w - VALUE_W + 8, y + (h - 8) / 2, UiTheme.TEXT);
    }

    private void setFromMouse(int mouseX) {
        double t = (mouseX - trackX()) / (double) trackW();
        int next = min + (int) Math.round(Math.max(0.0, Math.min(1.0, t)) * (max - min));
        if (next != value) {
            value = next;
            if (onChange != null) onChange.accept(value);
        }
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (!visible || !enabled || button != 0 || !contains(mouseX, mouseY) || mouseX >= x + w - VALUE_W) {
            return false;
        }
        dragging = true;
        setFromMouse(mouseX);
        return true;
    }
}
