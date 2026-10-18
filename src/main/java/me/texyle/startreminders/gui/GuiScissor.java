package me.texyle.startreminders.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.opengl.GL11;

/** Clips drawing to a rectangle given in scaled GUI coordinates. */
public final class GuiScissor {

    private GuiScissor() {}

    /** Starts clipping to the rectangle; does nothing if it is empty. Pair with {@link #disable()}. */
    public static void enable(Minecraft mc, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) {
            return;
        }

        ScaledResolution sr = new ScaledResolution(mc);
        int scale = sr.getScaleFactor();

        int scX = x * scale;
        int scY = (sr.getScaledHeight() - (y + h)) * scale;
        int scW = w * scale;
        int scH = h * scale;

        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(scX, scY, scW, scH);
    }

    public static void disable() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }
}
