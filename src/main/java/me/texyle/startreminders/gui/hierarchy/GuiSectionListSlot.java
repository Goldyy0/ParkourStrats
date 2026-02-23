package me.texyle.startreminders.gui.hierarchy;

import java.util.ArrayList;

import me.texyle.startreminders.data.MapSection;
import me.texyle.startreminders.data.ParkourMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiSlot;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

public class GuiSectionListSlot extends GuiSlot {

    private final Minecraft mc;
    private final FontRenderer font;
    private final GuiConfigureSections parent;
    private final ParkourMap map;

    private int activeLevel = 1; // 1..4 (set by parent)

    private ArrayList<MapSection> items = new ArrayList<MapSection>();
    private int selectedIndex = -1;

    // Custom vertical scrollbar skin colors (match other hierarchy lists)
    private static final int COLOR_SCROLLBAR_BG = 0x44000000;
    private static final int COLOR_SCROLLBAR_THUMB = 0xAA6666FF;

    public GuiSectionListSlot(Minecraft mc, int width, int height, int top, int bottom, int slotHeight, GuiConfigureSections parent, ParkourMap map) {
        super(mc, width, height, top, bottom, slotHeight);
        this.mc = mc;
        this.font = mc.fontRendererObj;
        this.parent = parent;
        this.map = map;

        this.showSelectionBox = false;
    }

    public void setActiveLevel(int levelOneBased) {
        if (levelOneBased < 1) levelOneBased = 1;
        if (levelOneBased > 4) levelOneBased = 4;
        this.activeLevel = levelOneBased;
    }

    public void refresh(ArrayList<MapSection> list) {
        this.items = (list != null) ? list : new ArrayList<MapSection>();
        if (selectedIndex >= items.size()) selectedIndex = -1;
    }

    public MapSection getSelected() {
        if (items == null) return null;
        if (selectedIndex < 0 || selectedIndex >= items.size()) return null;
        return items.get(selectedIndex);
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    public void setSelectedIndex(int idx) {
        if (items == null) {
            selectedIndex = -1;
            return;
        }
        if (idx < 0 || idx >= items.size()) {
            selectedIndex = -1;
            return;
        }
        selectedIndex = idx;
    }

    @Override
    protected int getSize() {
        return items != null ? items.size() : 0;
    }

    @Override
    protected void elementClicked(int index, boolean doubleClick, int mouseX, int mouseY) {
        selectedIndex = index;
        if (parent != null) parent.onListSelectionChanged();
    }

    @Override
    protected boolean isSelected(int index) {
        return index == selectedIndex;
    }

    @Override
    protected void drawBackground() {
        // Parent screen draws the background and panel.
    }

    /**
     * IMPORTANT: Prevent GuiSlot from drawing vanilla dirt/options background.
     */
    @Override
    protected void drawContainerBackground(Tessellator tessellator) {
        // Intentionally empty.
    }

    /**
     * IMPORTANT: Prevent GuiSlot from drawing the dark fade overlays at top/bottom.
     */
    @Override
    protected void overlayBackground(int startY, int endY, int startAlpha, int endAlpha) {
        // Intentionally empty.
    }

    /**
     * Keep vanilla list behavior, but enforce scissor clipping and custom scrollbar skin.
     */
    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        enableScissorForSlotViewport();
        try {
            super.drawScreen(mouseX, mouseY, partialTicks);
        } finally {
            disableScissor();
        }

        coverVanillaVerticalScrollbar();
        drawVerticalScrollbarSkin();
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

    private String buildParentPathPrefix(MapSection section) {
        if (map == null || section == null) return "";
        if (activeLevel <= 1) return "";

        int start = section.getStartJumpIndex();
        int end = section.getEndJumpIndex();
        if (start < 0 || end < 0) return "";

        StringBuilder sb = new StringBuilder();

        int maxLevels = 4;
        try {
            int m = map.getMaxSectionLevelsAllowed();
            if (m > 0) maxLevels = Math.min(4, m);
        } catch (Throwable ignored) { }

        int upper = Math.min(activeLevel - 1, maxLevels);

        for (int lvl = 1; lvl <= upper; lvl++) {
            ArrayList<MapSection> parents = map.getSectionsForLevel(lvl);
            if (parents == null || parents.isEmpty()) continue;

            MapSection container = null;
            for (int i = 0; i < parents.size(); i++) {
                MapSection p = parents.get(i);
                if (p == null) continue;

                int a = p.getStartJumpIndex();
                int b = p.getEndJumpIndex();

                if (a < 0 || b < 0) continue;

                if (b < a) {
                    int tmp = a;
                    a = b;
                    b = tmp;
                }

                if (start >= a && end <= b) {
                    container = p;
                    break;
                }
            }

            if (container != null) {
                String n = container.getName();
                if (n == null) n = "";
                n = n.trim();
                if (!n.isEmpty()) {
                    if (sb.length() > 0) sb.append("/");
                    sb.append(n);
                }
            }
        }

        if (sb.length() == 0) return "";
        return sb.toString() + ": ";
    }

    @Override
    protected void drawSlot(int entryID, int insideLeft, int yPos, int insideSlotHeight, int mouseXIn, int mouseYIn) {
        if (items == null || entryID < 0 || entryID >= items.size()) return;

        MapSection s = items.get(entryID);
        if (s == null) return;

        int left = insideLeft + 2;
        int right = insideLeft + this.getListWidth() - 4;

        int top = yPos;
        int bottom = yPos + insideSlotHeight;

        int zebra = (entryID % 2 == 0) ? 0x22101010 : 0x33333333;
        Gui.drawRect(left, top, right, bottom, zebra);

        if (entryID == selectedIndex) {
            Gui.drawRect(left, top, right, bottom, 0x55333333);
        }

        // Prefix for nested levels: "Area 1/S1: "
        String prefix = buildParentPathPrefix(s);

        // Section name
        String name = s.getName();
        if (name == null) name = "";

        // Range label
        String range;
        if (s.getStartJumpIndex() < 0 || s.getEndJumpIndex() < 0) {
            range = " (Not set)";
        } else {
            range = " (" + (s.getStartJumpIndex() + 1) + " - " + (s.getEndJumpIndex() + 1) + ")";
        }

        // Layout: prefix -> color square -> "name (range)"
        int x = left + 6;

        if (prefix != null && !prefix.isEmpty()) {
            font.drawString(prefix, x, top + 7, 0xFFFFFF, true);
            x += font.getStringWidth(prefix);
        }

        int square = 10;
        int sy = top + (insideSlotHeight - square) / 2;
        Gui.drawRect(x, sy, x + square, sy + square, s.getColorArgb());

        String text = name + range;
        font.drawString(text, x + square + 8, top + 7, 0xFFFFFF, true);

        Gui.drawRect(left, bottom - 1, right, bottom, 0x442A2A2A);
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