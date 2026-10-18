package me.texyle.startreminders.gui.hierarchy;

import java.io.IOException;
import java.util.ArrayList;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSlot;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.EnumChatFormatting;

import me.texyle.startreminders.data.Jump;
import me.texyle.startreminders.data.ParkourMap;
import me.texyle.startreminders.gui.GuiDraw;
import me.texyle.startreminders.gui.GuiScissor;

public class GuiPickJumpIndex extends GuiScreen {

    public interface IResultHandler {
        void onPick(int jumpIndex);
        void onCancel();
    }

    private static final int BTN_BACK = 1;

    private final GuiScreen parent;
    private final ParkourMap map;
    private final String title;
    private final IResultHandler handler;

    private GuiButton backButton;
    private JumpPickSlot list;

    // Visual layout (match hierarchy style like GuiJumpList)
    private int panelLeft;
    private int panelRight;
    private int panelTop;
    private int panelBottom;

    private int headerTop;
    private int headerBottom;

    // Colors (ARGB) - same as GuiJumpList
    private static final int COLOR_PANEL_BG = 0xAA0B0B0B;
    private static final int COLOR_PANEL_BORDER = 0xCC2A2A2A;
    private static final int COLOR_HEADER_BG = 0xFF4A4A4A;
    private static final int COLOR_GRID = 0x662A2A2A;
    private static final int COLOR_TITLE = 0xFFFFFF;

    public GuiPickJumpIndex(GuiScreen parent, ParkourMap map, String title, IResultHandler handler) {
        this.parent = parent;
        this.map = map;
        this.title = (title != null) ? title : "Pick jump";
        this.handler = handler;
    }

