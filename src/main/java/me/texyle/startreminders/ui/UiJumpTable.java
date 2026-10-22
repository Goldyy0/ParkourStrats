package me.texyle.startreminders.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import me.texyle.startreminders.data.Jump;
import me.texyle.startreminders.template.TemplateMode;
import me.texyle.startreminders.data.MapSection;
import me.texyle.startreminders.data.ParkourMap;
import me.texyle.startreminders.gui.GuiScissor;
import me.texyle.startreminders.reminders.Reminder;
import me.texyle.startreminders.reminders.UserPreferences;

/**
 * Table of the jumps of a map, one row per strategy, grouped by jump.
 * <ul>
 * <li>Rows of one jump form a group: it is selected, hovered and separated as a whole, and the jump's name
 * and sections appear once, on its first row.</li>
 * <li>The columns stretch to fill the width; when they do not fit, the number, section and jump columns stay
 * in place while the rest scrolls horizontally.</li>
 * <li>The strategy shown in the game is marked in jumps that have several.</li>
 * <li>Rows have a fixed height: text that does not fit is cut off with "..." and shown in full in a tooltip
 * on hover. Only a jump's name may wrap, over the height of all its strategy rows.</li>
 * </ul>
 */
public class UiJumpTable extends UiWidget {

    private static final char SECTION_SIGN = (char) 0xA7;

    private static final int HEADER_H = 20;
    /** Room the scrollbars take next to / below the table frame (6px bar plus a 2px gap). */
    private static final int BAR = 8;
    private static final int PAD_X = 5;
    private static final int PAD_Y = 4;
    private static final int ROW_H = 22;
    private static final int TOOLTIP_MAX_W = 280;

    private static final float MIN_ZOOM = 0.1f;
    private static final float MAX_ZOOM = 1.0f;
    private static final float ZOOM_STEP = 1.2f;
    private static final long ZOOM_LABEL_MS = 1200L;
    private static final int WHEEL_STEP = 24;
    private static final long DOUBLE_CLICK_MS = 320L;

    private static final int NUM_W = 38;
    /** Extra room in the number column for the checkbox of the sheet review. */
    private static final int CHECK_W = 16;
    private static final int COLOR_EXCLUDED = 0xB0121118;
    private static final int SECTION_W = 80;
    private static final int COMPACT_SECTION_W = 16;
    private static final int JUMP_W = 124;
    private static final int COUNT_LABEL_W = 38; // "2/3" plus the marker in front of it

    /** Sections may shrink to this when needed to keep the left columns frozen. */
    private static final int MIN_SECTION_W = 44;
    /** The frozen left part may take at most this share of the width. */
    private static final double MAX_FROZEN_SHARE = 0.6;

    private static final String[] TEXT_TITLES = { "Position", "Facing", "Setup", "Strategy", "Strafe", "Turn", "Author", "Tips" };
    private static final int[] TEXT_BASE_W = { 110, 80, 100, 150, 80, 60, 90, 130 };
    private static final int[] TEXT_FLEX = { 1, 0, 2, 3, 0, 0, 1, 2 };
    private static final int FLEX_TOTAL = 9;

    private static final int COLOR_ZEBRA = 0x0CFFFFFF;
    private static final int COLOR_HOVER = 0x16FFFFFF;
    private static final int COLOR_SELECTED = 0x426D3FD0;
    private static final int COLOR_GROUP_LINE = 0xFF3A3648;
    private static final int COLOR_ROW_LINE = 0x14FFFFFF;
    private static final int COLOR_COLUMN_LINE = 0x14FFFFFF;
    private static final int COLOR_EMPTY_CELL = 0xFF4A4658;

    private enum Kind { NUMBER, SECTION, JUMP, TEXT }

    private static final class Col {
        Kind kind;
        int index;      // section level (0-based) or text column
        int width;
        boolean frozen;
        int regionX;    // x within its region (frozen or scrolling)
    }

    private static final class Row {
        Jump jump;
        Reminder reminder;      // null if the jump has no strategy
        int groupRowIndex;
        int groupSize;
        int groupIndex;
        int jumpIndex;          // index in the map's jump list (sections refer to it)
        int top;
        int height;
        int groupTop;           // top of the group's first row
        int groupHeight;        // total height of the group's rows
        boolean firstOfGroup() { return groupRowIndex == 0; }
        boolean lastOfGroup() { return groupRowIndex == groupSize - 1; }
    }

    private ParkourMap map;
    /** This map is in template mode: jumps without a position get a red bar. */
    private boolean templateRows;
    private String filter = "";
    private String emptyText = "";
    private Consumer<Jump> onActivate;

    /** Called when a dragged jump is dropped: put {@code moved} before (or after) {@code anchor}. */
    public interface MoveHandler {
        void onMove(Jump moved, Jump anchor, boolean after);
    }

    /**
     * The review of a sheet before it is imported: every jump has a checkbox, and jumps outside the chosen first and
     * last jump (or unchecked) are dimmed.
     */
    public interface Review {
        /** Checked and inside the first..last range: will be imported. */
        boolean isIncluded(Jump jump);

        boolean isChecked(Jump jump);

        boolean isCropStart(Jump jump);

        boolean isCropEnd(Jump jump);

        void toggle(Jump jump);
    }

    private Review review;

    private MoveHandler onMove;
    private boolean moveMode;
    private Jump dragJump;          // pressed jump in move mode (a drag once the mouse moved enough)
    private boolean dragging;
    private int dragStartY;
    private int dropSlot = -1;      // 0..visible jumps: the gap the jump would be dropped into

    private Jump selectedJump;
    private float scroll = 0f;

    private final ArrayList<Row> rows = new ArrayList<Row>();
    private final ArrayList<Col> cols = new ArrayList<Col>();
    private final Map<String, List<String>> wrapCache = new HashMap<String, List<String>>();

    private boolean dataDirty = true;
    private boolean layoutDirty = true;

    private int sectionCount;
    private int sectionW = SECTION_W;
    private boolean compactSections;
    private boolean needV;
    private boolean needH;
    private boolean frozenEnabled;
    private int frozenW;
    // Layout is done in "content units"; the body is drawn scaled by the zoom, so on screen it is
    // viewW * zoom wide (viewWs) and bodyH * zoom tall (bodyHs).
    private int viewW;
    private int bodyH;
    private int viewWs;
    private int bodyHs;
    private int scrollViewW;
    private int scrollColsW;
    private int contentH;

