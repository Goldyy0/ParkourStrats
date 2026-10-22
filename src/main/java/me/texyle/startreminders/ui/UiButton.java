package me.texyle.startreminders.ui;

/** Flat button. PRIMARY is the one main action of a view; keep one per view. */
public class UiButton extends UiWidget {

    public enum Style { PRIMARY, MUTED_ACCENT, SECONDARY, GHOST, DANGER }

    private String label;
    private final Style style;
    private final Runnable onClick;

    public UiButton(String label, Style style, Runnable onClick) {
        this.label = label;
        this.style = style;
        this.onClick = onClick;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    /** Width that fits the label with comfortable padding. */
    public int preferredWidth() {
        return UiDraw.width(label) + 20;
    }

    @Override
    public void draw(int mouseX, int mouseY) {
        if (!visible) return;

        boolean hover = enabled && contains(mouseX, mouseY);

        int fill;
        int border;
        int textColor = UiTheme.TEXT;

        switch (style) {
            case PRIMARY:
                fill = hover ? UiTheme.ACCENT_HOVER : UiTheme.ACCENT;
                border = fill;
                break;
            case MUTED_ACCENT:
                fill = hover ? UiTheme.ACCENT_MUTED_HOVER : UiTheme.ACCENT_MUTED;
                border = fill;
                break;
            case DANGER:
                fill = hover ? UiTheme.DANGER_HOVER : UiTheme.DANGER;
                border = fill;
                break;
            case GHOST:
                fill = hover ? UiTheme.CONTROL_HOVER : 0x00000000;
                border = 0x00000000;
                break;
            case SECONDARY:
            default:
                fill = hover ? UiTheme.CONTROL_HOVER : UiTheme.CONTROL_BG;
                border = UiTheme.CONTROL_BORDER;
                break;
        }

        if (!enabled) {
            fill = style == Style.GHOST ? 0x00000000 : UiTheme.CONTROL_BG;
            border = style == Style.GHOST ? 0x00000000 : UiTheme.CARD_BORDER;
            textColor = UiTheme.TEXT_DISABLED;
        }

        UiDraw.box(x, y, w, h, fill, border);
        UiDraw.centered(UiDraw.fit(label, w - 6), x + w / 2, y + (h - UiDraw.font().FONT_HEIGHT) / 2 + 1, textColor);
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (!visible || !enabled || button != 0 || !contains(mouseX, mouseY)) {
            return false;
        }
        if (onClick != null) {
            onClick.run();
        }
        return true;
    }
}
