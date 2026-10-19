package me.texyle.startreminders.utils;

import net.minecraft.client.entity.EntityPlayerSP;

import me.texyle.startreminders.reminders.UserPreferences;

/** Block coordinates of the player as the mod stores them for a jump. */
public final class PlayerBlockPos {

    private PlayerBlockPos() {}

    /** X of the label block: the configured number of blocks ahead of the player, in the direction they look. */
    public static int x(EntityPlayerSP p) {
        return (int) Math.floor(p.posX - Math.sin(Math.toRadians(p.rotationYaw)) * UserPreferences.getLabelForward());
    }

    /** Y of the label block: the configured number of blocks above the player's feet block. */
    public static int jumpY(EntityPlayerSP p) {
        return (int) Math.floor(p.posY) + UserPreferences.getLabelUp();
    }

    public static int z(EntityPlayerSP p) {
        return (int) Math.floor(p.posZ + Math.cos(Math.toRadians(p.rotationYaw)) * UserPreferences.getLabelForward());
    }
}
