package me.texyle.startreminders.ui;

/**
 * Colors and metrics of the mod's windows (ARGB). One dark neutral palette with a single purple accent,
 * which marks the active state and the primary action and nothing else.
 */
public final class UiTheme {

    // Surfaces
    public static final int BACKDROP = 0xA0000000;
    public static final int PANEL_BG = 0xF0121116;
    public static final int PANEL_BORDER = 0xFF2B2838;
    public static final int CARD_BG = 0xFF19181F;
    public static final int CARD_BORDER = 0xFF2A2735;
    public static final int TABLE_HEADER_BG = 0xFF24222E;

    // Accent (dark purple)
    public static final int ACCENT = 0xFF6D3FD0;
    public static final int ACCENT_HOVER = 0xFF8250E8;
    public static final int ACCENT_PRESSED = 0xFF5A32B0;

    // Controls
    public static final int CONTROL_BG = 0xFF22202C;
    public static final int CONTROL_HOVER = 0xFF2E2B3C;
    public static final int CONTROL_BORDER = 0xFF383449;
    public static final int DANGER = 0xFFB3363B;
    public static final int DANGER_HOVER = 0xFFD2474D;

    // Text
    public static final int TEXT = 0xFFECEAF4;
    public static final int TEXT_MUTED = 0xFF9A96AC;
    public static final int TEXT_DISABLED = 0xFF5C5870;

    // Metrics
    public static final int CONTROL_HEIGHT = 20;
    public static final int PADDING = 10;

    private UiTheme() {}
}
