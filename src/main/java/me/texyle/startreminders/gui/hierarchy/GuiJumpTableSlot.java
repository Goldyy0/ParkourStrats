package me.texyle.startreminders.gui.hierarchy;

import java.util.ArrayList;
import java.util.List;

import me.texyle.startreminders.data.Jump;
import me.texyle.startreminders.reminders.Reminder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiSlot;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

public class GuiJumpTableSlot extends GuiSlot {

    public interface ISelectionHandler {
        void onSingleClick(int index);
        void onDoubleClick(int index);
        void onJumpNameClick(int index);
    }

    private final Minecraft mc;
    private final FontRenderer font;
    private final List<Jump> items;
    private final ISelectionHandler handler;

    private int selectedIndex = -1;

    private int lastClickedIndex = -1;
    private long lastClickTimeMs = 0L;

    private int lastRawClickIndex = -1;
    private long lastRawClickTimeMs = 0L;

    // Column layout (pixels)
    private static final int PAD_X = 6;

    public static final int COL_ROW_W = 28;
    public static final int COL_JUMP_W = 110;
    public static final int COL_POS_W = 110;
    public static final int COL_FACING_W = 80;
    public static final int COL_SETUP_W = 90;
    public static final int COL_STRAT_W = 90;
    public static final int COL_STRAFE_W = 70;
    public static final int COL_TURN_W = 60;
    public static final int COL_AUTHOR_W = 90;
    public static final int COL_TIPS_W = 90;

    private static final long DOUBLE_CLICK_WINDOW_MS = 320L;
    private static final long DUPLICATE_CLICK_GUARD_MS = 80L;

    // Grid color (ARGB)
    private static final int COLOR_GRID = 0x662A2A2A;

    // Selection highlight (ARGB)
    private static final int COLOR_ROW_SELECTED = 0x55333333;

    // Optional: subtle fill for Jump column
    private static final int COLOR_JUMP_COL_FILL = 0x22111111;

    // Custom vertical scrollbar skin colors (match horizontal)
    private static final int COLOR_SCROLLBAR_BG = 0x44000000;
    private static final int COLOR_SCROLLBAR_THUMB = 0xAA6666FF;

    // Horizontal scroll
    private int xScroll = 0;

    // Horizontal scrollbar drag state
    private boolean isDraggingScrollbar = false;
    private int dragGrabOffsetX = 0;

    // Cached horizontal scrollbar geometry (from last drawHorizontalScrollbar call)
    private int sbBarLeft = 0;
    private int sbBarRight = 0;
    private int sbYTop = 0;
    private int sbYBottom = 0;
    private int sbThumbX = 0;
    private int sbThumbW = 0;
    private int sbMaxScroll = 0;

    // Flattened rows: one row per strategy (Reminder) with Jump grouping
    private final ArrayList<RowRef> rows = new ArrayList<RowRef>();

    // Reminder formats:
    // New (8): [Position, Facing, Setup, Strategy, Strafe, Turn, Author, Tips]
    private static final int REM_NEW_MIN_SIZE = 8;
    private static final int REM_NEW_SETUP_OFFSET = 2;

    // Previous (6): [Setup, Strategy, Strafe, Turn, Author, Tips]
    private static final int REM_PREV_MIN_SIZE = 6;

    private static class RowRef {
        final Jump jump;
        final Reminder reminder;      // may be null if jump has no strategies
        final int groupRowIndex;      // 0..groupSize-1
        final int groupSize;

        RowRef(Jump jump, Reminder reminder, int groupRowIndex, int groupSize) {
            this.jump = jump;
            this.reminder = reminder;
            this.groupRowIndex = groupRowIndex;
            this.groupSize = groupSize;
        }

        int getOrdinalOneBased() {
            return groupRowIndex + 1;
        }
    }

    public GuiJumpTableSlot(Minecraft mc, int width, int height, int top, int bottom, int slotHeight,
                            List<Jump> items, ISelectionHandler handler) {
        super(mc, width, height, top, bottom, slotHeight);
        this.mc = mc;
        this.font = mc.fontRendererObj;
        this.items = items;
        this.handler = handler;

        // Disable vanilla selection highlight box, but keep vanilla input & layout.
        this.showSelectionBox = false;

        rebuildRows();
    }

