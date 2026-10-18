package me.texyle.startreminders.gui.hierarchy;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiSlot;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;

import me.texyle.startreminders.gui.GuiDraw;
import me.texyle.startreminders.gui.GuiScissor;

/**
 * Base for the mod's list widgets: keeps vanilla {@link GuiSlot} behavior (wheel, click hit-tests) but
 * clips drawing to the viewport, replaces the vanilla scrollbar with the mod's skin and suppresses the
 * dirt background and fade overlays (the parent screen draws the background).
 */
abstract class GuiStyledSlot extends GuiSlot {

    private static final int SCROLLBAR_WIDTH = 6;

    protected final Minecraft mc;
    protected final FontRenderer font;

    GuiStyledSlot(Minecraft mc, int width, int height, int top, int bottom, int slotHeight) {
        super(mc, width, height, top, bottom, slotHeight);
        this.mc = mc;
        this.font = mc.fontRendererObj;

        // Disable vanilla selection highlight (but keep list rendering & input)
        this.showSelectionBox = false;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        GuiScissor.enable(this.mc, this.left, this.top, this.right - this.left, this.bottom - this.top);
        try {
            super.drawScreen(mouseX, mouseY, partialTicks);
        } finally {
            GuiScissor.disable();
        }

        // Hide vanilla GuiSlot scrollbar (it is always drawn by super.drawScreen), then draw ours on top.
        coverVanillaVerticalScrollbar();
        drawVerticalScrollbarSkin();
    }

    @Override
    protected int getScrollBarX() {
        int barRight = this.right - 2;
        return barRight - SCROLLBAR_WIDTH;
    }

    private void coverVanillaVerticalScrollbar() {
        int maxScroll = Math.max(0, getContentHeight() - (this.bottom - this.top));

        if (maxScroll <= 0) {
            return;
        }

        int barLeft = getScrollBarX();
        int barRight = barLeft + SCROLLBAR_WIDTH;

        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GuiDraw.fillRect(barLeft, this.top, barRight, this.bottom, 0xFF101010);
    }

    private void drawVerticalScrollbarSkin() {
        int visibleH = (this.bottom - this.top);
        int totalH = getContentHeight();
        int maxScroll = Math.max(0, totalH - visibleH);

        if (maxScroll <= 0) {
            return;
        }

        int barRight = this.right - 2;
        int barLeft = barRight - SCROLLBAR_WIDTH;

        int barTop = this.top;
        int barBottom = this.bottom;
        int barH = barBottom - barTop;

        int thumbH = Math.max(24, (int) ((barH * (double) visibleH) / (double) totalH));
        int trackH = barH - thumbH;
        if (trackH < 0) trackH = 0;

        double t = this.amountScrolled / (double) maxScroll;
        if (t < 0.0) t = 0.0;
        if (t > 1.0) t = 1.0;

        int thumbY = barTop + (int) Math.round(t * trackH);

        GuiDraw.fillRect(barLeft, barTop, barRight, barBottom, ScrollbarSkin.BACKGROUND);
        GuiDraw.fillRect(barLeft, thumbY, barRight, thumbY + thumbH, ScrollbarSkin.THUMB);
    }

    @Override
    protected void drawBackground() {
        // Parent screen draws the background and panel.
    }

    @Override
    protected void drawContainerBackground(Tessellator tessellator) {
        // Intentionally empty: prevents GuiSlot from drawing the default dirt/options background.
    }

    @Override
    protected void overlayBackground(int startY, int endY, int startAlpha, int endAlpha) {
        // Intentionally empty: prevents the dark fade overlays at top/bottom.
    }
}
