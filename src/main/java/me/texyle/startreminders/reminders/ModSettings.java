package me.texyle.startreminders.reminders;

public class ModSettings {
    // Global toggle: show jump name above strategy in-world (non-legacy only).
    // Default is true.
    public boolean showJumpNameInWorld = true;

    // Global in-world colors (stored as Minecraft formatting codes, e.g. "§b").
    // Defaults: jump name = aqua (§b), text = white (§f).
    public String inWorldJumpNameColor = "\u00A7b";
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

    // Where a new strategy label is placed: blocks in front of the player (0..5, default 1) and blocks above
    // the feet block (0..5, default 2).
    public int labelForward = 1;
    public int labelUp = 2;
}