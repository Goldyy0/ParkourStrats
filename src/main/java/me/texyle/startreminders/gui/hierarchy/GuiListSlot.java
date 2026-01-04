package me.texyle.startreminders.gui.hierarchy;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiSlot;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

public class GuiListSlot<T> extends GuiSlot {

    public interface ILabelProvider<T> {
        String getLabel(T item);
    }

    public interface ISelectionHandler {
        void onSingleClick(int index);
        void onDoubleClick(int index);
    }

    private final Minecraft mc;
    private final FontRenderer font;
    private final List<T> items;
    private final ILabelProvider<T> labelProvider;
    private final ISelectionHandler selectionHandler;

    private int selectedIndex = -1;

    private int lastClickedIndex = -1;
    private long lastClickTimeMs = 0L;

    // Guards against duplicate elementClicked calls caused by input quirks.
    private int lastRawClickIndex = -1;
    private long lastRawClickTimeMs = 0L;

    private static final long DOUBLE_CLICK_WINDOW_MS = 320L;
    private static final long DUPLICATE_CLICK_GUARD_MS = 80L;

    private static final int COLOR_ROW_SELECTED = 0x55333333;

    // Custom vertical scrollbar skin colors (match horizontal)
    private static final int COLOR_SCROLLBAR_BG = 0x44000000;
    private static final int COLOR_SCROLLBAR_THUMB = 0xAA6666FF;

    // Leave a small gutter so clicks near the scrollbar don't feel weird.
    private static final int LIST_RIGHT_GUTTER_PX = 10;

    public GuiListSlot(Minecraft mc, int width, int height, int top, int bottom, int slotHeight,
                       List<T> items,
                       ILabelProvider<T> labelProvider,
                       ISelectionHandler selectionHandler) {
        super(mc, width, height, top, bottom, slotHeight);
        this.mc = mc;
        this.font = mc.fontRendererObj;
        this.items = items;
        this.labelProvider = labelProvider;
        this.selectionHandler = selectionHandler;

        // Disable vanilla selection highlight (but keep list rendering & input)
        this.showSelectionBox = false;
    }

    /**
     * IMPORTANT: Expand list width so the clickable area is not just a narrow centered rectangle.
     * GuiSlot uses getListWidth() for both drawing origin (insideLeft) and click hit-tests.
     */
    @Override
    public int getListWidth() {
        int w = (this.right - this.left) - LIST_RIGHT_GUTTER_PX;
        return Math.max(0, w);
    }

    /**
     * Keep vanilla GuiSlot behavior (wheel, click, insideLeft), but force scissor clipping
     * so nothing bleeds outside the viewport.
     */
    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        enableScissorForSlotViewport();
        try {
            super.drawScreen(mouseX, mouseY, partialTicks);
        } finally {
            disableScissor();
        }

        // Hide vanilla GuiSlot scrollbar (it is always drawn by super.drawScreen).
        coverVanillaVerticalScrollbar();

