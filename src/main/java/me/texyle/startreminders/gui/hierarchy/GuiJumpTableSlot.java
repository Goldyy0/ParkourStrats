package me.texyle.startreminders.gui.hierarchy;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiSlot;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import me.texyle.startreminders.data.Jump;
import me.texyle.startreminders.data.MapSection;
import me.texyle.startreminders.data.ParkourMap;
import me.texyle.startreminders.gui.GuiDraw;
import me.texyle.startreminders.gui.GuiScissor;
import me.texyle.startreminders.reminders.Reminder;

/**
 * Scrollable table of jumps with one row per strategy. Row model, section lookups and scrollbars live in
 * {@link JumpTableRows}, {@link SectionColumns}, {@link HorizontalScroller} and {@link VerticalScrollbar};
 * this class draws the cells and turns mouse input into selection callbacks.
 */
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

    private final JumpTableRows rows;
    private final HorizontalScroller horizontalScroller = new HorizontalScroller();
    private final VerticalScrollbar verticalScrollbar = new VerticalScrollbar();

    // Optional: needed for section rendering (colors + names)
    private ParkourMap map = null;

    private int selectedIndex = -1;

    private int lastClickedIndex = -1;
    private long lastClickTimeMs = 0L;

    private int lastRawClickIndex = -1;
    private long lastRawClickTimeMs = 0L;

    // Column layout (pixels)
    private static final int PAD_X = 6;

    // Cell padding
    static final int CELL_PAD_X = 5;
    static final int CELL_PAD_Y = 4;

    // Grid color (ARGB)
    private static final int COLOR_GRID = 0x442A2A2A;

    // Zebra row fills (ARGB)
    private static final int COLOR_ROW_DARK = 0x22101010;
    private static final int COLOR_ROW_LIGHT = 0x33333333;

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

    public static final int COL_SECTION_W = 80; // width per section column

    private static final long DOUBLE_CLICK_WINDOW_MS = 320L;
    private static final long DUPLICATE_CLICK_GUARD_MS = 80L;

    // Selection highlight (ARGB)
    private static final int COLOR_ROW_SELECTED = 0x55333333;

    // Optional: subtle fill for Jump column
    private static final int COLOR_JUMP_COL_FILL = 0x22111111;

    // Section cell visuals
    private static final float SECTION_TINT_STRENGTH = 0.72f;
    private static final int SECTION_TINT_BASE_RGB = 0x101010;

    private static final int WHEEL_STEP = 18;

    private int sectionColumns = 0;

    public GuiJumpTableSlot(Minecraft mc, int width, int height, int top, int bottom, int slotHeight,
                            List<Jump> items, ISelectionHandler handler) {
        super(mc, width, height, top, bottom, slotHeight);
        this.mc = mc;
        this.font = mc.fontRendererObj;
        this.items = items;
        this.handler = handler;
        this.rows = new JumpTableRows(items, font);

        // Disable vanilla selection highlight box.
        this.showSelectionBox = false;

        rebuildRows();
    }

    // -------------------------
    // Configuration / state
    // -------------------------

    public void setMap(ParkourMap map) {
        this.map = map;
    }

    public void setSectionColumns(int count) {
        if (count < 0) count = 0;
        if (count > 4) count = 4;
        this.sectionColumns = count;

        rows.relayout();
        clampVerticalScroll();
    }

    public void setPanelBounds(int left, int right) {
        this.left = left;
        this.right = right;

        rows.relayout();
        clampVerticalScroll();
    }

    public void refreshRows() {
        rebuildRows();
    }

    private void rebuildRows() {
        rows.rebuild();

        if (items == null) {
            return;
        }

        if (selectedIndex >= rows.size()) {
            selectedIndex = rows.size() == 0 ? -1 : (rows.size() - 1);
        }

        clampVerticalScroll();
    }

    // -------------------------
    // Selection
    // -------------------------

    public Jump getSelectedItem() {
        JumpTableRows.Row row = rows.get(selectedIndex);
        return row != null ? row.jump : null;
    }

    public int getSelectedJumpIndexInItems() {
        Jump sel = getSelectedItem();
        if (sel == null || items == null) {
            return -1;
        }

        // IMPORTANT: do NOT use items.indexOf(sel) (equals() may match wrong jump).
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i) == sel) {
                return i;
            }
        }

        return -1;
    }

    public void selectJump(Jump jump) {
        if (jump == null || rows.size() == 0) {
            selectedIndex = -1;
            return;
        }

        // IMPORTANT: match by reference only, never by equals().
        selectedIndex = rows.firstRowOf(jump);
    }

    @Override
    protected boolean isSelected(int index) {
        return index == selectedIndex;
    }

    // -------------------------
    // Scroll state (persisted between initGui rebuilds)
    // -------------------------

    public int getVerticalScrollPx() {
        return (int) this.amountScrolled;
    }

    public void setVerticalScrollPx(int px) {
        this.amountScrolled = px;
        clampVerticalScroll();
    }

    public double getHorizontalScrollPx() {
        return horizontalScroller.getOffset();
    }

    public void setHorizontalScrollPx(double px) {
        horizontalScroller.setOffset(px, getVisibleContentWidth(), getTotalContentWidth());
    }

    public void ensureSelectedVisible(int paddingPx) {
        if (paddingPx < 0) paddingPx = 0;

        if (selectedIndex < 0 || selectedIndex >= rows.size()) return;

        int rowTop = rows.topOf(selectedIndex);
        int rowBottom = rowTop + rows.heightOf(selectedIndex);

        int visibleH = (this.bottom - this.top);
        if (visibleH <= 0) return;

        int maxScroll = Math.max(0, getContentHeight() - visibleH);

        int scroll = (int) this.amountScrolled;
        int viewTop = scroll;
        int viewBottom = scroll + visibleH;

        int desiredScroll = scroll;

        // If row is above viewport -> align to top (with padding)
        if (rowTop - paddingPx < viewTop) {
            desiredScroll = rowTop - paddingPx;
        }
        // If row is below viewport -> align to bottom (with padding)
        else if (rowBottom + paddingPx > viewBottom) {
            desiredScroll = rowBottom + paddingPx - visibleH;
        }

        if (desiredScroll < 0) desiredScroll = 0;
        if (desiredScroll > maxScroll) desiredScroll = maxScroll;

        this.amountScrolled = desiredScroll;
        clampVerticalScroll();
    }

    public void centerSelectedRow() {
        if (selectedIndex < 0 || selectedIndex >= rows.size()) return;

        int rowTop = rows.topOf(selectedIndex);
        int rowH = rows.heightOf(selectedIndex);

        int visibleH = (this.bottom - this.top);
        if (visibleH <= 0) return;

        int maxScroll = Math.max(0, getContentHeight() - visibleH);

        int rowCenter = rowTop + (rowH / 2);
        int desiredScroll = rowCenter - (visibleH / 2);

        if (desiredScroll < 0) desiredScroll = 0;
        if (desiredScroll > maxScroll) desiredScroll = maxScroll;

        this.amountScrolled = desiredScroll;
        clampVerticalScroll();
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

    // -------------------------
    // Geometry
    // -------------------------

    public int getHeaderStartX() {
        return left + PAD_X - (int) Math.round(horizontalScroller.getOffset());
    }

    public int getVisibleContentWidth() {
        return Math.max(0, (right - left) - (PAD_X * 2));
    }

    public int getTotalContentWidth() {
        int sectionsW = sectionColumns * COL_SECTION_W;

        return (COL_ROW_W + sectionsW + COL_JUMP_W + COL_POS_W + COL_FACING_W + COL_SETUP_W + COL_STRAT_W
                + COL_STRAFE_W + COL_TURN_W + COL_AUTHOR_W + COL_TIPS_W) + (PAD_X * 2);
    }

    @Override
    public int getListWidth() {
        int w = (this.right - this.left) - 10;
        return Math.max(0, w);
    }

    @Override
    protected int getScrollBarX() {
        int barRight = this.right - 2;
        return barRight - VerticalScrollbar.WIDTH;
    }

    @Override
    protected int getSize() {
        return rows.size();
    }

    @Override
    protected int getContentHeight() {
        return rows.getContentHeight();
    }

    // -------------------------
    // Drawing
    // -------------------------

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        enableScissorForSlotViewport();
        try {
            drawVariableHeightList();
        } finally {
            disableScissor();
        }

        coverVanillaVerticalScrollbar();
        verticalScrollbar.draw(this.top, this.bottom, this.right, this.amountScrolled, getContentHeight());
        this.amountScrolled = verticalScrollbar.updateDrag(mouseX, mouseY, this.amountScrolled);
        clampVerticalScroll();
    }

    private void drawVariableHeightList() {
        clampVerticalScroll();

        int scroll = (int) this.amountScrolled;

        int first = rows.findFirstVisibleRow(scroll);
        if (first < 0) {
            return;
        }

        int y = this.top - scroll + rows.topOf(first);

        for (int i = first; i < rows.size(); i++) {
            int h = rows.heightOf(i);
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
        // Zebra background first (row background only)
        int zebra = (entryID % 2 == 0) ? COLOR_ROW_DARK : COLOR_ROW_LIGHT;
        GuiDraw.fillRect(this.left, rowTop, this.right, rowBottom, zebra);

        // Selection overlay (row background only)
        if (entryID == selectedIndex) {
            GuiDraw.fillRect(this.left, rowTop, this.right, rowBottom, COLOR_ROW_SELECTED);
        }

        JumpTableRows.Row row = rows.get(entryID);
        if (row == null || row.jump == null) {
            return;
        }

        Reminder r = row.reminder;

        int x = getHeaderStartX();

        // Row number
        drawCellWithGrid(String.valueOf(entryID + 1), x, rowTop, COL_ROW_W, rowBottom, true);
        x += COL_ROW_W;

        // Section columns: color ONLY these cells, not full rows.
        boolean isFirstRowOfJump = (row.groupRowIndex == 0);
        for (int col = 0; col < sectionColumns; col++) {
            drawSectionCell(col, row, isFirstRowOfJump, x, rowTop, COL_SECTION_W, rowBottom, false);
            x += COL_SECTION_W;
        }

        // Jump column
        drawJumpOrdinalCellWithGrid(row, x, rowTop, COL_JUMP_W, rowBottom, false);
        x += COL_JUMP_W;

        drawCellWithGrid(ReminderColumns.position(r), x, rowTop, COL_POS_W, rowBottom, false);
        x += COL_POS_W;

        drawCellWithGrid(ReminderColumns.facing(r), x, rowTop, COL_FACING_W, rowBottom, false);
        x += COL_FACING_W;

        drawCellWithGrid(ReminderColumns.field(r, 0), x, rowTop, COL_SETUP_W, rowBottom, false);
        x += COL_SETUP_W;

        drawCellWithGrid(ReminderColumns.field(r, 1), x, rowTop, COL_STRAT_W, rowBottom, false);
        x += COL_STRAT_W;

        drawCellWithGrid(ReminderColumns.field(r, 2), x, rowTop, COL_STRAFE_W, rowBottom, false);
        x += COL_STRAFE_W;

        drawCellWithGrid(ReminderColumns.field(r, 3), x, rowTop, COL_TURN_W, rowBottom, false);
        x += COL_TURN_W;

        drawCellWithGrid(ReminderColumns.field(r, 4), x, rowTop, COL_AUTHOR_W, rowBottom, false);
        x += COL_AUTHOR_W;

        drawCellWithGrid(ReminderColumns.field(r, 5), x, rowTop, COL_TIPS_W, rowBottom, false);
    }

    /**
     * Section cell behavior:
     * - Only the SECTION cell is colored (not the entire row).
     * - The cell color is the section color if a section exists for (col, jumpIndex).
     * - If missing for this column, it inherits the color from the nearest LEFT column that has a section at the same jumpIndex.
     *   (III <- II <- I)
     * - Section name (bold) is drawn only on the FIRST row of the jump group (first strategy row),
     *   and only if this column has a real section that starts at this jumpIndex.
     * - No end-of-section markers are drawn.
     */
    private void drawSectionCell(int col, JumpTableRows.Row row, boolean isFirstRowOfJump,
                                 int x, int yTop, int w, int yBottom, boolean isFirstCol) {
        int jumpIndex = rows.jumpIndexOf(row.jump);

        // "Direct" section for this column at this jumpIndex (may end as null)
        MapSection direct = SectionColumns.sectionAt(map, col, jumpIndex);

        MapSection colorSource = direct;
        if (colorSource == null) {
            // inherit from nearest left column
            for (int c = col - 1; c >= 0; c--) {
                MapSection sLeft = SectionColumns.sectionAt(map, c, jumpIndex);
                if (sLeft != null) {
                    colorSource = sLeft;
                    break;
                }
            }
        }

        if (colorSource != null) {
            int fill = SectionColumns.tintOpaque(colorSource.getColorArgb(), SECTION_TINT_BASE_RGB, SECTION_TINT_STRENGTH);
            GuiDraw.fillRect(x, yTop, x + w, yBottom, fill);
        }

        // Draw start label only if THIS column has a real section, and it's the start row,
        // and ONLY on the first row of the jump group (first strategy row).
        if (isFirstRowOfJump && direct != null && jumpIndex == direct.getStartJumpIndex()) {
            String name = direct.getName() != null ? direct.getName() : "";
            if (!name.trim().isEmpty()) {
                // Use per-section text color (ARGB -> RGB)
                int rgb = direct.getTextColorArgb() & 0xFFFFFF;
                drawCellWrapped("\u00A7l" + name + "\u00A7r", x, yTop, w, yBottom, rgb, false);
            }
        }

        drawCellBorderThin(x, yTop, x + w, yBottom, isFirstCol, false);
    }

    private void drawJumpOrdinalCellWithGrid(JumpTableRows.Row row, int x, int yTop, int w, int yBottom, boolean isFirstCol) {
        GuiDraw.fillRect(x, yTop, x + w, yBottom, COLOR_JUMP_COL_FILL);

        drawCellWrapped(JumpTableRows.jumpCellText(row), x, yTop, w, yBottom, 0xFFFFFF, true);

        drawCellBorderThin(x, yTop, x + w, yBottom, isFirstCol, false);
    }

    private void drawCellWithGrid(String text, int x, int yTop, int w, int yBottom, boolean isFirstCol) {
        drawCellWrapped(text, x, yTop, w, yBottom, 0xFFFFFF, false);
        drawCellBorderThin(x, yTop, x + w, yBottom, isFirstCol, false);
    }

    private void drawCellBorderThin(int left, int top, int right, int bottom, boolean drawLeft, boolean drawTop) {
        if (drawTop) {
            GuiDraw.fillRect(left, top, right, top + 1, COLOR_GRID);
        }
        if (drawLeft) {
            GuiDraw.fillRect(left, top, left + 1, bottom, COLOR_GRID);
        }

        GuiDraw.fillRect(left, bottom - 1, right, bottom, COLOR_GRID);
        GuiDraw.fillRect(right - 1, top, right, bottom, COLOR_GRID);
    }

    /** Draws word-wrapped text clipped to the cell; optionally underlines the first line. */
    private void drawCellWrapped(String text, int x, int yTop, int w, int yBottom, int rgb, boolean underlineFirstLine) {
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

            font.drawString(out, startX, startY + (i * lineH), rgb, true);
        }
    }

    public void drawVerticalSeparators(int yTop, int yBottom) {
        int x = getHeaderStartX();

        x += COL_ROW_W;
        GuiDraw.vLine(x, yTop, yBottom, COLOR_GRID);

        for (int i = 0; i < sectionColumns; i++) {
            x += COL_SECTION_W;
            GuiDraw.vLine(x, yTop, yBottom, COLOR_GRID);
        }

        x += COL_JUMP_W;   GuiDraw.vLine(x, yTop, yBottom, COLOR_GRID);
        x += COL_POS_W;    GuiDraw.vLine(x, yTop, yBottom, COLOR_GRID);
        x += COL_FACING_W; GuiDraw.vLine(x, yTop, yBottom, COLOR_GRID);
        x += COL_SETUP_W;  GuiDraw.vLine(x, yTop, yBottom, COLOR_GRID);
        x += COL_STRAT_W;  GuiDraw.vLine(x, yTop, yBottom, COLOR_GRID);
        x += COL_STRAFE_W; GuiDraw.vLine(x, yTop, yBottom, COLOR_GRID);
        x += COL_TURN_W;   GuiDraw.vLine(x, yTop, yBottom, COLOR_GRID);
        x += COL_AUTHOR_W; GuiDraw.vLine(x, yTop, yBottom, COLOR_GRID);
    }

    public void drawHorizontalScrollbar(int yTop, int yBottom) {
        horizontalScroller.draw(left + PAD_X, right - PAD_X, yTop, yBottom,
                getVisibleContentWidth(), getTotalContentWidth());
    }

    public void updateHorizontalScrollbarDrag(int mouseX, int mouseY) {
        horizontalScroller.updateDrag(mouseX, mouseY);
    }

    /** Hides the vanilla vertical scrollbar, which the custom one replaces. */
    private void coverVanillaVerticalScrollbar() {
        int visibleH = (this.bottom - this.top);
        int maxScroll = Math.max(0, getContentHeight() - visibleH);

        if (maxScroll <= 0) {
            return;
        }

        int barLeft = getScrollBarX();
        int barRight = barLeft + VerticalScrollbar.WIDTH;

        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GuiDraw.fillRect(barLeft, this.top, barRight, this.bottom, 0xFF101010);
    }

    private void enableScissorForSlotViewport() {
        GuiScissor.enable(this.mc, this.left, this.top, this.right - this.left, this.bottom - this.top);
    }

    private void disableScissor() {
        GuiScissor.disable();
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

    @Override
    protected void drawSlot(int entryID, int insideLeft, int yPos, int insideSlotHeight, int mouseXIn, int mouseYIn) { }

    // -------------------------
    // Input
    // -------------------------

    @Override
    public void handleMouseInput() {
        int dWheel = Mouse.getEventDWheel();

        if (!isShiftDown() && dWheel != 0) {
            if (dWheel > 0) {
                this.amountScrolled -= WHEEL_STEP;
            } else {
                this.amountScrolled += WHEEL_STEP;
            }
            clampVerticalScroll();
        }

        if (Mouse.getEventButton() == 0 && Mouse.getEventButtonState()) {
            int mouseX = getMouseXScaled();
            int mouseY = getMouseYScaled();

            if (mouseY >= this.top && mouseY <= this.bottom) {
                if (verticalScrollbar.isMouseOver(mouseX, mouseY)) {
                    return;
                }

                int idx = getRowIndexAt(mouseY);
                if (idx >= 0 && idx < getSize()) {
                    elementClicked(idx, false, mouseX, mouseY);
                }
            }
        }
    }

    /** Shift + wheel scrolls horizontally. */
    public void handleHorizontalScrollWheel() {
        int dWheel = Mouse.getDWheel();
        if (dWheel == 0) {
            return;
        }

        if (!isShiftDown()) {
            return;
        }

        horizontalScroller.scrollByWheel(dWheel, getVisibleContentWidth(), getTotalContentWidth());
    }

    @Override
    protected void elementClicked(int index, boolean ignoredDoubleClickFlag, int mouseX, int mouseY) {
        if (horizontalScroller.isMouseOver(mouseX, mouseY) || verticalScrollbar.isMouseOver(mouseX, mouseY)) {
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

        if (jumpColumnClicked && handler != null) {
            handler.onJumpNameClick(index);

            if (isShiftDown()) {
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

    private int getRowIndexAt(int mouseY) {
        if (mouseY < this.top || mouseY > this.bottom) {
            return -1;
        }

        int yInContent = (int) this.amountScrolled + (mouseY - this.top);
        return rows.rowIndexAtContentY(yInContent);
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
        int jumpLeft = baseX + COL_ROW_W + (sectionColumns * COL_SECTION_W);
        int jumpRight = jumpLeft + COL_JUMP_W;

        return mouseX >= jumpLeft && mouseX <= jumpRight;
    }

    private static boolean isShiftDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
    }

    private int getMouseXScaled() {
        return Mouse.getEventX() * this.width / this.mc.displayWidth;
    }

    private int getMouseYScaled() {
        return this.height - (Mouse.getEventY() * this.height / this.mc.displayHeight) - 1;
    }
}
