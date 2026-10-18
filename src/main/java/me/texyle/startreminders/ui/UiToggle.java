package me.texyle.startreminders.ui;

import java.util.function.Consumer;

/** On / off switch. */
public class UiToggle extends UiWidget {

    private boolean on;
    private final Consumer<Boolean> onChange;

    public UiToggle(boolean initial, Consumer<Boolean> onChange) {
        this.on = initial;
        this.onChange = onChange;
        this.w = 32;
        this.h = 16;
    }

    public void setOn(boolean on) {
        this.on = on;
    }

    @Override
    public void draw(int mouseX, int mouseY) {
        if (!visible) return;

        int track = on ? UiTheme.ACCENT : UiTheme.CONTROL_BG;
        if (enabled && contains(mouseX, mouseY)) {
            track = on ? UiTheme.ACCENT_HOVER : UiTheme.CONTROL_HOVER;
        }

        UiDraw.box(x, y, w, h, track, on ? track : UiTheme.CONTROL_BORDER);

        int knob = h - 6;
        int knobX = on ? x + w - knob - 3 : x + 3;
        UiDraw.fill(knobX, y + 3, knob, knob, enabled ? UiTheme.TEXT : UiTheme.TEXT_DISABLED);
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (!visible || !enabled || button != 0 || !contains(mouseX, mouseY)) {
            return false;
        }
        on = !on;
        if (onChange != null) {
            onChange.accept(Boolean.valueOf(on));
        }
        return true;
    }
}
