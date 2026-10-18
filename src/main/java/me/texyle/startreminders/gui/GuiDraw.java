package me.texyle.startreminders.gui;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;

/** Low-level 2D drawing primitives shared by the mod's screens. */
public final class GuiDraw {

    private GuiDraw() {}

    /** Fills a rectangle with an ARGB colour. Coordinates may be given in any order. */
    public static void fillRect(int left, int top, int right, int bottom, int color) {
        if (left > right) {
            int tmp = left;
            left = right;
            right = tmp;
        }
        if (top > bottom) {
            int tmp = top;
            top = bottom;
            bottom = tmp;
        }

        float a = (color >> 24 & 255) / 255.0F;
        float r = (color >> 16 & 255) / 255.0F;
        float g = (color >> 8 & 255) / 255.0F;
        float b = (color & 255) / 255.0F;

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();

        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(r, g, b, a);

        wr.begin(7, DefaultVertexFormats.POSITION);
        wr.pos(left, bottom, 0).endVertex();
        wr.pos(right, bottom, 0).endVertex();
        wr.pos(right, top, 0).endVertex();
        wr.pos(left, top, 0).endVertex();
        tessellator.draw();

        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    /** Draws a one pixel wide vertical line from y1 to y2 (inclusive). */
    public static void vLine(int x, int y1, int y2, int color) {
        if (y2 < y1) {
            int tmp = y1;
            y1 = y2;
            y2 = tmp;
        }
        fillRect(x, y1, x + 1, y2 + 1, color);
    }
}
