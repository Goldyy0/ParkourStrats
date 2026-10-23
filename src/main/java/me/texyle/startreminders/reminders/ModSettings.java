package me.texyle.startreminders.reminders;

public class ModSettings {
    // Global toggle: show jump name above strategy in-world (non-legacy only).
    // Default is true.
    public boolean showJumpNameInWorld = true;

    // Global in-world colors (stored as Minecraft formatting codes, e.g. "§b").
    // Defaults: jump name = purple (§5), text = white (§f).
    public String inWorldJumpNameColor = "\u00A75";
    public String inWorldTextColor = "\u00A7f";

    // Edit menu jump pick mode:
    // false = Nearest (default), true = In crosshair
    public boolean editPickModeInCrosshair = false;

    // Jump table: sections as a narrow color stripe (name shown on hover) instead of full columns.
    // Default is true.
    public boolean sectionsCompact = true;

    // Strategy editor: extra decimals (0..5) in the live position readout. Default is 0.
    public int playerInfoExtraDecimals = 0;

    // Show-strategies key: true = press to toggle, false = hold. Default is true.
    public boolean showKeyToggle = true;

    // Status messages the mod prints in chat (Record taps always shows its prompt). Default is true.
    public boolean chatMessages = true;

    // Where a new strategy label is placed: blocks in front of the player (0..2, default 1) and blocks above
    // the feet block (0..2, default 2).
    public int labelForward = 1;
    public int labelUp = 2;

    // How far (blocks, 1..100) the edit key looks for a jump in "Crosshair" mode. Default is 50.
    public int crosshairRange = 50;

    // How far (blocks, 1..100) the edit key looks for a jump in "Nearest" mode. Default is 50.
    public int nearestRange = 50;

    // How far (blocks) the strategy labels are drawn in the world.
    public int labelRange = 15;

    // Template mode: which section level the crosshair text shows (0 = the innermost section, 1..5 = that level).
    public int sectionIndicatorLevel = 0;
}