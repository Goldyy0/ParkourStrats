package me.texyle.startreminders.gui.hierarchy;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

import net.minecraft.client.gui.FontRenderer;

import me.texyle.startreminders.data.Jump;
import me.texyle.startreminders.reminders.Reminder;

/**
 * Row model of the jump table: one row per strategy of each jump (a jump without strategies gets one empty row),
 * plus the pixel height / top of every row, which depends on how much text wraps.
 */
final class JumpTableRows {

    private static final int MIN_ROW_HEIGHT = 22;

    /** One table row. */
    static final class Row {
        final Jump jump;
        final Reminder reminder;      // may be null if jump has no strategies
        final int groupRowIndex;      // 0..groupSize-1
        final int groupSize;

        Row(Jump jump, Reminder reminder, int groupRowIndex, int groupSize) {
            this.jump = jump;
            this.reminder = reminder;
            this.groupRowIndex = groupRowIndex;
            this.groupSize = groupSize;
        }

        int getOrdinalOneBased() {
            return groupRowIndex + 1;
        }
    }

    private final List<Jump> items;
    private final FontRenderer font;

    private final ArrayList<Row> rows = new ArrayList<Row>();
    private final ArrayList<Integer> heights = new ArrayList<Integer>();
    private final ArrayList<Integer> tops = new ArrayList<Integer>();
    private int contentHeightPx = 0;

    // Jump -> index in items (identity based, equals() could match the wrong jump)
    private final IdentityHashMap<Jump, Integer> jumpIndexCache = new IdentityHashMap<Jump, Integer>();

    JumpTableRows(List<Jump> items, FontRenderer font) {
        this.items = items;
        this.font = font;
    }

    int size() {
        return rows.size();
    }

    Row get(int index) {
        return (index >= 0 && index < rows.size()) ? rows.get(index) : null;
    }

    int heightOf(int index) {
        return heights.get(index);
    }

    int topOf(int index) {
        return tops.get(index);
    }

    int getContentHeight() {
        return contentHeightPx;
    }

    /** Rebuilds rows from the jumps, then their layout. */
    void rebuild() {
        rows.clear();
        jumpIndexCache.clear();

        if (items == null) {
            relayout();
            return;
        }

        for (int i = 0; i < items.size(); i++) {
            Jump j = items.get(i);
            if (j != null) {
                jumpIndexCache.put(j, Integer.valueOf(i));
            }
        }

        for (Jump j : items) {
            if (j == null) {
                continue;
            }

            List<Reminder> rems = j.getReminders();

            if (rems == null || rems.isEmpty()) {
                rows.add(new Row(j, null, 0, 1));
                continue;
            }

            int groupSize = rems.size();
            for (int i = 0; i < rems.size(); i++) {
                rows.add(new Row(j, rems.get(i), i, groupSize));
            }
        }

        relayout();
    }

    /** Recomputes row heights and tops (e.g. after the column widths changed). */
    void relayout() {
        heights.clear();
        tops.clear();
        contentHeightPx = 0;

        for (int i = 0; i < rows.size(); i++) {
            tops.add(contentHeightPx);

            int h = computeRowHeight(i, rows.get(i));

            heights.add(h);
            contentHeightPx += h;
        }
    }

    /** @return index of the first row that is at least partly below the given content-space scroll offset, or -1. */
    int findFirstVisibleRow(int scroll) {
        if (tops.isEmpty()) {
            return -1;
        }

        for (int i = 0; i < tops.size(); i++) {
            int bottom = tops.get(i) + heights.get(i);
            if (bottom > scroll) {
                return i;
            }
        }
        return tops.size() - 1;
    }

    /** @return the row covering the given content-space y, or -1. */
    int rowIndexAtContentY(int yInContent) {
        for (int i = 0; i < tops.size(); i++) {
            int t = tops.get(i);
            int b = t + heights.get(i);
            if (yInContent >= t && yInContent < b) {
                return i;
            }
        }
        return -1;
    }

    /** @return the first row of the given jump (reference match), or -1. */
    int firstRowOf(Jump jump) {
        for (int i = 0; i < rows.size(); i++) {
            Row rr = rows.get(i);
            if (rr != null && rr.jump == jump) {
                return i;
            }
        }
        return -1;
    }

    /** @return position of the jump in the items list (reference match), or -1. */
    int jumpIndexOf(Jump j) {
        if (j == null) return -1;

        Integer cached = jumpIndexCache.get(j);
        if (cached != null) return cached.intValue();

        if (items == null) return -1;

        for (int i = 0; i < items.size(); i++) {
            if (items.get(i) == j) {
                jumpIndexCache.put(j, Integer.valueOf(i));
                return i;
            }
        }
        return -1;
    }

    static String jumpCellText(Row row) {
        String jumpId = row.jump.getId() != null ? row.jump.getId() : "<null>";
        return jumpId + " (" + row.getOrdinalOneBased() + ")";
    }

    private int computeRowHeight(int rowIndex, Row rr) {
        if (rr == null) {
            return MIN_ROW_HEIGHT;
        }

        Reminder r = rr.reminder;

        int maxLines = 1;

        maxLines = Math.max(maxLines, countWrappedLines(String.valueOf(rowIndex + 1), GuiJumpTableSlot.COL_ROW_W));
        maxLines = Math.max(maxLines, countWrappedLines(jumpCellText(rr), GuiJumpTableSlot.COL_JUMP_W));
        maxLines = Math.max(maxLines, countWrappedLines(ReminderColumns.position(r), GuiJumpTableSlot.COL_POS_W));
        maxLines = Math.max(maxLines, countWrappedLines(ReminderColumns.facing(r), GuiJumpTableSlot.COL_FACING_W));
        maxLines = Math.max(maxLines, countWrappedLines(ReminderColumns.field(r, 0), GuiJumpTableSlot.COL_SETUP_W));
        maxLines = Math.max(maxLines, countWrappedLines(ReminderColumns.field(r, 1), GuiJumpTableSlot.COL_STRAT_W));
        maxLines = Math.max(maxLines, countWrappedLines(ReminderColumns.field(r, 2), GuiJumpTableSlot.COL_STRAFE_W));
        maxLines = Math.max(maxLines, countWrappedLines(ReminderColumns.field(r, 3), GuiJumpTableSlot.COL_TURN_W));
        maxLines = Math.max(maxLines, countWrappedLines(ReminderColumns.field(r, 4), GuiJumpTableSlot.COL_AUTHOR_W));
        maxLines = Math.max(maxLines, countWrappedLines(ReminderColumns.field(r, 5), GuiJumpTableSlot.COL_TIPS_W));

        int h = (GuiJumpTableSlot.CELL_PAD_Y * 2) + (maxLines * font.FONT_HEIGHT);

        return Math.max(MIN_ROW_HEIGHT, h);
    }

    private int countWrappedLines(String text, int colW) {
        int innerW = Math.max(0, colW - (GuiJumpTableSlot.CELL_PAD_X * 2));
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
}
