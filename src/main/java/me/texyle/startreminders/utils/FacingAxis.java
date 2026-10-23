package me.texyle.startreminders.utils;

import net.minecraft.util.MathHelper;

/** The world axis a player looks along, from their yaw: +Z at 0 degrees, -X at 90, -Z at 180, +X at -90. */
public final class FacingAxis {

    private static final String[] NAMES = { "+Z", "-X", "-Z", "+X" };
    private static final int[] DX = { 0, -1, 0, 1 };
    private static final int[] DZ = { 1, 0, -1, 0 };

    private FacingAxis() {}

    private static int index(float yaw) {
        float wrapped = MathHelper.wrapAngleTo180_float(yaw);
        if (wrapped >= -45f && wrapped < 45f) return 0;
        if (wrapped >= 45f && wrapped < 135f) return 1;
        if (wrapped >= -135f && wrapped < -45f) return 3;
        return 2;
    }

    /** "+X", "-X", "+Z" or "-Z". */
    public static String name(float yaw) {
        return NAMES[index(yaw)];
    }

    /** X step of one block straight ahead (-1, 0 or 1). */
    public static int dx(float yaw) {
        return DX[index(yaw)];
    }

    /** Z step of one block straight ahead (-1, 0 or 1). */
    public static int dz(float yaw) {
        return DZ[index(yaw)];
    }
}