    private final HorizontalScroller hScroller = new HorizontalScroller();
    private final VerticalScrollbar vScroller = new VerticalScrollbar();

    /** Zoom level; every new table (a new hub window) starts at 100 %. */
    private float zoom = 1f;
    private long zoomLabelUntil = 0L;

    private int hoverRow = -1;
    private int lastMouseX = -9999;
    private int lastMouseY = -9999;
    private UiPopup popup; // for the section menus in the header
    private int lastClickedGroup = -1;
    private long lastClickMs = 0L;

    // -------------------------
    // Configuration
    // -------------------------

    public void setMap(ParkourMap map) {
        this.map = map;
        refresh();
    }

    /** Re-reads the jumps of the map (after any change to them). */
    public void refresh() {
        dataDirty = true;
        layoutDirty = true;
    }

    /** Shows only jumps whose name or strategy text contains the query (empty = all). */
    public void setFilter(String query) {
        String q = query != null ? query.trim().toLowerCase() : "";
        if (!q.equals(filter)) {
            filter = q;
            refresh();
            scroll = 0f;
        }
    }

    public void setEmptyText(String text) {
        this.emptyText = text != null ? text : "";
    }

    /** The window's popup, used for the section menus ("fast travel") in the header. */
    public void setPopup(UiPopup popup) {
        this.popup = popup;
    }

    public void setReview(Review review) {
        this.review = review;
        refresh();
    }

    private int numW() {
        return NUM_W + (review != null ? CHECK_W : 0);
    }

    public void setOnMove(MoveHandler handler) {
        this.onMove = handler;
    }

    public boolean isMoveMode() {
        return moveMode;
    }

    public void setMoveMode(boolean on) {
        moveMode = on;
        dragJump = null;
        dragging = false;
        dropSlot = -1;
    }

    public void setOnActivate(Consumer<Jump> handler) {
        this.onActivate = handler;
    }

    public void resetScroll() {
        scroll = 0f;
        hScroller.setOffset(0, 1, 1);
    }

    @Override
    public void setBounds(int x, int y, int w, int h) {
        super.setBounds(x, y, w, h);
        layoutDirty = true;
    }

    // -------------------------
    // Selection
    // -------------------------

    public Jump getSelectedJump() {
        return selectedJump;
    }

    public void selectJump(Jump jump) {
        selectedJump = jump;
    }

    /** Moves the selection by whole jumps; selects the first (delta > 0) or last jump if nothing is selected. */
    public void selectRelative(int delta) {
        ensureLayout();
        List<Jump> order = visibleJumps();
        if (order.isEmpty()) return;

        int current = -1;
        for (int i = 0; i < order.size(); i++) {
            if (order.get(i) == selectedJump) current = i;
        }

        int target = (current < 0) ? (delta > 0 ? 0 : order.size() - 1) : current + delta;
        target = Math.max(0, Math.min(order.size() - 1, target));
        selectedJump = order.get(target);
        ensureSelectedVisible();
    }

    public void selectEdge(boolean last) {
        ensureLayout();
        List<Jump> order = visibleJumps();
        if (order.isEmpty()) return;
        selectedJump = last ? order.get(order.size() - 1) : order.get(0);
        ensureSelectedVisible();
    }

    private List<Jump> visibleJumps() {
        List<Jump> order = new ArrayList<Jump>();
        Jump last = null;
        for (Row r : rows) {
            if (r.jump != last) {
                order.add(r.jump);
                last = r.jump;
            }
        }
        return order;
    }

    public void ensureSelectedVisible() {
        ensureLayout();
        if (selectedJump == null) return;

        int top = -1;
        int bottom = -1;
        for (Row r : rows) {
            if (r.jump == selectedJump) {
                if (top < 0) top = r.top;
                bottom = r.top + r.height;
            }
        }
        if (top < 0) return;

        if (top < scroll) scroll = top;
        if (bottom > scroll + bodyH) scroll = bottom - bodyH;
        clampScroll();
    }

    /** Handles navigation keys; call only while the table owns the keyboard. @return true if used */
    public boolean keyTyped(int keyCode) {
        switch (keyCode) {
            case Keyboard.KEY_UP:
                selectRelative(-1);
                return true;
            case Keyboard.KEY_DOWN:
                selectRelative(1);
                return true;
            case Keyboard.KEY_PRIOR:
                selectRelative(-5);
                return true;
            case Keyboard.KEY_NEXT:
                selectRelative(5);
                return true;
            case Keyboard.KEY_HOME:
                selectEdge(false);
                return true;
            case Keyboard.KEY_END:
                selectEdge(true);
                return true;
            case Keyboard.KEY_RETURN:
            case Keyboard.KEY_NUMPADENTER:
                if (selectedJump != null && onActivate != null) {
                    onActivate.accept(selectedJump);
                    return true;
                }
                return false;
            default:
                return false;
        }
    }

    // -------------------------
    // Model and layout
    // -------------------------

    private FontRenderer font() {
        return UiDraw.font();
    }

    private int lineHeight() {
        return font().FONT_HEIGHT + 1;
    }

    private void ensureLayout() {
        if (!dataDirty && !layoutDirty) return;

        if (dataDirty) {
            rebuildRows();
            dataDirty = false;
        }
        computeLayout();
        layoutDirty = false;
    }

    private boolean matches(Jump jump) {
        if (filter.isEmpty()) return true;

        // Only the jump's name; the strategy texts are not searched.
        String id = jump.getId();
        return id != null && id.toLowerCase().contains(filter);
    }

    private void rebuildRows() {
        rows.clear();
        if (map == null || map.getJumps() == null) return;

        List<Jump> jumps = map.getJumps();
        int groupIndex = 0;
        for (int i = 0; i < jumps.size(); i++) {
            Jump j = jumps.get(i);
            if (j == null || !matches(j)) continue;

            List<Reminder> reminders = j.getReminders();
            int size = (reminders == null || reminders.isEmpty()) ? 1 : reminders.size();

            for (int k = 0; k < size; k++) {
                Row r = new Row();
                r.jump = j;
                r.reminder = (reminders == null || reminders.isEmpty()) ? null : reminders.get(k);
                r.groupRowIndex = k;
                r.groupSize = size;
                r.groupIndex = groupIndex;
                r.jumpIndex = i;
                rows.add(r);
            }
            groupIndex++;
        }
    }

