package me.texyle.startreminders.ui;

import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** A field showing the current choice; clicking it opens the window's popup with all choices. */
public class UiDropdown extends UiWidget {

    private final UiPopup popup;
    private final String caption;
    private final Supplier<String> valueText;
    private final Supplier<List<UiPopup.Entry>> entries;
    private IntSupplier valueColor;

    public UiDropdown(UiPopup popup, String caption, Supplier<String> valueText, Supplier<List<UiPopup.Entry>> entries) {
        this.popup = popup;
        this.caption = caption;
        this.valueText = valueText;
        this.entries = entries;
    }

    /** Colors the shown value (ARGB, 0 = default); for the special Global / RestoredStrats containers. */
    public UiDropdown valueColor(IntSupplier color) {
        this.valueColor = color;
        return this;
    }

    @Override
    public void draw(int mouseX, int mouseY) {
        if (!visible) return;

        boolean hover = enabled && contains(mouseX, mouseY);
        UiDraw.box(x, y, w, h, hover ? UiTheme.CONTROL_HOVER : UiTheme.CONTROL_BG, UiTheme.CONTROL_BORDER);

        int textY = y + (h - UiDraw.font().FONT_HEIGHT) / 2 + 1;
        String cap = caption.toUpperCase();
        int valueX = x + 8;
        if (!cap.isEmpty()) {
            UiDraw.text(cap, x + 8, textY, UiTheme.TEXT_MUTED);
            valueX += UiDraw.width(cap) + 8;
        }

        int maxValueW = w - (valueX - x) - 18;
        String value = valueText.get();
        int special = valueColor != null ? valueColor.getAsInt() : 0;
        int color = special != 0 ? special : (enabled ? UiTheme.TEXT : UiTheme.TEXT_DISABLED);
        UiDraw.text(UiDraw.fit(value != null ? value : "-", maxValueW), valueX, textY, color);

        UiDraw.text("v", x + w - 12, textY, UiTheme.TEXT_MUTED);
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (!visible || !enabled || button != 0 || !contains(mouseX, mouseY)) {
            return false;
        }
        popup.open(x, y + h + 2, w, entries.get());
        return true;
    }
}
