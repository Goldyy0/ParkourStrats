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
}