        // Draw our custom vertical scrollbar on top.
        drawVerticalScrollbarSkin();
    }

    private void coverVanillaVerticalScrollbar() {
        int visibleH = (this.bottom - this.top);
        int totalH = getContentHeight();
        int maxScroll = Math.max(0, totalH - visibleH);

        if (maxScroll <= 0) {
            return;
        }

        int barLeft = getScrollBarX();
        int barRight = barLeft + 6;

        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        drawRectLocal(barLeft, this.top, barRight, this.bottom, 0xFF101010);
    }

    private void enableScissorForSlotViewport() {
        int x = this.left;
        int y = this.top;
        int w = Math.max(0, this.right - this.left);
        int h = Math.max(0, this.bottom - this.top);

        if (w <= 0 || h <= 0) {
            return;
        }

        ScaledResolution sr = new ScaledResolution(this.mc);
        int scale = sr.getScaleFactor();

        int scX = x * scale;
        int scY = (sr.getScaledHeight() - (y + h)) * scale;
        int scW = w * scale;
        int scH = h * scale;

        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(scX, scY, scW, scH);
    }

    private void disableScissor() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    @Override
    protected int getScrollBarX() {
        int barW = 6;
        int barRight = this.right - 2;
        return barRight - barW;
    }

    private void drawVerticalScrollbarSkin() {
        int visibleH = (this.bottom - this.top);
        int totalH = getContentHeight();
        int maxScroll = Math.max(0, totalH - visibleH);

        if (maxScroll <= 0) {
            return;
        }

        int barW = 6;
        int barRight = this.right - 2;
        int barLeft = barRight - barW;

        int barTop = this.top;
        int barBottom = this.bottom;
        int barH = barBottom - barTop;

        int thumbH = Math.max(24, (int) ((barH * (double) visibleH) / (double) totalH));
        int trackH = barH - thumbH;
        if (trackH < 0) trackH = 0;

        double t = (maxScroll == 0) ? 0.0 : (this.amountScrolled / (double) maxScroll);
        if (t < 0.0) t = 0.0;
        if (t > 1.0) t = 1.0;

        int thumbY = barTop + (int) Math.round(t * trackH);

        drawRectLocal(barLeft, barTop, barRight, barBottom, COLOR_SCROLLBAR_BG);
        drawRectLocal(barLeft, thumbY, barRight, thumbY + thumbH, COLOR_SCROLLBAR_THUMB);
    }

    @Override
    protected void drawContainerBackground(Tessellator tessellator) {
        // Intentionally empty: prevents GuiSlot from drawing the default dirt/options background.
    }

    @Override
    protected void overlayBackground(int startY, int endY, int startAlpha, int endAlpha) {
        // Intentionally empty: prevents the dark fade overlays at top/bottom.
    }

    @Override
    protected int getSize() {
        return items != null ? items.size() : 0;
    }

    @Override
    protected void elementClicked(int index, boolean ignoredDoubleClickFlag, int mouseX, int mouseY) {
        long now = Minecraft.getSystemTime();

        if (index == lastRawClickIndex && (now - lastRawClickTimeMs) <= DUPLICATE_CLICK_GUARD_MS) {
            return;
        }
        lastRawClickIndex = index;
        lastRawClickTimeMs = now;

        selectedIndex = index;

        if (selectionHandler != null) {
            selectionHandler.onSingleClick(index);
        }

        boolean sameIndex = (index == lastClickedIndex);
        boolean withinWindow = (now - lastClickTimeMs) <= DOUBLE_CLICK_WINDOW_MS;
        boolean isDouble = sameIndex && withinWindow;

        lastClickedIndex = index;
        lastClickTimeMs = now;

        if (isDouble && selectionHandler != null) {
            selectionHandler.onDoubleClick(index);

            // Consume to avoid chain-opens from very fast repeated callbacks.
            lastClickedIndex = -1;
            lastClickTimeMs = 0L;
        }
    }

    @Override
    protected boolean isSelected(int index) {
        return index == selectedIndex;
    }

    @Override
    protected void drawBackground() {
        // Parent screen draws background.
    }

    @Override
    protected void drawSlot(int entryID, int insideLeft, int yPos, int insideSlotHeight, int mouseXIn, int mouseYIn) {
        if (entryID == selectedIndex) {
            drawRectLocal(this.left, yPos, this.right, yPos + this.slotHeight, COLOR_ROW_SELECTED);
        }

        if (items == null || entryID < 0 || entryID >= items.size()) {
            return;
        }

        T item = items.get(entryID);
        String label = (labelProvider != null) ? labelProvider.getLabel(item) : String.valueOf(item);
        if (label == null) {
            label = "";
        }

        // Center text within the visible list width (excluding scrollbar gutter).
        int listW = getListWidth();
        int maxTextW = Math.max(0, listW - 8);
        String trimmed = font.trimStringToWidth(label, maxTextW);
        int textW = font.getStringWidth(trimmed);

// Center by the actual slot/screen center, not by listW (which is shrunk by the scrollbar gutter)
        int centerX = (this.left + this.right) / 2;
        int textX = centerX - (textW / 2);

// Keep it inside the clickable content area (avoid drawing under the scrollbar)
        int contentLeft = this.left + 4;
        int contentRight = this.right - LIST_RIGHT_GUTTER_PX - 4;

        if (textX < contentLeft) textX = contentLeft;
        if (textX + textW > contentRight) textX = contentRight - textW;

        int textY = yPos + 2;
        font.drawString(trimmed, textX, textY, 0xFFFFFF, true);
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    public void setSelectedIndex(int idx) {
        this.selectedIndex = idx;
    }

    public T getSelectedItem() {
        if (items == null) return null;
        if (selectedIndex < 0 || selectedIndex >= items.size()) return null;
        return items.get(selectedIndex);
    }

    private static void drawRectLocal(int left, int top, int right, int bottom, int color) {
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
}