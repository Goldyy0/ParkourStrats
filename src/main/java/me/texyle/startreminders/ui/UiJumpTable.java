package me.texyle.startreminders.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Keyboard;

import me.texyle.startreminders.data.Jump;
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
    private static final int BAR = 8;
    private static final int PAD_X = 5;
    private static final int PAD_Y = 4;
    private static final int ROW_H = 22;
    private static final int TOOLTIP_MAX_W = 280;
    private static final int WHEEL_STEP = 24;
    private static final long DOUBLE_CLICK_MS = 320L;

    private static final int NUM_W = 30;
    private static final int SECTION_W = 80;
    private static final int COMPACT_SECTION_W = 10;
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
    private String filter = "";
    private String emptyText = "";
    private Consumer<Jump> onActivate;

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
    private int viewW;
    private int bodyH;
    private int scrollViewW;
    private int scrollColsW;
    private int contentH;

    private final HorizontalScroller hScroller = new HorizontalScroller();
    private final VerticalScrollbar vScroller = new VerticalScrollbar();

    private int hoverRow = -1;
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

        String id = jump.getId();
        if (id != null && id.toLowerCase().contains(filter)) return true;

        List<Reminder> reminders = jump.getReminders();
        if (reminders == null) return false;
        for (Reminder r : reminders) {
            if (r == null || r.lines == null) continue;
            for (String line : r.lines) {
                if (line != null && line.toLowerCase().contains(filter)) return true;
            }
        }
        return false;
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

        sectionCount = map != null ? Math.max(0, Math.min(4, map.getEffectiveSectionsCount())) : 0;
        compactSections = UserPreferences.isSectionsCompact();

        int innerW = w - 2;
        int availH = h - 2 - HEADER_H;

        needV = false;
        for (int pass = 0; pass < 3; pass++) {
            viewW = innerW - (needV ? BAR : 0);
            buildColumns();
            bodyH = availH - (needH ? BAR : 0);
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
        int leftNatural = NUM_W + sectionCount * baseSectionW + JUMP_W;
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
            int budget = (int) (viewW * MAX_FROZEN_SHARE) - NUM_W - JUMP_W;
            if (sectionCount == 0) {
                frozenEnabled = (NUM_W + JUMP_W) <= viewW * MAX_FROZEN_SHARE;
            } else {
                int fitted = Math.min(baseSectionW, budget / sectionCount);
                // Compact stripes are already as narrow as they get; full columns may shrink a little.
                frozenEnabled = fitted >= (compactSections ? baseSectionW : MIN_SECTION_W);
                if (frozenEnabled) sectionW = fitted;
            }
        }

        add(Kind.NUMBER, 0, NUM_W);
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

    private int bodyLeft() {
        return x + 1;
    }

    private int bodyTop() {
        return y + 1 + HEADER_H;
    }

    /** Screen x of a column's left edge. */
    private int colScreenX(Col c) {
        if (c.frozen) return bodyLeft() + c.regionX;
        return bodyLeft() + frozenW + c.regionX - (int) Math.round(hScroller.getOffset());
    }

    private int rowAt(int mouseX, int mouseY) {
        if (mouseX < bodyLeft() || mouseX >= bodyLeft() + viewW) return -1;
        if (mouseY < bodyTop() || mouseY >= bodyTop() + bodyH) return -1;

        int contentY = (int) scroll + (mouseY - bodyTop());
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            if (contentY >= r.top && contentY < r.top + r.height) return i;
        }
        return -1;
    }

    private boolean overScrollbars(int mouseX, int mouseY) {
        return (needV && mouseX >= x + w - 1 - BAR) || (needH && mouseY >= y + h - 1 - BAR);
    }

    // -------------------------
    // Drawing
    // -------------------------

    @Override
    public void draw(int mouseX, int mouseY) {
        if (!visible) return;
        ensureLayout();
        clampScroll();

        UiDraw.box(x, y, w, h, UiTheme.CARD_BG, UiTheme.CARD_BORDER);

        hoverRow = overScrollbars(mouseX, mouseY) ? -1 : rowAt(mouseX, mouseY);

        drawHeader();

        if (rows.isEmpty()) {
            if (!emptyText.isEmpty()) {
                UiDraw.centered(emptyText, x + w / 2, bodyTop() + bodyH / 2 - 4, UiTheme.TEXT_MUTED);
            }
        } else {
            drawBody();
        }

        drawScrollbars(mouseX, mouseY);
        drawTooltip(mouseX, mouseY);
    }

    private void drawHeader() {
        int left = bodyLeft();
        int top = y + 1;

        UiDraw.fill(left, top, w - 2, HEADER_H, UiTheme.TABLE_HEADER_BG);
        UiDraw.fill(left, top + HEADER_H - 1, w - 2, 1, UiTheme.CARD_BORDER);

        // Scrolling columns first; frozen ones are drawn on top so scrolled text never shows through them.
        GuiScissor.enable(Minecraft.getMinecraft(), left + frozenW, top, viewW - frozenW, HEADER_H);
        for (Col c : cols) {
            if (!c.frozen) drawHeaderCell(c, top);
        }
        GuiScissor.disable();

        if (frozenW > 0) {
            UiDraw.fill(left, top, frozenW, HEADER_H - 1, UiTheme.TABLE_HEADER_BG);
            GuiScissor.enable(Minecraft.getMinecraft(), left, top, frozenW, HEADER_H);
            for (Col c : cols) {
                if (c.frozen) drawHeaderCell(c, top);
            }
            GuiScissor.disable();
        }
    }

    private void drawHeaderCell(Col c, int top) {
        if (c.kind == Kind.SECTION && compactSections) return; // too narrow for a title; see the tooltip

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
        UiDraw.text(UiDraw.fit(title.toUpperCase(), c.width - PAD_X * 2), cx + PAD_X, top + (HEADER_H - 8) / 2, UiTheme.TEXT_MUTED);
        UiDraw.fill(cx + c.width - 1, top + 4, 1, HEADER_H - 9, COLOR_COLUMN_LINE);
    }

    private void drawBody() {
        int left = bodyLeft();
        int top = bodyTop();
        int first = findFirstVisibleRow();
        int end = first;
        while (end < rows.size() && top + rows.get(end).top - (int) scroll < top + bodyH) end++;
        Minecraft mc = Minecraft.getMinecraft();

        // Pass 1: the scrolling region.
        GuiScissor.enable(mc, left + frozenW, top, viewW - frozenW, bodyH);
        for (int i = first; i < end; i++) {
            Row r = rows.get(i);
            drawRowBackground(r, top + r.top - (int) scroll, left + frozenW, viewW - frozenW, false);
        }
        for (int i = first; i < end; i++) {
            Row r = rows.get(i);
            for (Col c : cols) {
                if (!c.frozen) drawCell(r, c, colScreenX(c), top + r.top - (int) scroll, i == first);
            }
        }
        GuiScissor.disable();

        // Pass 2: the frozen region, over the scrolled one. All backgrounds first, because a jump's name
        // may run over the rows of its other strategies.
        if (frozenW > 0) {
            GuiScissor.enable(mc, left, top, frozenW, bodyH);
            for (int i = first; i < end; i++) {
                Row r = rows.get(i);
                drawRowBackground(r, top + r.top - (int) scroll, left, frozenW, true);
            }
            for (int i = first; i < end; i++) {
                Row r = rows.get(i);
                for (Col c : cols) {
                    if (c.frozen) drawCell(r, c, colScreenX(c), top + r.top - (int) scroll, i == first);
                }
            }
            GuiScissor.disable();

            drawStripeExtension(top);

            // Divider, with a soft shadow while content is scrolled under it.
            UiDraw.fill(left + frozenW - 1, top, 1, bodyH, COLOR_GROUP_LINE);
            if (hScroller.getOffset() > 0.5) {
                UiDraw.fill(left + frozenW, top, 3, bodyH, 0x33000000);
            }
        }
    }

    /**
     * The section stripes continue down through the strip of the horizontal scrollbar, to the end of the table,
     * in the color the next row would have (or the last row's when the content ends).
     */
    private void drawStripeExtension(int top) {
        if (!needH || !frozenEnabled || sectionCount == 0 || rows.isEmpty()) return;

        float probe = scroll + bodyH;
        Row row = rows.get(rows.size() - 1);
        for (Row r : rows) {
            if (probe >= r.top && probe < r.top + r.height) {
                row = r;
                break;
            }
        }

        for (Col c : cols) {
            if (c.kind != Kind.SECTION || !c.frozen) continue;
            int tint = sectionTint(row, c.index);
            if (tint != 0) {
                UiDraw.fill(colScreenX(c), top + bodyH, c.width, BAR, tint);
            }
        }
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
        if (r.firstOfGroup()) {
            UiDraw.text(String.valueOf(r.jumpIndex + 1), cx + PAD_X + 2, lineY(ry), UiTheme.TEXT_MUTED);
        }
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
            if (r.jump.getActiveReminderIndex() == r.groupRowIndex) {
                UiDraw.fill(labelX - 6, ry + (ROW_H - 10) / 2, 3, 10, UiTheme.ACCENT_HOVER);
            }
        }
    }

    /** The jump's name, wrapped over the group's height and cut off with "..." if it still does not fit. */
    private void drawJumpName(Row r, int cx, int groupY, int textW) {
        int lineH = lineHeight();
        int maxLines = Math.max(1, (r.groupHeight - PAD_Y * 2) / lineH);

        List<String> lines = wrap(jumpName(r.jump), textW);
        int shown = Math.min(maxLines, lines.size());
        int blockH = shown * lineH;
        int startY = groupY + Math.max(PAD_Y, (r.groupHeight - blockH) / 2);

        for (int i = 0; i < shown; i++) {
            String line = lines.get(i);
            if (i == shown - 1 && lines.size() > shown) {
                line = ellipsize(line, textW - PAD_X * 2);
            }
            UiDraw.text(line, cx + PAD_X, startY + i * lineH, UiTheme.TEXT);
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
        if (needV) {
            vScroller.draw(bodyTop(), bodyTop() + bodyH, x + w, scroll, contentH);
        } else {
            vScroller.draw(bodyTop(), bodyTop() + bodyH, x + w, 0f, 0);
        }

        if (needH) {
            int yTop = y + h - 1 - BAR + 2;
            // The bar spans the whole table width, although it only moves the scrolling part on the right.
            hScroller.draw(bodyLeft() + 2, bodyLeft() + viewW - 2, yTop, yTop + 4, scrollViewW, scrollColsW);
        } else {
            hScroller.draw(0, 0, 0, 0, 1, 0);
        }

        // While one scrollbar is dragged the other one is locked, so brushing past it cannot move it.
        if (!hScroller.isDragging()) {
            scroll = vScroller.updateDrag(mouseX, mouseY, scroll);
            clampScroll();
        }
        if (!vScroller.isDragging()) {
            hScroller.updateDrag(mouseX, mouseY);
        }
    }

    private void drawTooltip(int mouseX, int mouseY) {
        if (hoverRow < 0) return;

        Row r = rows.get(hoverRow);
        Col over = null;
        for (Col c : cols) {
            int cx = colScreenX(c);
            if (mouseX >= cx && mouseX < cx + c.width && (c.frozen || mouseX >= bodyLeft() + frozenW)) over = c;
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

        int index = rowAt(mouseX, mouseY);
        if (index < 0) return true;

        Row r = rows.get(index);
        long now = Minecraft.getSystemTime();
        boolean isDouble = (r.groupIndex == lastClickedGroup) && (now - lastClickMs) <= DOUBLE_CLICK_MS;

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
        if (!visible || !contains(mouseX, mouseY) || delta == 0) return false;
        ensureLayout();

        boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
        if (shift) {
            hScroller.scrollByWheel(delta, scrollViewW, scrollColsW);
        } else {
            scroll += (delta > 0) ? -WHEEL_STEP : WHEEL_STEP;
            clampScroll();
        }
        return true;
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