    /**
     * Keep vanilla GuiSlot drawing & input, but force scissor clipping around the slot viewport.
     * Then draw our custom vertical scrollbar skin on top.
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
            return; // no scrollbar -> nothing to cover
        }

        int barLeft = getScrollBarX();
        int barRight = barLeft + 6; // vanilla scrollbar width in 1.8.9 GuiSlot

        // Fully opaque fill to prevent any vanilla thumb bleeding through.
        // Pick a color that matches your panel/track. Using the same vibe as COLOR_SCROLLBAR_BG but opaque.
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        drawRectLocal(barLeft, this.top, barRight, this.bottom, 0xFF101010);
    }

    @Override
    public int getListWidth() {
        // Expand clickable row area to the whole panel width.
        // GuiSlot uses this for insideLeft and for mouse hit-tests.
        int w = (this.right - this.left) - 10; // small gutter near scrollbar
        return Math.max(0, w);
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

    public void refreshRows() {
        rebuildRows();
    }

    public int getSelectedJumpIndexInItems() {
        Jump sel = getSelectedItem();
        if (sel == null || items == null) {
            return -1;
        }
        return items.indexOf(sel);
    }

    public void selectJump(Jump jump) {
        if (jump == null || rows == null || rows.isEmpty()) {
            selectedIndex = -1;
            return;
        }

        for (int i = 0; i < rows.size(); i++) {
            RowRef rr = rows.get(i);
            if (rr != null && rr.jump == jump) {
                selectedIndex = i;
                return;
            }
        }

        for (int i = 0; i < rows.size(); i++) {
            RowRef rr = rows.get(i);
            if (rr != null && rr.jump != null && rr.jump.equals(jump)) {
                selectedIndex = i;
                return;
            }
        }

        selectedIndex = -1;
    }

    private void rebuildRows() {
        rows.clear();

        if (items == null) {
            return;
        }

        for (Jump j : items) {
            if (j == null) {
                continue;
            }

            List<Reminder> rems = j.getReminders();

            if (rems == null || rems.isEmpty()) {
                rows.add(new RowRef(j, null, 0, 1));
                continue;
            }

            int groupSize = rems.size();
            for (int i = 0; i < rems.size(); i++) {
                rows.add(new RowRef(j, rems.get(i), i, groupSize));
            }
        }

        if (selectedIndex >= rows.size()) {
            selectedIndex = rows.isEmpty() ? -1 : (rows.size() - 1);
        }
    }

    public void setPanelBounds(int left, int right) {
        this.left = left;
        this.right = right;
        this.width = right - left;
    }

    public void handleHorizontalScrollWheel() {
        int dWheel = org.lwjgl.input.Mouse.getDWheel();
        if (dWheel == 0) {
            return;
        }

        boolean shiftDown = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
        if (!shiftDown) {
            return;
        }

        int visible = getVisibleContentWidth();
        int total = getTotalContentWidth();
        int maxScroll = Math.max(0, total - visible);

        if (maxScroll <= 0) {
            xScroll = 0;
            return;
        }

        int step = 18;
        if (dWheel > 0) {
            xScroll -= step;
        } else {
            xScroll += step;
        }

        if (xScroll < 0) xScroll = 0;
        if (xScroll > maxScroll) xScroll = maxScroll;
    }

    public int getHeaderStartX() {
        return left + PAD_X - xScroll;
    }

    public int getVisibleContentWidth() {
        return Math.max(0, (right - left) - (PAD_X * 2));
    }

    public int getTotalContentWidth() {
        return (COL_ROW_W + COL_JUMP_W + COL_POS_W + COL_FACING_W + COL_SETUP_W + COL_STRAT_W
                + COL_STRAFE_W + COL_TURN_W + COL_AUTHOR_W + COL_TIPS_W) + (PAD_X * 2);
    }

    public void drawVerticalSeparators(int yTop, int yBottom) {
        int x = getHeaderStartX();

        x += COL_ROW_W;    drawVLineLocal(x, yTop, yBottom, COLOR_GRID);
        x += COL_JUMP_W;   drawVLineLocal(x, yTop, yBottom, COLOR_GRID);
        x += COL_POS_W;    drawVLineLocal(x, yTop, yBottom, COLOR_GRID);
        x += COL_FACING_W; drawVLineLocal(x, yTop, yBottom, COLOR_GRID);
        x += COL_SETUP_W;  drawVLineLocal(x, yTop, yBottom, COLOR_GRID);
        x += COL_STRAT_W;  drawVLineLocal(x, yTop, yBottom, COLOR_GRID);
        x += COL_STRAFE_W; drawVLineLocal(x, yTop, yBottom, COLOR_GRID);
        x += COL_TURN_W;   drawVLineLocal(x, yTop, yBottom, COLOR_GRID);
        x += COL_AUTHOR_W; drawVLineLocal(x, yTop, yBottom, COLOR_GRID);
    }

    public void drawHorizontalScrollbar(int yTop, int yBottom) {
        int visible = getVisibleContentWidth();
        int total = getTotalContentWidth();

        if (total <= visible) {
            xScroll = 0;

            sbBarLeft = sbBarRight = sbYTop = sbYBottom = sbThumbX = sbThumbW = 0;
            sbMaxScroll = 0;
            isDraggingScrollbar = false;
            return;
        }

        int barLeft = left + PAD_X;
        int barRight = right - PAD_X;
        int barW = barRight - barLeft;

        int thumbW = Math.max(24, (int) ((barW * (double) visible) / (double) total));
        int maxScroll = total - visible;

        int thumbX = barLeft + (int) ((barW - thumbW) * (xScroll / (double) maxScroll));

        sbBarLeft = barLeft;
        sbBarRight = barRight;
        sbYTop = yTop;
        sbYBottom = yBottom;
        sbThumbX = thumbX;
        sbThumbW = thumbW;
        sbMaxScroll = maxScroll;

        drawRectLocal(barLeft, yTop, barRight, yBottom, 0x44000000);
        drawRectLocal(thumbX, yTop, thumbX + thumbW, yBottom, 0xAA6666FF);
    }

    public void updateHorizontalScrollbarDrag(int mouseX, int mouseY) {
        if (sbMaxScroll <= 0) {
            isDraggingScrollbar = false;
            return;
        }

        boolean leftDown = org.lwjgl.input.Mouse.isButtonDown(0);

        if (!leftDown) {
            isDraggingScrollbar = false;
            return;
        }

        if (isDraggingScrollbar) {
            int trackW = (sbBarRight - sbBarLeft) - sbThumbW;
            if (trackW <= 0) {
                xScroll = 0;
                return;
            }

            int thumbLeft = mouseX - dragGrabOffsetX;
            if (thumbLeft < sbBarLeft) thumbLeft = sbBarLeft;
            if (thumbLeft > sbBarLeft + trackW) thumbLeft = sbBarLeft + trackW;

            double t = (thumbLeft - sbBarLeft) / (double) trackW;
            xScroll = (int) Math.round(t * sbMaxScroll);

            if (xScroll < 0) xScroll = 0;
            if (xScroll > sbMaxScroll) xScroll = sbMaxScroll;
            return;
        }

        if (mouseY >= sbYTop && mouseY <= sbYBottom && mouseX >= sbBarLeft && mouseX <= sbBarRight) {
            int thumbLeft = sbThumbX;
            int thumbRight = sbThumbX + sbThumbW;

            if (mouseX >= thumbLeft && mouseX <= thumbRight) {
                isDraggingScrollbar = true;
                dragGrabOffsetX = mouseX - thumbLeft;
                return;
            }

            int desiredThumbLeft = mouseX - (sbThumbW / 2);
            int trackW = (sbBarRight - sbBarLeft) - sbThumbW;
            if (trackW <= 0) {
                xScroll = 0;
                return;
            }

            if (desiredThumbLeft < sbBarLeft) desiredThumbLeft = sbBarLeft;
            if (desiredThumbLeft > sbBarLeft + trackW) desiredThumbLeft = sbBarLeft + trackW;

            double t = (desiredThumbLeft - sbBarLeft) / (double) trackW;
            xScroll = (int) Math.round(t * sbMaxScroll);

            if (xScroll < 0) xScroll = 0;
            if (xScroll > sbMaxScroll) xScroll = sbMaxScroll;

            isDraggingScrollbar = true;
            dragGrabOffsetX = mouseX - desiredThumbLeft;
        }
    }

    private boolean isMouseOverScrollbar(int mouseX, int mouseY) {
        if (sbMaxScroll <= 0) return false;
        return mouseX >= sbBarLeft && mouseX <= sbBarRight && mouseY >= sbYTop && mouseY <= sbYBottom;
    }

    @Override
    protected int getSize() {
        return rows != null ? rows.size() : 0;
    }

    @Override
    protected void elementClicked(int index, boolean ignoredDoubleClickFlag, int mouseX, int mouseY) {
        if (isMouseOverScrollbar(mouseX, mouseY)) {
            return;
        }

        long now = Minecraft.getSystemTime();

        if (index == lastRawClickIndex && (now - lastRawClickTimeMs) <= DUPLICATE_CLICK_GUARD_MS) {
            return;
        }
        lastRawClickIndex = index;
        lastRawClickTimeMs = now;

        selectedIndex = index;

        if (handler != null) {
            handler.onSingleClick(index);
        }

        boolean isDouble = (index == lastClickedIndex) && ((now - lastClickTimeMs) <= DOUBLE_CLICK_WINDOW_MS);

        lastClickedIndex = index;
        lastClickTimeMs = now;

        boolean jumpColumnClicked = isJumpColumnClicked(mouseX, mouseY);
        boolean shiftDown = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);

        if (jumpColumnClicked && handler != null) {
            handler.onJumpNameClick(index);

            // Optional: shift+click behaves like "open" (double click)
            if (shiftDown) {
                handler.onDoubleClick(index);
                lastClickedIndex = -1;
                lastClickTimeMs = 0L;
                return;
            }
        }

        if (isDouble && handler != null) {
            handler.onDoubleClick(index);
            lastClickedIndex = -1;
            lastClickTimeMs = 0L;
        }
    }

    /**
     * Robust column hit-test:
     * - Requires click inside the visible content region (between PAD_X paddings)
     * - Uses the same x-origin as drawing (getHeaderStartX)
     * - Works with horizontal scroll
     */
    private boolean isJumpColumnClicked(int mouseX, int mouseY) {
        // Must be within slot vertical viewport
        if (mouseY < this.top || mouseY > this.bottom) {
            return false;
        }

        // Must be within the visible content area (exclude padding zones)
        int contentLeftVisible = this.left + PAD_X;
        int contentRightVisible = this.right - PAD_X;

        if (mouseX < contentLeftVisible || mouseX > contentRightVisible) {
            return false;
        }

        // Compute Jump column bounds in screen space using the same origin as drawing
        int baseX = getHeaderStartX(); // left + PAD_X - xScroll
        int jumpLeft = baseX + COL_ROW_W;
        int jumpRight = jumpLeft + COL_JUMP_W;

        return mouseX >= jumpLeft && mouseX <= jumpRight;
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
    protected void drawContainerBackground(Tessellator tessellator) {
        // Intentionally empty: prevents GuiSlot from drawing default dirt/options background.
    }

    @Override
    protected void overlayBackground(int startY, int endY, int startAlpha, int endAlpha) {
        // Intentionally empty: prevents the top/bottom dark overlays.
    }

    @Override
    protected void drawSlot(int entryID, int insideLeft, int yPos, int insideSlotHeight, int mouseXIn, int mouseYIn) {
        if (entryID == selectedIndex) {
            drawRectLocal(this.left, yPos, this.right, yPos + this.slotHeight, COLOR_ROW_SELECTED);
        }

        if (rows == null || entryID < 0 || entryID >= rows.size()) {
            return;
        }

        RowRef row = rows.get(entryID);
        if (row == null || row.jump == null) {
            return;
        }

        Reminder r = row.reminder;

        int rowTop = yPos;
        int rowBottom = yPos + this.slotHeight;

        int x = getHeaderStartX();

        // Row number
        drawCellWithGrid(String.valueOf(entryID + 1), x, rowTop, COL_ROW_W, rowBottom);
        x += COL_ROW_W;

        // Jump column
        drawJumpOrdinalCellWithGrid(row, x, rowTop, COL_JUMP_W, rowBottom);
        x += COL_JUMP_W;

        // Position
        drawCellWithGrid(getPositionFromReminder(r), x, rowTop, COL_POS_W, rowBottom);
        x += COL_POS_W;

        // Facing
        drawCellWithGrid(getFacingFromReminder(r), x, rowTop, COL_FACING_W, rowBottom);
        x += COL_FACING_W;

        // Setup
        drawCellWithGrid(getReminderField(r, 0), x, rowTop, COL_SETUP_W, rowBottom);
        x += COL_SETUP_W;

        // Strategy
        drawCellWithGrid(getReminderField(r, 1), x, rowTop, COL_STRAT_W, rowBottom);
        x += COL_STRAT_W;

        // Strafe
        drawCellWithGrid(getReminderField(r, 2), x, rowTop, COL_STRAFE_W, rowBottom);
        x += COL_STRAFE_W;

        // Turn
        drawCellWithGrid(getReminderField(r, 3), x, rowTop, COL_TURN_W, rowBottom);
        x += COL_TURN_W;

        // Author
        drawCellWithGrid(getReminderField(r, 4), x, rowTop, COL_AUTHOR_W, rowBottom);
        x += COL_AUTHOR_W;

        // Tips
        drawCellWithGrid(getReminderField(r, 5), x, rowTop, COL_TIPS_W, rowBottom);
    }

    private void drawJumpOrdinalCellWithGrid(RowRef row, int x, int yPos, int w, int rowBottom) {
        drawRectLocal(x, yPos, x + w, rowBottom, COLOR_JUMP_COL_FILL);

        String jumpId = row.jump.getId() != null ? row.jump.getId() : "<null>";
        int ordinal = row.getOrdinalOneBased();
        String text = jumpId + " (" + ordinal + ")";

        drawCellUnderline(text, x, yPos, w);

        drawCellBorder(x, yPos, x + w, rowBottom);
    }

    private void drawCellWithGrid(String text, int x, int yPos, int w, int rowBottom) {
        drawCell(text, x, yPos, w);
        drawCellBorder(x, yPos, x + w, rowBottom);
    }

    private void drawCellBorder(int left, int top, int right, int bottom) {
        // top
        drawRectLocal(left, top, right, top + 1, COLOR_GRID);
        // bottom
        drawRectLocal(left, bottom - 1, right, bottom, COLOR_GRID);
        // left
        drawRectLocal(left, top, left + 1, bottom, COLOR_GRID);
        // right
        drawRectLocal(right - 1, top, right, bottom, COLOR_GRID);
    }

    private static String getPositionFromReminder(Reminder r) {
        if (r == null || r.lines == null) return "";
        if (r.lines.size() >= REM_NEW_MIN_SIZE) {
            return nullSafe(r.lines.get(0));
        }
        return "";
    }

    private static String getFacingFromReminder(Reminder r) {
        if (r == null || r.lines == null) return "";
        if (r.lines.size() >= REM_NEW_MIN_SIZE) {
            return nullSafe(r.lines.get(1));
        }
        return "";
    }

    private static String getReminderField(Reminder r, int fieldIdx) {
        if (r == null || r.lines == null || fieldIdx < 0 || fieldIdx > 5) {
            return "";
        }

        // New (8): [Position, Facing, Setup, Strategy, Strafe, Turn, Author, Tips]
        if (r.lines.size() >= REM_NEW_MIN_SIZE) {
            return getLineSafe(r.lines, REM_NEW_SETUP_OFFSET + fieldIdx);
        }

        // Previous (6): [Setup, Strategy, Strafe, Turn, Author, Tips]
        if (r.lines.size() >= REM_PREV_MIN_SIZE) {
            return getLineSafe(r.lines, fieldIdx);
        }

        // Very old legacy (<6): map directly so legacy[0] becomes Setup, legacy[1] Strategy, etc.
        return getLineSafe(r.lines, fieldIdx);
    }

    private static String getLineSafe(ArrayList<String> lines, int idx) {
        if (lines == null || idx < 0 || idx >= lines.size()) {
            return "";
        }
        String s = lines.get(idx);
        return s != null ? s : "";
    }

    private static String nullSafe(String s) {
        return s != null ? s : "";
    }

    private void drawCell(String text, int x, int yPos, int w) {
        String safe = text != null ? text : "";
        String trimmed = font.trimStringToWidth(safe, Math.max(0, w - 6));
        font.drawString(trimmed, x + 2, yPos + 3, 0xFFFFFF, true);
    }

    private void drawCellUnderline(String text, int x, int yPos, int w) {
        String safe = text != null ? text : "";
        String trimmed = font.trimStringToWidth(safe, Math.max(0, w - 6));
        font.drawString("\u00A7n" + trimmed + "\u00A7r", x + 2, yPos + 3, 0xFFFFFF, true);
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    public Jump getSelectedItem() {
        if (rows == null) return null;
        if (selectedIndex < 0 || selectedIndex >= rows.size()) return null;
        RowRef row = rows.get(selectedIndex);
        return row != null ? row.jump : null;
    }

    public void setSelectedIndex(int idx) {
        this.selectedIndex = idx;
    }

    private static void drawVLineLocal(int x, int y1, int y2, int color) {
        if (y2 < y1) {
            int tmp = y1;
            y1 = y2;
            y2 = tmp;
        }
        drawRectLocal(x, y1, x + 1, y2 + 1, color);
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