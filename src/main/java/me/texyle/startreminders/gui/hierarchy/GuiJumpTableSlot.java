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
import org.lwjgl.input.Mouse;
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

    // Cell padding
    private static final int CELL_PAD_X = 5;
    private static final int CELL_PAD_Y = 4;

    // Grid color (ARGB) - lighter + less "heavy"
    private static final int COLOR_GRID = 0x442A2A2A;

    // Zebra row fills (ARGB)
    private static final int COLOR_ROW_DARK = 0x22101010;
    private static final int COLOR_ROW_LIGHT = 0x33333333; // <- make this lighter if you want stronger contrast

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

    // Selection highlight (ARGB)
    private static final int COLOR_ROW_SELECTED = 0x55333333;

    // Optional: subtle fill for Jump column
    private static final int COLOR_JUMP_COL_FILL = 0x22111111;

    // Custom vertical scrollbar skin colors (match horizontal)
    private static final int COLOR_SCROLLBAR_BG = 0x44000000;
    private static final int COLOR_SCROLLBAR_THUMB = 0xAA6666FF;

    // Horizontal scroll (keep a smooth value to avoid "snappy" dragging)
    private double xScrollD = 0.0;

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

    // Cached vertical scrollbar geometry (from last drawVerticalScrollbarSkin call)
    private int vsbBarLeft = 0;
    private int vsbBarRight = 0;
    private int vsbBarTop = 0;
    private int vsbBarBottom = 0;
    private int vsbThumbY = 0;
    private int vsbThumbH = 0;
    private int vsbMaxScroll = 0;

    // Vertical scrollbar drag state
    private boolean isDraggingVScrollbar = false;
    private int vDragGrabOffsetY = 0;

    // Flattened rows: one row per strategy (Reminder) with Jump grouping
    private final ArrayList<RowRef> rows = new ArrayList<RowRef>();

    // Variable row heights cache (content-space, not screen-space)
    private final ArrayList<Integer> rowHeights = new ArrayList<Integer>();
    private final ArrayList<Integer> rowTops = new ArrayList<Integer>();
    private int contentHeightPx = 0;

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

        // Disable vanilla selection highlight box.
        this.showSelectionBox = false;

        rebuildRows();
    }

    /**
     * IMPORTANT:
     * We do NOT call super.drawScreen() anymore because GuiSlot forces a uniform slotHeight.
     * We render & scroll ourselves to support variable row heights.
     */
    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        enableScissorForSlotViewport();
        try {
            drawVariableHeightList(mouseX, mouseY);
        } finally {
            disableScissor();
        }

        // Hide vanilla GuiSlot scrollbar (super.drawScreen is no longer called, but keep this safe)
        coverVanillaVerticalScrollbar();

        // Draw our custom vertical scrollbar on top.
        drawVerticalScrollbarSkin();

        // Handle vertical scrollbar drag (uses cached geometry from drawVerticalScrollbarSkin)
        updateVerticalScrollbarDrag(mouseX, mouseY);
    }

    private void drawVariableHeightList(int mouseX, int mouseY) {
        clampVerticalScroll();

        int scroll = (int) this.amountScrolled;

        int first = findFirstVisibleRow(scroll);
        if (first < 0) {
            return;
        }

        int y = this.top - scroll + rowTops.get(first);

        for (int i = first; i < rows.size(); i++) {
            int h = rowHeights.get(i);
            int rowTop = y;
            int rowBottom = y + h;

            if (rowTop > this.bottom) {
                break;
            }

            if (rowBottom >= this.top) {
                drawRow(i, rowTop, rowBottom);
            }

            y += h;
        }
    }

    private void drawRow(int entryID, int rowTop, int rowBottom) {
        // Zebra background first
        int zebra = (entryID % 2 == 0) ? COLOR_ROW_DARK : COLOR_ROW_LIGHT;
        drawRectLocal(this.left, rowTop, this.right, rowBottom, zebra);

        // Selection overlay
        if (entryID == selectedIndex) {
            drawRectLocal(this.left, rowTop, this.right, rowBottom, COLOR_ROW_SELECTED);
        }

        if (rows == null || entryID < 0 || entryID >= rows.size()) {
            return;
        }

        RowRef row = rows.get(entryID);
        if (row == null || row.jump == null) {
            return;
        }

        Reminder r = row.reminder;

        int x = getHeaderStartX();

        // Row number
        drawCellWithGrid(String.valueOf(entryID + 1), x, rowTop, COL_ROW_W, rowBottom, true);
        x += COL_ROW_W;

        // Jump column
        drawJumpOrdinalCellWithGrid(row, x, rowTop, COL_JUMP_W, rowBottom, false);
        x += COL_JUMP_W;

        // Position
        drawCellWithGrid(getPositionFromReminder(r), x, rowTop, COL_POS_W, rowBottom, false);
        x += COL_POS_W;

        // Facing
        drawCellWithGrid(getFacingFromReminder(r), x, rowTop, COL_FACING_W, rowBottom, false);
        x += COL_FACING_W;

        // Setup
        drawCellWithGrid(getReminderField(r, 0), x, rowTop, COL_SETUP_W, rowBottom, false);
        x += COL_SETUP_W;

        // Strategy
        drawCellWithGrid(getReminderField(r, 1), x, rowTop, COL_STRAT_W, rowBottom, false);
        x += COL_STRAT_W;

        // Strafe
        drawCellWithGrid(getReminderField(r, 2), x, rowTop, COL_STRAFE_W, rowBottom, false);
        x += COL_STRAFE_W;

        // Turn
        drawCellWithGrid(getReminderField(r, 3), x, rowTop, COL_TURN_W, rowBottom, false);
        x += COL_TURN_W;

        // Author
        drawCellWithGrid(getReminderField(r, 4), x, rowTop, COL_AUTHOR_W, rowBottom, false);
        x += COL_AUTHOR_W;

        // Tips
        drawCellWithGrid(getReminderField(r, 5), x, rowTop, COL_TIPS_W, rowBottom, false);
    }

    private void clampVerticalScroll() {
        int visibleH = (this.bottom - this.top);
        int maxScroll = Math.max(0, getContentHeight() - visibleH);

        if (this.amountScrolled < 0) {
            this.amountScrolled = 0;
        } else if (this.amountScrolled > maxScroll) {
            this.amountScrolled = maxScroll;
        }
    }

    private int findFirstVisibleRow(int scroll) {
        if (rowTops == null || rowTops.isEmpty()) {
            return -1;
        }

        // Linear search is fine for typical sizes; can be optimized to binary later.
        for (int i = 0; i < rowTops.size(); i++) {
            int top = rowTops.get(i);
            int bottom = top + rowHeights.get(i);
            if (bottom > scroll) {
                return i;
            }
        }
        return rowTops.size() - 1;
    }

    private int getRowIndexAt(int mouseX, int mouseY) {
        if (mouseY < this.top || mouseY > this.bottom) {
            return -1;
        }

        int yInContent = (int) this.amountScrolled + (mouseY - this.top);

        for (int i = 0; i < rowTops.size(); i++) {
            int t = rowTops.get(i);
            int b = t + rowHeights.get(i);
            if (yInContent >= t && yInContent < b) {
                return i;
            }
        }
        return -1;
    }

    /**
     * We override GuiSlot's input loop because super.handleMouseInput assumes uniform slotHeight.
     */
    @Override
    public void handleMouseInput() {
        // Vertical wheel scroll (Shift wheel is handled by parent for horizontal scroll)
        int dWheel = Mouse.getEventDWheel();
        boolean shiftDown = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);

        if (!shiftDown && dWheel != 0) {
            int step = 18;
            if (dWheel > 0) {
                this.amountScrolled -= step;
            } else {
                this.amountScrolled += step;
            }
            clampVerticalScroll();
        }

        // Click handling
        if (Mouse.getEventButton() == 0 && Mouse.getEventButtonState()) {
            int mouseX = getMouseXScaled();
            int mouseY = getMouseYScaled();

            // Ignore clicks outside the list viewport
            if (mouseY >= this.top && mouseY <= this.bottom) {
                // Do not treat scrollbar clicks as row clicks
                if (isMouseOverVerticalScrollbar(mouseX, mouseY)) {
                    return;
                }

                int idx = getRowIndexAt(mouseX, mouseY);
                if (idx >= 0 && idx < getSize()) {
                    elementClicked(idx, false, mouseX, mouseY);
                }
            }
        }
    }

    private int getMouseXScaled() {
        return Mouse.getEventX() * this.width / this.mc.displayWidth;
    }

    private int getMouseYScaled() {
        return this.height - (Mouse.getEventY() * this.height / this.mc.displayHeight) - 1;
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

    @Override
    public int getListWidth() {
        int w = (this.right - this.left) - 10;
        return Math.max(0, w);
    }

    private void drawVerticalScrollbarSkin() {
        int visibleH = (this.bottom - this.top);
        int totalH = getContentHeight();
        int maxScroll = Math.max(0, totalH - visibleH);

        if (maxScroll <= 0) {
            // Clear cached geometry
            vsbBarLeft = vsbBarRight = vsbBarTop = vsbBarBottom = vsbThumbY = vsbThumbH = 0;
            vsbMaxScroll = 0;
            isDraggingVScrollbar = false;
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

        // Cache geometry for hit-test & drag
        vsbBarLeft = barLeft;
        vsbBarRight = barRight;
        vsbBarTop = barTop;
        vsbBarBottom = barBottom;
        vsbThumbY = thumbY;
        vsbThumbH = thumbH;
        vsbMaxScroll = maxScroll;

        drawRectLocal(barLeft, barTop, barRight, barBottom, COLOR_SCROLLBAR_BG);
        drawRectLocal(barLeft, thumbY, barRight, thumbY + thumbH, COLOR_SCROLLBAR_THUMB);
    }

    private boolean isMouseOverVerticalScrollbar(int mouseX, int mouseY) {
        if (vsbMaxScroll <= 0) return false;
        return mouseX >= vsbBarLeft && mouseX <= vsbBarRight && mouseY >= vsbBarTop && mouseY <= vsbBarBottom;
    }

    private void updateVerticalScrollbarDrag(int mouseX, int mouseY) {
        if (vsbMaxScroll <= 0) {
            isDraggingVScrollbar = false;
            return;
        }

        boolean leftDown = Mouse.isButtonDown(0);

        if (!leftDown) {
            isDraggingVScrollbar = false;
            return;
        }

        // Only react when cursor is in the scrollbar area, unless we're already dragging
        if (!isMouseOverVerticalScrollbar(mouseX, mouseY)) {
            if (!isDraggingVScrollbar) {
                return;
            }
        }

        int thumbTopNow = vsbThumbY;
        int thumbBottomNow = vsbThumbY + vsbThumbH;

        int trackH = (vsbBarBottom - vsbBarTop) - vsbThumbH;
        if (trackH <= 0) {
            this.amountScrolled = 0;
            return;
        }

        if (isDraggingVScrollbar) {
            double desiredThumbTop = (double) mouseY - (double) vDragGrabOffsetY;
            if (desiredThumbTop < vsbBarTop) desiredThumbTop = vsbBarTop;
            if (desiredThumbTop > vsbBarTop + trackH) desiredThumbTop = vsbBarTop + trackH;

            double t = (desiredThumbTop - vsbBarTop) / (double) trackH;
            if (t < 0.0) t = 0.0;
            if (t > 1.0) t = 1.0;

            this.amountScrolled = (float) (t * (double) vsbMaxScroll);
            clampVerticalScroll();
            return;
        }

        // Not currently dragging: start drag if clicking thumb
        if (mouseY >= thumbTopNow && mouseY <= thumbBottomNow) {
            isDraggingVScrollbar = true;
            vDragGrabOffsetY = mouseY - thumbTopNow;
            return;
        }

        // Click on track: center thumb under cursor then start dragging
        double desiredThumbTop = (double) mouseY - (vsbThumbH / 2.0);
        if (desiredThumbTop < vsbBarTop) desiredThumbTop = vsbBarTop;
        if (desiredThumbTop > vsbBarTop + trackH) desiredThumbTop = vsbBarTop + trackH;

        double t = (desiredThumbTop - vsbBarTop) / (double) trackH;
        if (t < 0.0) t = 0.0;
        if (t > 1.0) t = 1.0;

        this.amountScrolled = (float) (t * (double) vsbMaxScroll);
        clampVerticalScroll();

        isDraggingVScrollbar = true;
        vDragGrabOffsetY = (int) Math.round(mouseY - desiredThumbTop);
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
            rebuildRowLayout();
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

        rebuildRowLayout();
        clampVerticalScroll();
    }

    private void rebuildRowLayout() {
        rowHeights.clear();
        rowTops.clear();
        contentHeightPx = 0;

        if (rows == null || rows.isEmpty()) {
            return;
        }

        for (int i = 0; i < rows.size(); i++) {
            rowTops.add(contentHeightPx);

            RowRef rr = rows.get(i);
            int h = computeRowHeight(i, rr);

            rowHeights.add(h);
            contentHeightPx += h;
        }
    }

    private int computeRowHeight(int rowIndex, RowRef rr) {
        // Base minimum height close to your old 22px
        int minH = 22;

        if (rr == null) {
            return minH;
        }

        Reminder r = rr.reminder;

        int maxLines = 1;

        maxLines = Math.max(maxLines, countWrappedLines(String.valueOf(rowIndex + 1), COL_ROW_W));
        maxLines = Math.max(maxLines, countWrappedLines(getJumpCellText(rr), COL_JUMP_W));
        maxLines = Math.max(maxLines, countWrappedLines(getPositionFromReminder(r), COL_POS_W));
        maxLines = Math.max(maxLines, countWrappedLines(getFacingFromReminder(r), COL_FACING_W));
        maxLines = Math.max(maxLines, countWrappedLines(getReminderField(r, 0), COL_SETUP_W));
        maxLines = Math.max(maxLines, countWrappedLines(getReminderField(r, 1), COL_STRAT_W));
        maxLines = Math.max(maxLines, countWrappedLines(getReminderField(r, 2), COL_STRAFE_W));
        maxLines = Math.max(maxLines, countWrappedLines(getReminderField(r, 3), COL_TURN_W));
        maxLines = Math.max(maxLines, countWrappedLines(getReminderField(r, 4), COL_AUTHOR_W));
        maxLines = Math.max(maxLines, countWrappedLines(getReminderField(r, 5), COL_TIPS_W));

        int lineH = font.FONT_HEIGHT;
        int h = (CELL_PAD_Y * 2) + (maxLines * lineH);

        return Math.max(minH, h);
    }

    private int countWrappedLines(String text, int colW) {
        int innerW = Math.max(0, colW - (CELL_PAD_X * 2));
        if (innerW <= 0) {
            return 1;
        }

        String safe = (text != null) ? text : "";
        if (safe.trim().isEmpty()) {
            return 1;
        }

        List<String> lines = font.listFormattedStringToWidth(safe, innerW);
        if (lines == null || lines.isEmpty()) {
            return 1;
        }
        return lines.size();
    }

    private String getJumpCellText(RowRef row) {
        String jumpId = row.jump.getId() != null ? row.jump.getId() : "<null>";
        int ordinal = row.getOrdinalOneBased();
        return jumpId + " (" + ordinal + ")";
    }

    public void setPanelBounds(int left, int right) {
        this.left = left;
        this.right = right;

        // DO NOT modify this.width here.
        // GuiSlot expects this.width to remain the full GUI/screen width for correct mouse scaling.

        // Width changes can affect wrapping; safest to rebuild.
        rebuildRowLayout();
        clampVerticalScroll();
    }

    public void handleHorizontalScrollWheel() {
        int dWheel = Mouse.getDWheel();
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
            xScrollD = 0.0;
            return;
        }

        double step = 18.0;
        if (dWheel > 0) {
            xScrollD -= step;
        } else {
            xScrollD += step;
        }

        if (xScrollD < 0.0) xScrollD = 0.0;
        if (xScrollD > maxScroll) xScrollD = maxScroll;
    }

    public int getHeaderStartX() {
        return left + PAD_X - (int) Math.round(xScrollD);
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
            xScrollD = 0.0;

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

        // Clamp the smooth scroll
        if (xScrollD < 0.0) xScrollD = 0.0;
        if (xScrollD > maxScroll) xScrollD = maxScroll;

        int trackW = Math.max(0, barW - thumbW);

        double t = (maxScroll <= 0) ? 0.0 : (xScrollD / (double) maxScroll);
        if (t < 0.0) t = 0.0;
        if (t > 1.0) t = 1.0;

        int thumbX = barLeft + (int) Math.floor(t * trackW);

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

        boolean leftDown = Mouse.isButtonDown(0);

        if (!leftDown) {
            isDraggingScrollbar = false;
            return;
        }

        // Only react to interactions inside the scrollbar track area
        if (!(mouseY >= sbYTop && mouseY <= sbYBottom && mouseX >= sbBarLeft && mouseX <= sbBarRight)) {
            if (!isDraggingScrollbar) {
                return;
            }
        }

        int thumbLeftNow = sbThumbX;
        int thumbRightNow = sbThumbX + sbThumbW;

        if (isDraggingScrollbar) {
            int trackW = (sbBarRight - sbBarLeft) - sbThumbW;
            if (trackW <= 0) {
                xScrollD = 0.0;
                return;
            }

            // Compute desired thumb position in track space (pixel-precise)
            double desiredThumbLeft = (double) mouseX - (double) dragGrabOffsetX;
            if (desiredThumbLeft < sbBarLeft) desiredThumbLeft = sbBarLeft;
            if (desiredThumbLeft > sbBarLeft + trackW) desiredThumbLeft = sbBarLeft + trackW;

            double t = (desiredThumbLeft - sbBarLeft) / (double) trackW;
            if (t < 0.0) t = 0.0;
            if (t > 1.0) t = 1.0;

            xScrollD = t * (double) sbMaxScroll;

            if (xScrollD < 0.0) xScrollD = 0.0;
            if (xScrollD > sbMaxScroll) xScrollD = sbMaxScroll;
            return;
        }

        // Not currently dragging: start drag if clicking thumb, otherwise jump-to-position and start drag
        if (mouseX >= thumbLeftNow && mouseX <= thumbRightNow) {
            isDraggingScrollbar = true;
            dragGrabOffsetX = mouseX - thumbLeftNow;
            return;
        }

        // Click on track: center thumb under cursor, then start dragging
        int trackW = (sbBarRight - sbBarLeft) - sbThumbW;
        if (trackW <= 0) {
            xScrollD = 0.0;
            return;
        }

        double desiredThumbLeft = (double) mouseX - (sbThumbW / 2.0);
        if (desiredThumbLeft < sbBarLeft) desiredThumbLeft = sbBarLeft;
        if (desiredThumbLeft > sbBarLeft + trackW) desiredThumbLeft = sbBarLeft + trackW;

        double t = (desiredThumbLeft - sbBarLeft) / (double) trackW;
        if (t < 0.0) t = 0.0;
        if (t > 1.0) t = 1.0;

        xScrollD = t * (double) sbMaxScroll;

        isDraggingScrollbar = true;
        dragGrabOffsetX = (int) Math.round(mouseX - desiredThumbLeft);
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
    protected int getContentHeight() {
        return contentHeightPx;
    }

    @Override
    protected void elementClicked(int index, boolean ignoredDoubleClickFlag, int mouseX, int mouseY) {
        if (isMouseOverScrollbar(mouseX, mouseY) || isMouseOverVerticalScrollbar(mouseX, mouseY)) {
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

    private boolean isJumpColumnClicked(int mouseX, int mouseY) {
        if (mouseY < this.top || mouseY > this.bottom) {
            return false;
        }

        int contentLeftVisible = this.left + PAD_X;
        int contentRightVisible = this.right - PAD_X;

        if (mouseX < contentLeftVisible || mouseX > contentRightVisible) {
            return false;
        }

        int baseX = getHeaderStartX();
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
        // Intentionally empty.
    }

    @Override
    protected void overlayBackground(int startY, int endY, int startAlpha, int endAlpha) {
        // Intentionally empty.
    }

    // Not used anymore (we render in drawRow), but keep method override to avoid accidental vanilla calls.
    @Override
    protected void drawSlot(int entryID, int insideLeft, int yPos, int insideSlotHeight, int mouseXIn, int mouseYIn) { }

    private void drawJumpOrdinalCellWithGrid(RowRef row, int x, int yTop, int w, int yBottom, boolean isFirstCol) {
        drawRectLocal(x, yTop, x + w, yBottom, COLOR_JUMP_COL_FILL);

        String text = getJumpCellText(row);

        drawCellUnderline(text, x, yTop, w, yBottom);

        drawCellBorderThin(x, yTop, x + w, yBottom, isFirstCol, false);
    }

    private void drawCellWithGrid(String text, int x, int yTop, int w, int yBottom, boolean isFirstCol) {
        drawCell(text, x, yTop, w, yBottom);
        drawCellBorderThin(x, yTop, x + w, yBottom, isFirstCol, false);
    }

    private void drawCellBorderThin(int left, int top, int right, int bottom, boolean drawLeft, boolean drawTop) {
        if (drawTop) {
            drawRectLocal(left, top, right, top + 1, COLOR_GRID);
        }
        if (drawLeft) {
            drawRectLocal(left, top, left + 1, bottom, COLOR_GRID);
        }

        drawRectLocal(left, bottom - 1, right, bottom, COLOR_GRID);
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

        if (r.lines.size() >= REM_NEW_MIN_SIZE) {
            return getLineSafe(r.lines, REM_NEW_SETUP_OFFSET + fieldIdx);
        }

        if (r.lines.size() >= REM_PREV_MIN_SIZE) {
            return getLineSafe(r.lines, fieldIdx);
        }

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

    private void drawCell(String text, int x, int yTop, int w, int yBottom) {
        drawCellWrapped(text, x, yTop, w, yBottom, false);
    }

    private void drawCellUnderline(String text, int x, int yTop, int w, int yBottom) {
        drawCellWrapped(text, x, yTop, w, yBottom, true);
    }

    private void drawCellWrapped(String text, int x, int yTop, int w, int yBottom, boolean underlineFirstLine) {
        String safe = text != null ? text : "";
        int innerW = Math.max(0, w - (CELL_PAD_X * 2));
        int innerH = Math.max(0, (yBottom - yTop) - (CELL_PAD_Y * 2));

        if (innerW <= 0 || innerH <= 0) {
            return;
        }

        int lineH = font.FONT_HEIGHT;
        int maxLines = Math.max(1, innerH / lineH);

        List<String> lines = font.listFormattedStringToWidth(safe, innerW);
        if (lines == null || lines.isEmpty()) {
            return;
        }

        int drawLines = Math.min(maxLines, lines.size());
        int startX = x + CELL_PAD_X;
        int startY = yTop + CELL_PAD_Y;

        for (int i = 0; i < drawLines; i++) {
            String ln = lines.get(i);
            if (ln == null) ln = "";

            String out = ln;
            if (underlineFirstLine && i == 0) {
                out = "\u00A7n" + out + "\u00A7r";
            }

            font.drawString(out, startX, startY + (i * lineH), 0xFFFFFF, true);
        }
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