    @Override
    public void initGui() {
        super.initGui();

        this.buttonList.clear();

        backButton = new GuiButton(BTN_BACK, 8, 8, 60, 20, "Back");
        this.buttonList.add(backButton);

        // Panel geometry (same pattern as GuiJumpList)
        panelLeft = 10;
        panelRight = this.width - 10;
        panelTop = 34;
        panelBottom = this.height - 24;

        // Header row geometry (inside panel)
        headerTop = panelTop + 6;
        headerBottom = headerTop + 16;

        // List area inside panel (below header)
        int listTop = headerBottom + 4;
        int listBottom = panelBottom - 6;

        list = new JumpPickSlot(Minecraft.getMinecraft(), this.width, this.height, listTop, listBottom, 24, map, this);
        list.setPanelBounds(panelLeft + 1, panelRight - 1);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // IMPORTANT: do NOT call drawDefaultBackground() (it draws the dirt)
        drawRect(0, 0, this.width, this.height, 0xFF101010);

        // Title (top area)
        this.drawCenteredString(this.fontRendererObj, title, this.width / 2, 12, COLOR_TITLE);

        drawPanel();

        ArrayList<Jump> js = (map != null) ? map.getJumps() : null;
        if (js == null || js.isEmpty()) {
            this.drawCenteredString(this.fontRendererObj,
                    EnumChatFormatting.RED + "There are no jumps in this map",
                    this.width / 2, this.height / 2, 0xFFFFFF);
        } else if (list != null) {
            list.drawScreen(mouseX, mouseY, partialTicks);
        }

        // Ensure no scissor leaks
        GuiScissor.disable();

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawPanel() {
        // Panel background fill
        drawRect(panelLeft, panelTop, panelRight, panelBottom, COLOR_PANEL_BG);

        // Left/right borders only
        drawRect(panelLeft, panelTop, panelLeft + 1, panelBottom, COLOR_PANEL_BORDER);
        drawRect(panelRight - 1, panelTop, panelRight, panelBottom, COLOR_PANEL_BORDER);

        // Header background
        drawRect(panelLeft + 1, headerTop - 2, panelRight - 1, headerBottom + 2, COLOR_HEADER_BG);

        // Separator under header
        drawRect(panelLeft + 1, headerBottom + 2, panelRight - 1, headerBottom + 3, COLOR_GRID);

        // Header label
        this.fontRendererObj.drawString("Jump", panelLeft + 8, headerTop + 3, 0xFFFFFF, true);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();

        ArrayList<Jump> js = (map != null) ? map.getJumps() : null;
        if (js != null && !js.isEmpty() && list != null) {
            list.handleMouseInput();
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button == null) return;

        if (button.id == BTN_BACK) {
            if (handler != null) handler.onCancel();
            Minecraft.getMinecraft().displayGuiScreen(parent);
        }
    }

    void onPick(int idx) {
        if (handler != null) handler.onPick(idx);
        Minecraft.getMinecraft().displayGuiScreen(parent);
    }

    private static class JumpPickSlot extends GuiSlot {

        private static final int SCROLLBAR_W = 6;
        private static final int SCROLLBAR_PAD_RIGHT = 2; // match other GUI skins

        private final Minecraft mc;
        private final FontRenderer font;
        private final ParkourMap map;
        private final GuiPickJumpIndex parent;

        // Panel bounds for consistent width/scrollbar position
        private int panelLeft = 0;
        private int panelRight = 0;

        // Stored viewport bounds
        private final int slotTop;
        private final int slotBottom;

        // Selection
        private int selectedIndex = -1;

        // Row visuals
        private static final int COLOR_GRID_THIN = 0x442A2A2A;
        private static final int COLOR_ROW_DARK = 0x22101010;
        private static final int COLOR_ROW_LIGHT = 0x33333333;
        private static final int COLOR_ROW_SELECTED = 0x55333333;

        JumpPickSlot(Minecraft mc, int width, int height, int top, int bottom, int slotHeight, ParkourMap map, GuiPickJumpIndex parent) {
            super(mc, width, height, top, bottom, slotHeight);
            this.mc = mc;
            this.font = mc.fontRendererObj;
            this.map = map;
            this.parent = parent;
            this.showSelectionBox = false;

            this.slotTop = top;
            this.slotBottom = bottom;
        }

        void setPanelBounds(int left, int right) {
            this.panelLeft = left;
            this.panelRight = right;
        }

        @Override
        public int getListWidth() {
            // IMPORTANT:
            // Make the content area narrower so entries DO NOT overlap the scrollbar.
            int panelW = (panelRight > panelLeft) ? (panelRight - panelLeft) : super.getListWidth();
            int contentW = panelW - (SCROLLBAR_W + SCROLLBAR_PAD_RIGHT + 2);
            return Math.max(0, contentW);
        }

        @Override
        protected int getScrollBarX() {
            // Keep the scrollbar inside the panel, like other hierarchy lists.
            // GuiSlot uses this as left X of the scrollbar.
            if (panelRight > panelLeft) {
                int barRight = panelRight - SCROLLBAR_PAD_RIGHT;
                return barRight - SCROLLBAR_W;
            }
            return super.getScrollBarX();
        }

        @Override
        protected int getSize() {
            ArrayList<Jump> js = (map != null) ? map.getJumps() : null;
            return (js != null) ? js.size() : 0;
        }

        @Override
        protected void elementClicked(int index, boolean doubleClick, int mouseX, int mouseY) {
            // IMPORTANT:
            // - Single click only selects (does NOT close the GUI).
            // - Double click confirms selection (fixes scrollbar drag being interrupted by accidental pick).
            selectedIndex = index;

            if (doubleClick) {
                if (parent != null) parent.onPick(index);
            }
        }

        @Override
        protected boolean isSelected(int index) {
            return index == selectedIndex;
        }

        @Override
        protected void drawBackground() {
            // Intentionally empty: parent screen draws the panel background.
        }

        @Override
        protected void drawContainerBackground(Tessellator tessellator) {
            // IMPORTANT: prevent vanilla dirt background
        }

        @Override
        protected void overlayBackground(int startY, int endY, int startAlpha, int endAlpha) {
            // IMPORTANT: prevent vanilla fade overlays
        }

        /**
         * Keep vanilla list behavior, but enforce scissor clipping and custom scrollbar skin.
         */
        @Override
        public void drawScreen(int mouseX, int mouseY, float partialTicks) {
            enableScissorForViewport();
            try {
                super.drawScreen(mouseX, mouseY, partialTicks);
            } finally {
                GuiScissor.disable();
            }

            coverVanillaVerticalScrollbar();
            drawVerticalScrollbarSkin();
        }

        private void enableScissorForViewport() {
            int x = (panelRight > panelLeft) ? panelLeft : 0;
            int w = (panelRight > panelLeft) ? (panelRight - panelLeft) : this.width;
            GuiScissor.enable(this.mc, x, slotTop, w, Math.max(0, slotBottom - slotTop));
        }

        private int getViewportTop() {
            return slotTop;
        }

        private int getViewportBottom() {
            return slotBottom;
        }

        private int getViewportLeft() {
            return (panelRight > panelLeft) ? panelLeft : 0;
        }

        private int getViewportRight() {
            return (panelRight > panelLeft) ? panelRight : this.width;
        }

        private void coverVanillaVerticalScrollbar() {
            int visibleH = (getViewportBottom() - getViewportTop());
            int totalH = getContentHeight();
            int maxScroll = Math.max(0, totalH - visibleH);

            if (maxScroll <= 0) {
                return;
            }

            int barLeft = getScrollBarX();
            int barRight = barLeft + SCROLLBAR_W;

            GlStateManager.disableTexture2D();
            GlStateManager.enableBlend();
            GuiDraw.fillRect(barLeft, getViewportTop(), barRight, getViewportBottom(), 0xFF101010);
        }

        private void drawVerticalScrollbarSkin() {
            int visibleH = (getViewportBottom() - getViewportTop());
            int totalH = getContentHeight();
            int maxScroll = Math.max(0, totalH - visibleH);

            if (maxScroll <= 0) {
                return;
            }

            int barRight = getViewportRight() - SCROLLBAR_PAD_RIGHT;
            int barLeft = barRight - SCROLLBAR_W;

            int barTop = getViewportTop();
            int barBottom = getViewportBottom();
            int barH = barBottom - barTop;

            int thumbH = Math.max(24, (int) ((barH * (double) visibleH) / (double) totalH));
            int trackH = barH - thumbH;
            if (trackH < 0) trackH = 0;

            double t = (maxScroll == 0) ? 0.0 : (this.amountScrolled / (double) maxScroll);
            if (t < 0.0) t = 0.0;
            if (t > 1.0) t = 1.0;

            int thumbY = barTop + (int) Math.round(t * trackH);

            GuiDraw.fillRect(barLeft, barTop, barRight, barBottom, ScrollbarSkin.BACKGROUND);
            GuiDraw.fillRect(barLeft, thumbY, barRight, thumbY + thumbH, ScrollbarSkin.THUMB);
        }

        @Override
        protected void drawSlot(int entryID, int insideLeft, int yPos, int insideSlotHeight, int mouseXIn, int mouseYIn) {
            ArrayList<Jump> js = (map != null) ? map.getJumps() : null;
            if (js == null || entryID < 0 || entryID >= js.size()) return;

            Jump j = js.get(entryID);
            String name = (j != null && j.getId() != null && j.getId().trim().length() > 0) ? j.getId() : "<unnamed>";

            int left = (panelRight > panelLeft) ? (panelLeft + 2) : (insideLeft + 2);

            // IMPORTANT:
            // Do NOT draw entries under the scrollbar, otherwise clicking the scrollbar also clicks an entry.
            int barLeft = getScrollBarX();
            int right = (panelRight > panelLeft) ? (barLeft - 1) : (insideLeft + this.getListWidth() - 4);

            int top = yPos;
            int bottom = yPos + insideSlotHeight;

            int zebra = (entryID % 2 == 0) ? COLOR_ROW_DARK : COLOR_ROW_LIGHT;
            drawRect(left, top, right, bottom, zebra);

            if (entryID == selectedIndex) {
                drawRect(left, top, right, bottom, COLOR_ROW_SELECTED);
            }

            String line = (entryID + 1) + ") " + name;
            font.drawString(line, left + 6, top + 7, 0xFFFFFF, true);

            drawRect(left, bottom - 1, right, bottom, COLOR_GRID_THIN);
        }

    }
}