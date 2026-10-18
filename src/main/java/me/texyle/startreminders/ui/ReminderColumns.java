package me.texyle.startreminders.ui;

import java.util.ArrayList;

import me.texyle.startreminders.reminders.Reminder;

/**
 * Reads table columns out of a strategy's text lines, which exist in two layouts:
 * <ul>
 * <li>new (8 lines): [Position, Facing, Setup, Strategy, Strafe, Turn, Author, Tips]</li>
 * <li>previous (6 lines): [Setup, Strategy, Strafe, Turn, Author, Tips]</li>
 * </ul>
 */
final class ReminderColumns {

    private static final int NEW_MIN_SIZE = 8;
    private static final int NEW_SETUP_OFFSET = 2;

    private ReminderColumns() {}

    /** Position exists only in the new layout. */
    static String position(Reminder r) {
        if (r == null || r.lines == null) return "";
        return r.lines.size() >= NEW_MIN_SIZE ? nullSafe(r.lines.get(0)) : "";
    }

    /** Facing exists only in the new layout. */
    static String facing(Reminder r) {
        if (r == null || r.lines == null) return "";
        return r.lines.size() >= NEW_MIN_SIZE ? nullSafe(r.lines.get(1)) : "";
    }

    /**
     * @param fieldIdx 0 = Setup, 1 = Strategy, 2 = Strafe, 3 = Turn, 4 = Author, 5 = Tips
     */
    static String field(Reminder r, int fieldIdx) {
        if (r == null || r.lines == null || fieldIdx < 0 || fieldIdx > 5) {
            return "";
        }

        if (r.lines.size() >= NEW_MIN_SIZE) {
            return lineAt(r.lines, NEW_SETUP_OFFSET + fieldIdx);
        }
        return lineAt(r.lines, fieldIdx);
    }

    private static String lineAt(ArrayList<String> lines, int idx) {
        if (lines == null || idx < 0 || idx >= lines.size()) {
            return "";
        }
        return nullSafe(lines.get(idx));
    }

    private static String nullSafe(String s) {
        return s != null ? s : "";
    }
}