    private void computeLayout() {
        wrapCache.clear();

        sectionCount = map != null ? Math.max(0, Math.min(ParkourMap.MAX_SECTION_LEVELS, map.getEffectiveSectionsCount())) : 0;
        compactSections = UserPreferences.isSectionsCompact();

        needV = false;
        for (int pass = 0; pass < 3; pass++) {
            viewWs = frameW() - 2;
            viewW = (int) Math.ceil(viewWs / zoom);
            buildColumns();
            bodyHs = frameH() - 2 - HEADER_H;
            bodyH = (int) Math.ceil(bodyHs / zoom);
            computeRowHeights();

            boolean v = contentH > bodyH;
            if (v == needV) break;
            needV = v;
        }

        clampScroll();
        hScroller.setOffset(hScroller.getOffset(), scrollViewW, scrollColsW);
    }

    private void buildColumns() {
        cols.clear();

        int baseSectionW = compactSections ? COMPACT_SECTION_W : SECTION_W;
        sectionW = baseSectionW;
        int leftNatural = numW() + sectionCount * baseSectionW + JUMP_W;
        int textNatural = 0;
        for (int w0 : TEXT_BASE_W) textNatural += w0;
        int natural = leftNatural + textNatural;

        int[] textW = TEXT_BASE_W.clone();
        if (natural <= viewW) {
            // Everything fits: stretch the flexible columns so the table fills the width.
            int extra = viewW - natural;
            int given = 0;
            for (int i = 0; i < textW.length; i++) {
                int share = extra * TEXT_FLEX[i] / FLEX_TOTAL;
                textW[i] += share;
                given += share;
            }
            textW[3] += extra - given;
            needH = false;
            frozenEnabled = false;
        } else {
            needH = true;

            // Keep the left part frozen even on maps with many section levels by narrowing the section columns.
            int budget = (int) (viewW * MAX_FROZEN_SHARE) - numW() - JUMP_W;
            if (sectionCount == 0) {
                frozenEnabled = (numW() + JUMP_W) <= viewW * MAX_FROZEN_SHARE;
            } else {
                int fitted = Math.min(baseSectionW, budget / sectionCount);
                // Compact stripes are already as narrow as they get; full columns may shrink a little.
                frozenEnabled = fitted >= (compactSections ? baseSectionW : MIN_SECTION_W);
                if (frozenEnabled) sectionW = fitted;
            }
        }

        add(Kind.NUMBER, 0, numW());
        for (int i = 0; i < sectionCount; i++) add(Kind.SECTION, i, sectionW);
        add(Kind.JUMP, 0, JUMP_W);
        for (int i = 0; i < textW.length; i++) add(Kind.TEXT, i, textW[i]);

        int frozenX = 0;
        int scrollX = 0;
        scrollColsW = 0;
        frozenW = 0;
        for (Col c : cols) {
            boolean isLeft = c.kind != Kind.TEXT;
            c.frozen = frozenEnabled && isLeft;
            if (c.frozen) {
                c.regionX = frozenX;
                frozenX += c.width;
            } else {
                c.regionX = scrollX;
                scrollX += c.width;
            }
        }
        frozenW = frozenX;
        scrollColsW = scrollX;
        scrollViewW = viewW - frozenW;
    }

    private void add(Kind kind, int index, int width) {
        Col c = new Col();
        c.kind = kind;
        c.index = index;
        c.width = width;
        cols.add(c);
    }

    private void computeRowHeights() {
        int top = 0;
        int groupTop = 0;

        for (Row r : rows) {
            if (r.firstOfGroup()) groupTop = top;

            r.top = top;
            r.height = ROW_H;
            r.groupTop = groupTop;
            r.groupHeight = r.groupSize * ROW_H;
            top += ROW_H;
        }
        contentH = top;
    }

    private static String jumpName(Jump j) {
        return (j.getId() != null) ? j.getId() : "<unnamed>";
    }

    /** Text of a table column of the TEXT kind: 0 = Position, 1 = Facing, 2..7 = Setup..Tips. */
    private static String textOf(Row r, int textColumn) {
        if (textColumn == 0) return ReminderColumns.position(r.reminder);
        if (textColumn == 1) return ReminderColumns.facing(r.reminder);
        return ReminderColumns.field(r.reminder, textColumn - 2);
    }

    private List<String> wrap(String text, int columnWidth) {
        String safe = text != null ? text : "";
        int inner = Math.max(1, columnWidth - PAD_X * 2);
        if (safe.trim().isEmpty()) return java.util.Collections.singletonList("");

        String key = inner + ":" + safe;
        List<String> cached = wrapCache.get(key);
        if (cached != null) return cached;

        List<String> lines = font().listFormattedStringToWidth(safe, inner);
        if (lines == null || lines.isEmpty()) lines = java.util.Collections.singletonList("");
        wrapCache.put(key, lines);
        return lines;
    }

    /** Cuts the text to the width, ending in "..." when something was removed. */
    private String ellipsize(String text, int width) {
        if (text == null) return "";
        if (UiDraw.width(text) <= width) return text;

        int dots = UiDraw.width("...");
        return font().trimStringToWidth(text, Math.max(0, width - dots)) + "...";
    }

    private boolean fits(String text, int columnWidth) {
        return UiDraw.width(text) <= columnWidth - PAD_X * 2;
    }

    private void clampScroll() {
        scroll = Math.max(0f, Math.min(scroll, Math.max(0, contentH - bodyH)));
    }

    // -------------------------
    // Geometry helpers
    // -------------------------

    /** Width of the table frame; with a vertical scrollbar its band is left free on the right. */
    private int frameW() {
        return w - (needV ? BAR : 0);
    }

    /** Height of the table frame; with a horizontal scrollbar its band is left free at the bottom. */
    private int frameH() {
        return h - (needH ? BAR : 0);
    }

    private int bodyLeft() {
        return x + 1;
    }

    private int bodyTop() {
        return y + 1 + HEADER_H;
    }

    /** Scaled size: content units to screen pixels. */
    private int sw(int contentSize) {
        return Math.round(contentSize * zoom);
    }

    /** Position of a column's left edge in content units, relative to the left of the body. */
    private double contentX(Col c) {
        if (c.frozen) return c.regionX;
        return frozenW + c.regionX - hScroller.getOffset();
    }

    /** Left edge of a column in the (unscaled) space the body is drawn in. */
    private int colUnscaledX(Col c) {
        return bodyLeft() + (int) Math.round(contentX(c));
    }

    /** Left edge of a column on screen. */
    private int colScreenX(Col c) {
        return bodyLeft() + (int) Math.round(contentX(c) * zoom);
    }

