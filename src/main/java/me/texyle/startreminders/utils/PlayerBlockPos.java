package me.texyle.startreminders.utils;

import net.minecraft.client.entity.EntityPlayerSP;

/** Block coordinates of the player as the mod stores them for a jump. */
public final class PlayerBlockPos {

    private PlayerBlockPos() {}

    public static int x(EntityPlayerSP p) {
        return (int) Math.floor(p.posX);
    }

    /** Y of the block the player's head is in (feet block + 1) as used for jump coordinates. */
    public static int jumpY(EntityPlayerSP p) {
        return (int) Math.floor(p.posY) + 2;
    }

    public static int z(EntityPlayerSP p) {
        return (int) Math.floor(p.posZ);
    }
}