    private int rowAt(int mouseX, int mouseY) {
        if (mouseX < bodyLeft() || mouseX >= bodyLeft() + viewWs) return -1;
        if (mouseY < bodyTop() || mouseY >= bodyTop() + bodyHs) return -1;

        int contentY = (int) (scroll + (mouseY - bodyTop()) / zoom);
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            if (contentY >= r.top && contentY < r.top + r.height) return i;
        }
        return -1;
    }

    private boolean overScrollbars(int mouseX, int mouseY) {
        return (needV && mouseX >= x + frameW()) || (needH && mouseY >= y + frameH());
    }

    // -------------------------
    // Drawing
    // -------------------------

    @Override
    public void draw(int mouseX, int mouseY) {
        if (!visible) return;
        ensureLayout();
        clampScroll();
        templateRows = map != null && TemplateMode.map() == map;

        UiDraw.box(x, y, frameW(), frameH(), UiTheme.CARD_BG, UiTheme.CARD_BORDER);

        lastMouseX = mouseX;
        lastMouseY = mouseY;
        hoverRow = overScrollbars(mouseX, mouseY) ? -1 : rowAt(mouseX, mouseY);

        drawHeader();

        if (rows.isEmpty()) {
            if (!emptyText.isEmpty()) {
                UiDraw.centered(emptyText, x + w / 2, bodyTop() + bodyHs / 2 - 4, UiTheme.TEXT_MUTED);
            }
        } else {
            drawBody();
        }

        updateDrag(mouseX, mouseY);
        drawScrollbars(mouseX, mouseY);
        drawDragOverlay(mouseX, mouseY);
        drawZoomLabel();
        if (!dragging) drawTooltip(mouseX, mouseY);
    }

    private void drawHeader() {
        int left = bodyLeft();
        int top = y + 1;
        int frozenWs = sw(frozenW);
        Minecraft mc = Minecraft.getMinecraft();

        UiDraw.fill(left, top, viewWs, HEADER_H, UiTheme.TABLE_HEADER_BG);
        UiDraw.fill(left, top + HEADER_H - 1, viewWs, 1, UiTheme.CARD_BORDER);

        // Scrolling columns first; frozen ones are drawn on top so scrolled text never shows through them.
        GuiScissor.enable(mc, left + frozenWs, top, viewWs - frozenWs, HEADER_H);
        for (Col c : cols) {
            if (!c.frozen) drawHeaderCell(c, top);
        }
        GuiScissor.disable();

        if (frozenW > 0) {
            UiDraw.fill(left, top, frozenWs, HEADER_H - 1, UiTheme.TABLE_HEADER_BG);
            GuiScissor.enable(mc, left, top, frozenWs, HEADER_H);
            for (Col c : cols) {
                if (c.frozen) drawHeaderCell(c, top);
            }
            GuiScissor.disable();
        }
    }

    private void drawHeaderCell(Col c, int top) {
        String title;
        switch (c.kind) {
            case NUMBER:
                title = "#";
                break;
            case SECTION:
                title = map != null && map.getSectionName(c.index) != null ? map.getSectionName(c.index) : "";
                break;
            case JUMP:
                title = "Jump";
                break;
            default:
                title = TEXT_TITLES[c.index];
        }

        int cx = colScreenX(c);
        int cw = sw(c.width);

        if (c.kind == Kind.SECTION) {
            // A small arrow opens the list of this level's sections; compact stripes have no room for a title.
            boolean hover = popup != null && UiDraw.inside(lastMouseX, lastMouseY, cx, top, cw, HEADER_H - 1);
            int arrowColor = hover ? UiTheme.TEXT : UiTheme.TEXT_MUTED;
            if (hover) UiDraw.fill(cx, top, cw, HEADER_H - 1, UiTheme.CARD_BORDER);
            int ax = compactSections ? cx + (cw - 1 - 5) / 2 : cx + cw - 9;
            int ay = top + (HEADER_H - 3) / 2;
            UiDraw.fill(ax, ay, 5, 1, arrowColor);
            UiDraw.fill(ax + 1, ay + 1, 3, 1, arrowColor);
            UiDraw.fill(ax + 2, ay + 2, 1, 1, arrowColor);
            if (!compactSections) {
                UiDraw.text(UiDraw.fit(title.toUpperCase(), cw - PAD_X - 12), cx + PAD_X, top + (HEADER_H - 8) / 2, UiTheme.TEXT_MUTED);
            }
        } else {
            UiDraw.text(UiDraw.fit(title.toUpperCase(), cw - PAD_X * 2), cx + PAD_X, top + (HEADER_H - 8) / 2, UiTheme.TEXT_MUTED);
        }
        if (!(c.kind == Kind.SECTION && compactSections)) {
            UiDraw.fill(cx + cw - 1, top + 4, 1, HEADER_H - 9, COLOR_COLUMN_LINE);
        }
    }

    private void drawBody() {
        int left = bodyLeft();
        int top = bodyTop();
        int first = findFirstVisibleRow();
        int end = first;
        while (end < rows.size() && top + rows.get(end).top - (int) scroll < top + bodyH) end++;

        Minecraft mc = Minecraft.getMinecraft();
        int frozenWs = sw(frozenW);

        // Everything below is laid out in content units and scaled around the top-left corner of the body.
        // (Scissor rectangles are in screen pixels and are not affected by the scale.)
        GlStateManager.pushMatrix();
        GlStateManager.translate(left, top, 0f);
        GlStateManager.scale(zoom, zoom, 1f);
        GlStateManager.translate(-left, -top, 0f);

        // Pass 1: the scrolling region.
        GuiScissor.enable(mc, left + frozenWs, top, viewWs - frozenWs, bodyHs);
        for (int i = first; i < end; i++) {
            Row r = rows.get(i);
            drawRowBackground(r, top + r.top - (int) scroll, left + frozenW, viewW - frozenW, false);
        }
        for (int i = first; i < end; i++) {
            Row r = rows.get(i);
            for (Col c : cols) {
                if (!c.frozen) drawCell(r, c, colUnscaledX(c), top + r.top - (int) scroll, i == first);
            }
        }
        GuiScissor.disable();

        // Pass 2: the frozen region, over the scrolled one. All backgrounds first, because a jump's name
        // may run over the rows of its other strategies.
        if (frozenW > 0) {
            GuiScissor.enable(mc, left, top, frozenWs, bodyHs);
            for (int i = first; i < end; i++) {
                Row r = rows.get(i);
                drawRowBackground(r, top + r.top - (int) scroll, left, frozenW, true);
            }
            for (int i = first; i < end; i++) {
                Row r = rows.get(i);
                for (Col c : cols) {
                    if (c.frozen) drawCell(r, c, colUnscaledX(c), top + r.top - (int) scroll, i == first);
                }
            }
            GuiScissor.disable();

            // Divider between the frozen and the scrolling part.
            UiDraw.fill(left + frozenW - 1, top, 1, bodyH, COLOR_GROUP_LINE);
        }

        GlStateManager.popMatrix();
    }

    private int findFirstVisibleRow() {
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            if (r.top + r.height > scroll) return i;
        }
        return Math.max(0, rows.size() - 1);
    }

    private void drawRowBackground(Row r, int ry, int rx, int rw, boolean frozenPass) {
        if (frozenPass) {
            UiDraw.fill(rx, ry, rw, r.height, UiTheme.CARD_BG);
        }

        if (r.groupIndex % 2 == 1) {
            UiDraw.fill(rx, ry, rw, r.height, COLOR_ZEBRA);
        }

        boolean hovered = hoverRow >= 0 && rows.get(hoverRow).jump == r.jump;
        if (hovered) {
            UiDraw.fill(rx, ry, rw, r.height, COLOR_HOVER);
        }
        if (r.jump == selectedJump) {
            UiDraw.fill(rx, ry, rw, r.height, COLOR_SELECTED);
        }

        // Lines: strong between jumps, faint between strategies of one jump.
        UiDraw.fill(rx, ry + r.height - 1, rw, 1, r.lastOfGroup() ? COLOR_GROUP_LINE : COLOR_ROW_LINE);

        // Accent bar along the whole selected group, at the very left of the table.
        boolean leftmostRegion = frozenPass || frozenW == 0;
        if (r.jump == selectedJump && leftmostRegion) {
            UiDraw.fill(bodyLeft(), ry, 2, r.height, UiTheme.ACCENT_HOVER);
        }
    }

    private void drawCell(Row r, Col c, int cx, int ry, boolean firstVisibleRow) {
        drawCellContent(r, c, cx, ry, firstVisibleRow);
        if (review == null) return;

        // Review: dim what will not be imported (the checkbox stays clear) and mark the first / last jump.
        if (!review.isIncluded(r.jump)) {
            int skip = c.kind == Kind.NUMBER ? PAD_X + CHECK_W : 0;
            UiDraw.fill(cx + skip, ry, c.width - skip, r.height, COLOR_EXCLUDED);
        }
        if (r.firstOfGroup() && review.isCropStart(r.jump)) {
            UiDraw.fill(cx, ry, c.width, 2, UiTheme.ACCENT_HOVER);
        }
        if (r.lastOfGroup() && review.isCropEnd(r.jump)) {
            UiDraw.fill(cx, ry + r.height - 2, c.width, 2, UiTheme.ACCENT_HOVER);
        }
    }

    private void drawCellContent(Row r, Col c, int cx, int ry, boolean firstVisibleRow) {
        switch (c.kind) {
            case NUMBER:
                drawNumberCell(r, cx, ry);
                break;
            case SECTION:
                drawSectionCell(r, c, cx, ry);
                break;
            case JUMP:
                drawJumpCell(r, c, cx, ry, firstVisibleRow);
                break;
            default:
                drawTextCell(r, c, cx, ry);
        }

        // Column line (not between the stripes of compact sections: their colors should read as one band)
        if (!(c.kind == Kind.SECTION && compactSections)) {
            UiDraw.fill(cx + c.width - 1, ry, 1, r.height - 1, COLOR_COLUMN_LINE);
        }
    }

    /** Y at which one line of text is vertically centered in a row. */
    private int lineY(int ry) {
        return ry + (ROW_H - 8) / 2;
    }

    private void drawNumberCell(Row r, int cx, int ry) {
        if (!r.firstOfGroup()) return;

        int textX = cx + PAD_X + 2;
        if (review != null) {
            int boxY = ry + (ROW_H - 10) / 2;
            boolean checked = review.isChecked(r.jump);
            UiDraw.box(cx + PAD_X, boxY, 10, 10, checked ? UiTheme.ACCENT : UiTheme.CONTROL_BG, UiTheme.CONTROL_BORDER);
            if (checked) {
                UiDraw.fill(cx + PAD_X + 2, boxY + 5, 2, 2, 0xFFFFFFFF);
                UiDraw.fill(cx + PAD_X + 4, boxY + 6, 2, 2, 0xFFFFFFFF);
                UiDraw.fill(cx + PAD_X + 6, boxY + 3, 2, 4, 0xFFFFFFFF);
            }
            textX += CHECK_W;
        }
        UiDraw.text(String.valueOf(r.jumpIndex + 1), textX, lineY(ry), UiTheme.TEXT_MUTED);
    }

    /** Fill color of the section cell of this level for the row, or 0 if no section covers it. */
    private int sectionTint(Row r, int level) {
        // Cells without their own section inherit the color of the nearest column to the left that has one.
        MapSection source = SectionColumns.sectionAt(map, level, r.jumpIndex);
        for (int lvl = level - 1; source == null && lvl >= 0; lvl--) {
            source = SectionColumns.sectionAt(map, lvl, r.jumpIndex);
        }
        return source != null ? SectionColumns.tintOpaque(source.getColorArgb(), 0x101010, 0.72f) : 0;
    }

    private void drawSectionCell(Row r, Col c, int cx, int ry) {
        int tint = sectionTint(r, c.index);
        if (tint != 0) {
            UiDraw.fill(cx, ry, c.width, r.height, tint);
        }

        // Full mode: the section's name, once, where it starts.
        MapSection direct = SectionColumns.sectionAt(map, c.index, r.jumpIndex);
        if (!compactSections && r.firstOfGroup() && direct != null && r.jumpIndex == direct.getStartJumpIndex()) {
            String name = direct.getName() != null ? direct.getName().trim() : "";
            if (!name.isEmpty()) {
                int rgb = direct.getTextColorArgb() & 0xFFFFFF;
                String shown = ellipsize("" + SECTION_SIGN + "l" + name, c.width - PAD_X * 2 + 0);
                UiDraw.text(shown, cx + PAD_X, lineY(ry), rgb | 0xFF000000);
            }
        }
    }

    private void drawJumpCell(Row r, Col c, int cx, int ry, boolean firstVisibleRow) {
        boolean multi = r.groupSize > 1;
        int textW = c.width - (multi ? COUNT_LABEL_W : 0);

        // Template mode: a red bar along all strategies of a jump that has no position yet.
        boolean waiting = templateRows && !TemplateMode.isPlaced(r.jump);
        if (waiting) {
            UiDraw.fill(cx + c.width - 3, ry, 3, r.height, UiTheme.DANGER_HOVER);
        }

        // The name is drawn once per group, from the group's top, over the rows of all its strategies
        // (or from the first visible row when the group's own first row has scrolled away).
        if (r.firstOfGroup() || firstVisibleRow) {
            drawJumpName(r, cx, ry - (r.top - r.groupTop), textW);
        }

        if (multi) {
            String label = (r.groupRowIndex + 1) + "/" + r.groupSize;
            int labelX = cx + c.width - PAD_X - UiDraw.width(label);
            UiDraw.text(label, labelX, lineY(ry), UiTheme.TEXT_DISABLED);

            // Marks the strategy that is shown in the game, right in front of its number.
            if (!waiting && r.jump.getActiveReminderIndex() == r.groupRowIndex) {
                UiDraw.fill(labelX - 6, ry + (ROW_H - 10) / 2, 3, 10, UiTheme.ACCENT_HOVER);
            }
        }
    }

    /** The jump's name, wrapped over the group's height and cut off with "..." if it still does not fit. */
    private void drawJumpName(Row r, int cx, int groupY, int textW) {
        int lineH = lineHeight();
        int maxLines = Math.max(1, (r.groupHeight - PAD_Y * 2) / lineH);

        // Gone from the sheet: red. Not placed in the world yet: dim.
        boolean unplaced = r.jump.getX() == 0 && r.jump.getY() == 0 && r.jump.getZ() == 0;
        int nameColor = r.jump.isSheetMissing() ? UiTheme.DANGER_HOVER : (unplaced && review == null ? UiTheme.TEXT_MUTED : UiTheme.TEXT);

        List<String> lines = wrap(jumpName(r.jump), textW);
        int shown = Math.min(maxLines, lines.size());
        int blockH = shown * lineH;
        int startY = groupY + Math.max(PAD_Y, (r.groupHeight - blockH) / 2);

        for (int i = 0; i < shown; i++) {
            String line = lines.get(i);
            if (i == shown - 1 && lines.size() > shown) {
                // More text follows: always end in dots, even when this line itself fits.
                line = font().trimStringToWidth(line, Math.max(0, textW - PAD_X * 2 - UiDraw.width("..."))) + "...";
            }
            UiDraw.text(line, cx + PAD_X, startY + i * lineH, nameColor);
        }
    }

    private void drawTextCell(Row r, Col c, int cx, int ry) {
        String text = textOf(r, c.index);
        if (text == null || text.trim().isEmpty()) {
            // Empty cells show a faint dash so the row reads as "nothing here" instead of a hole.
            UiDraw.text("-", cx + PAD_X, lineY(ry), COLOR_EMPTY_CELL);
            return;
        }
        UiDraw.text(ellipsize(text, c.width - PAD_X * 2), cx + PAD_X, lineY(ry), UiTheme.TEXT);
    }

    private void drawScrollbars(int mouseX, int mouseY) {
        // The bars sit outside the frame, along its whole length: the vertical one right of it (header
        // included), the horizontal one below it. They work in screen units: the scroll range of the rows is
        // the same, but scaled by the zoom.
        if (needV) {
            int frameH = frameH();
            vScroller.draw(y, y + frameH, x + w + 2, scroll * zoom, Math.round(contentH * zoom + (frameH - bodyHs)));
        } else {
            vScroller.draw(y, y + frameH(), x + w + 2, 0f, 0);
        }

        if (needH) {
            // Spans the whole frame width, although it only moves the scrolling part on the right.
            hScroller.draw(x, x + frameW(), y + h - 6, y + h, scrollViewW, scrollColsW);
        } else {
            hScroller.draw(0, 0, 0, 0, 1, 0);
        }

        // While one scrollbar is dragged the other one is locked, so brushing past it cannot move it.
        if (!hScroller.isDragging()) {
            scroll = vScroller.updateDrag(mouseX, mouseY, scroll * zoom) / zoom;
            clampScroll();
        }
        if (!vScroller.isDragging()) {
            hScroller.updateDrag(mouseX, mouseY);
        }
    }

    // -------------------------
    // Section menus ("fast travel")
    // -------------------------

    /** A click on a section column's header opens the list of that level's sections. */
    private boolean openSectionMenu(int mouseX, int mouseY) {
        if (popup == null || map == null) return false;
        int headerTop = y + 1;
        if (mouseY < headerTop || mouseY >= headerTop + HEADER_H) return false;

        for (Col c : cols) {
            if (c.kind != Kind.SECTION) continue;
            int cx = colScreenX(c);
            int cw = sw(c.width);
            boolean inFrozenPart = mouseX < bodyLeft() + sw(frozenW);
            if (mouseX < cx || mouseX >= cx + cw || (!c.frozen && inFrozenPart)) continue;

            List<UiPopup.Entry> entries = sectionEntries(c.index);
            if (!entries.isEmpty()) popup.open(cx, headerTop + HEADER_H, 160, entries);
            return true;
        }
        return false;
    }

    private List<UiPopup.Entry> sectionEntries(int level) {
        List<UiPopup.Entry> entries = new ArrayList<UiPopup.Entry>();
        ArrayList<MapSection> sections = map.getSectionsForLevel(level + 1);
        if (sections == null) return entries;

        List<MapSection> sorted = new ArrayList<MapSection>();
        for (MapSection s : sections) {
            if (s != null && s.getStartJumpIndex() >= 0 && s.getEndJumpIndex() >= s.getStartJumpIndex()) sorted.add(s);
        }
        sorted.sort((a, b) -> Integer.compare(a.getStartJumpIndex(), b.getStartJumpIndex()));

        int selectedIndex = -1;
        for (Row r : rows) {
            if (r.jump == selectedJump) selectedIndex = r.jumpIndex;
        }

        for (MapSection s : sorted) {
            final MapSection target = s;
            String name = (s.getName() != null && !s.getName().trim().isEmpty()) ? s.getName().trim() : "(unnamed)";
            boolean current = selectedIndex >= s.getStartJumpIndex() && selectedIndex <= s.getEndJumpIndex();
            boolean reachable = firstRowOfSection(s) != null;
            entries.add(new UiPopup.Entry(name, reachable, current, false, () -> travelTo(target)));
        }
        return entries;
    }

    /** First shown row inside the section (a search filter may hide its first jump). */
    private Row firstRowOfSection(MapSection s) {
        for (Row r : rows) {
            if (r.firstOfGroup() && r.jumpIndex >= s.getStartJumpIndex() && r.jumpIndex <= s.getEndJumpIndex()) return r;
        }
        return null;
    }

    private void travelTo(MapSection s) {
        ensureLayout();
        Row r = firstRowOfSection(s);
        if (r == null) return;

        selectedJump = r.jump;
        scroll = r.top;
        clampScroll();
    }

    // -------------------------
    // Move mode (drag and drop)
    // -------------------------

    /** First row of every shown jump, top to bottom. */
    private List<Row> groupHeads() {
        List<Row> heads = new ArrayList<Row>();
        for (Row r : rows) {
            if (r.firstOfGroup()) heads.add(r);
        }
        return heads;
    }

    private int slotAt(int mouseY, List<Row> heads) {
        float contentY = scroll + (mouseY - bodyTop()) / zoom;
        int slot = 0;
        for (Row r : heads) {
            if (r.top + r.groupHeight / 2f < contentY) slot++;
        }
        return slot;
    }

    /** Polled every frame (there is no release event): tracks the drag, scrolls at the edges, drops on release. */
    private void updateDrag(int mouseX, int mouseY) {
        if (dragJump == null) return;

        if (!Mouse.isButtonDown(0)) {
            finishDrag();
            return;
        }

        if (!dragging && Math.abs(mouseY - dragStartY) >= 4) dragging = true;
        if (!dragging) return;

        // Near the top or bottom edge the table keeps scrolling by itself.
        if (mouseY < bodyTop() + 14) {
            scroll -= 6f / zoom;
        } else if (mouseY > bodyTop() + bodyHs - 14) {
            scroll += 6f / zoom;
        }
        clampScroll();

        dropSlot = slotAt(mouseY, groupHeads());
    }

    private void finishDrag() {
        Jump moved = dragJump;
        boolean wasDragging = dragging;
        int slot = dropSlot;
        dragJump = null;
        dragging = false;
        dropSlot = -1;

        if (!wasDragging || moved == null || onMove == null || slot < 0) return;

        List<Row> heads = groupHeads();
        int from = -1;
        for (int i = 0; i < heads.size(); i++) {
            if (heads.get(i).jump == moved) from = i;
        }
        if (from < 0 || slot == from || slot == from + 1) return; // dropped where it already is

        if (slot < heads.size()) {
            onMove.onMove(moved, heads.get(slot).jump, false);
        } else {
            onMove.onMove(moved, heads.get(heads.size() - 1).jump, true);
        }
    }

    private void drawDragOverlay(int mouseX, int mouseY) {
        if (!moveMode) return;

        if (dragging && dropSlot >= 0) {
            List<Row> heads = groupHeads();
            if (!heads.isEmpty()) {
                int contentY = dropSlot < heads.size()
                        ? heads.get(dropSlot).top
                        : heads.get(heads.size() - 1).top + heads.get(heads.size() - 1).groupHeight;
                int lineY = bodyTop() + Math.round((contentY - scroll) * zoom);
                if (lineY >= bodyTop() && lineY <= bodyTop() + bodyHs) {
                    UiDraw.fill(bodyLeft(), lineY - 1, viewWs, 2, UiTheme.ACCENT_HOVER);
                }
            }

            String name = jumpName(dragJump);
            int gw = UiDraw.width(name) + 12;
            UiDraw.box(mouseX + 10, mouseY - 7, gw, 14, UiTheme.PANEL_BG | 0xFF000000, UiTheme.ACCENT);
            UiDraw.text(name, mouseX + 16, mouseY - 4, UiTheme.TEXT);
        }

        String hint = "MOVE MODE - drag jumps to reorder";
        int hw = UiDraw.width(hint) + 12;
        int hy = y + frameH() - 20;
        UiDraw.box(bodyLeft() + 8, hy, hw, 14, UiTheme.PANEL_BG | 0xFF000000, UiTheme.ACCENT);
        UiDraw.text(hint, bodyLeft() + 14, hy + 3, UiTheme.TEXT);
    }

    /** Briefly shows the zoom level in the corner after it changed. */
    private void drawZoomLabel() {
        if (Minecraft.getSystemTime() >= zoomLabelUntil) return;

        String label = Math.round(zoom * 100) + "%";
        int lw = UiDraw.width(label) + 12;
        int lx = x + frameW() - lw - 8;
        int ly = y + frameH() - 20;
        UiDraw.box(lx, ly, lw, 14, UiTheme.PANEL_BG | 0xFF000000, UiTheme.CONTROL_BORDER);
        UiDraw.text(label, lx + 6, ly + 3, UiTheme.TEXT);
    }

    private void drawTooltip(int mouseX, int mouseY) {
        if (hoverRow < 0) return;

        Row r = rows.get(hoverRow);
        Col over = null;
        double relX = (mouseX - bodyLeft()) / zoom; // mouse x in content units, from the body's left
        for (Col c : cols) {
            double cx = contentX(c);
            if (relX >= cx && relX < cx + c.width && (c.frozen || relX >= frozenW)) over = c;
        }
        if (over == null) return;

        List<String> lines = new ArrayList<String>();
        List<Integer> colors = new ArrayList<Integer>();

        if (over.kind == Kind.JUMP) {
            Jump j = r.jump;
            boolean noCoords = j.getX() == 0 && j.getY() == 0 && j.getZ() == 0;
            int strategies = j.getReminders() != null ? j.getReminders().size() : 0;

            for (String line : font().listFormattedStringToWidth(jumpName(j), TOOLTIP_MAX_W)) {
                lines.add(line);
                colors.add(UiTheme.TEXT);
            }
            lines.add(noCoords ? "No coordinates yet" : "X " + j.getX() + "   Y " + j.getY() + "   Z " + j.getZ());
            colors.add(noCoords ? UiTheme.TEXT_DISABLED : UiTheme.TEXT_MUTED);
            lines.add(strategies + (strategies == 1 ? " strategy" : " strategies"));
            colors.add(UiTheme.TEXT_MUTED);
            if (j.isSheetMissing()) {
                lines.add("No longer in the sheet");
                colors.add(UiTheme.DANGER_HOVER);
            }
        } else if (over.kind == Kind.TEXT) {
            // Only text that was cut off needs a tooltip; it shows the whole text.
            String text = textOf(r, over.index);
            if (text == null || text.trim().isEmpty() || fits(text, over.width)) return;

            lines.add(TEXT_TITLES[over.index].toUpperCase());
            colors.add(UiTheme.TEXT_MUTED);
            for (String line : font().listFormattedStringToWidth(text, TOOLTIP_MAX_W)) {
                lines.add(line);
                colors.add(UiTheme.TEXT);
            }
        } else if (over.kind == Kind.SECTION) {
            // The stripe (or a cut-off name) has no room for names: list the sections this jump is in,
            // the hovered level in white.
            for (int level = 0; level < sectionCount; level++) {
                MapSection s = SectionColumns.sectionAt(map, level, r.jumpIndex);
                if (s == null) continue;

                String title = map.getSectionName(level) != null ? map.getSectionName(level).trim() : "";
                if (title.isEmpty()) title = "Level " + (level + 1);
                String name = (s.getName() != null && !s.getName().trim().isEmpty()) ? s.getName().trim() : "(unnamed)";

                lines.add(title + ": " + name);
                colors.add(level == over.index ? UiTheme.TEXT : UiTheme.TEXT_MUTED);
            }
            if (lines.isEmpty()) {
                lines.add("No section");
                colors.add(UiTheme.TEXT_DISABLED);
            }
        } else {
            return;
        }

        int tw = 0;
        for (String line : lines) tw = Math.max(tw, UiDraw.width(line));
        tw += 14;
        int th = lines.size() * 11 + 6;

        ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
        int tx = Math.min(mouseX + 12, sr.getScaledWidth() - tw - 4);
        int ty = Math.min(mouseY + 14, sr.getScaledHeight() - th - 4);

        UiDraw.box(tx, ty, tw, th, UiTheme.PANEL_BG | 0xFF000000, UiTheme.CONTROL_BORDER);
        for (int i = 0; i < lines.size(); i++) {
            UiDraw.text(lines.get(i), tx + 7, ty + 5 + i * 11, colors.get(i).intValue());
        }
    }

    // -------------------------
    // Input
    // -------------------------

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (!visible || !contains(mouseX, mouseY)) return false;
        if (button != 0) return true;

        ensureLayout();
        if (overScrollbars(mouseX, mouseY)) return true; // dragging is handled while drawing
        if (openSectionMenu(mouseX, mouseY)) return true;

        int index = rowAt(mouseX, mouseY);
        if (index < 0) return true;

        Row r = rows.get(index);

        if (review != null && !cols.isEmpty()) {
            double local = (mouseX - colScreenX(cols.get(0))) / (double) zoom;
            if (local >= 0 && local < PAD_X + CHECK_W) {
                review.toggle(r.jump);
                selectedJump = r.jump;
                return true;
            }
        }

        long now = Minecraft.getSystemTime();
        boolean isDouble = !moveMode && (r.groupIndex == lastClickedGroup) && (now - lastClickMs) <= DOUBLE_CLICK_MS;

        if (moveMode) {
            dragJump = r.jump;
            dragging = false;
            dragStartY = mouseY;
        }

        selectedJump = r.jump;
        lastClickedGroup = r.groupIndex;
        lastClickMs = now;

        if (isDouble) {
            lastClickedGroup = -1;
            if (onActivate != null) onActivate.accept(r.jump);
        }
        return true;
    }

    @Override
    public boolean mouseWheel(int mouseX, int mouseY, int delta) {
        // While a jump is dragged the wheel scrolls wherever the mouse is.
        boolean overTable = contains(mouseX, mouseY) || dragging;
        if (!visible || !overTable || delta == 0) return false;
        ensureLayout();

        boolean ctrl = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
        if (ctrl) {
            zoomAt(delta > 0 ? 1 : -1, mouseX, mouseY);
            return true;
        }

        boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
        if (shift) {
            hScroller.scrollByWheel(delta, scrollViewW, scrollColsW);
        } else {
            scroll += (delta > 0) ? -WHEEL_STEP : WHEEL_STEP;
            clampScroll();
        }
        return true;
    }

    // -------------------------
    // Zoom
    // -------------------------

    /**
     * Zooms in (steps > 0) or out around a screen position: the point of the table under that position stays
     * under it, so you can zoom into the part of a long sheet you are looking at.
     */
    public void zoomAt(int steps, int mouseX, int mouseY) {
        ensureLayout();

        float old = zoom;
        float target = (float) (old * Math.pow(ZOOM_STEP, steps));
        float next = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, target));
        zoomLabelUntil = Minecraft.getSystemTime() + ZOOM_LABEL_MS;
        if (Math.abs(next - old) < 0.001f) return;

        // The content point under the cursor, before the change.
        float relY = mouseY - bodyTop();
        float contentY = scroll + relY / old;
        float relX = mouseX - bodyLeft();
        boolean inScrollingPart = relX >= frozenW * old;
        double contentXInScrollingPart = hScroller.getOffset() + relX / old - frozenW;

        zoom = next;
        layoutDirty = true;
        ensureLayout();

        // Put the same point back under the cursor.
        scroll = contentY - relY / next;
        clampScroll();
        if (inScrollingPart) {
            double offset = contentXInScrollingPart - relX / next + frozenW;
            hScroller.setOffset(offset, scrollViewW, scrollColsW);
        }
    }

    /** Zooms around the middle of the table (keyboard shortcuts). */
    public void zoomCentered(int steps) {
        zoomAt(steps, x + frameW() / 2, bodyTop() + bodyHs / 2);
    }

    /** Back to 100 % without showing the zoom label (used when the tab is shown again). */
    public void clearZoom() {
        if (Math.abs(zoom - 1f) < 0.001f) return;
        zoom = 1f;
        layoutDirty = true;
        zoomLabelUntil = 0L;
        scroll = 0f;
        hScroller.setOffset(0, scrollViewW, scrollColsW);
    }

    public void resetZoom() {
        zoomAt(0, x, y);
        if (Math.abs(zoom - 1f) < 0.001f) return;
        zoom = 1f;
        layoutDirty = true;
        zoomLabelUntil = Minecraft.getSystemTime() + ZOOM_LABEL_MS;
        ensureLayout();
        clampScroll();
    }

    /** Identity set of the jumps shown, for callers that need to know whether a jump is visible. */
    public boolean isShowing(Jump jump) {
        ensureLayout();
        for (Row r : rows) {
            if (r.jump == jump) return true;
        }
        return false;
    }
}
