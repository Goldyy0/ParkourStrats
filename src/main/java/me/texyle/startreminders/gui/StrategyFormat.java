package me.texyle.startreminders.gui;

import java.util.ArrayList;

/**
 * Helpers for the strategy line layouts: new = 8 lines [Position, Facing, Setup, Strategy, Strafe, Turn, Author, Tips],
 * previous = 6 lines without Position / Facing.
 */
public final class StrategyFormat {

    public static final int NEW_FORMAT_SIZE = 8;

    private StrategyFormat() {}

    public static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** BetterLinkCraft strategies have 8 lines and a meaningful Position or Facing. */
    public static boolean isBetterLcEightLine(ArrayList<String> lines) {
        if (lines == null || lines.size() < NEW_FORMAT_SIZE) return false;
        return !isBlank(lineAt(lines, 0)) || !isBlank(lineAt(lines, 1));
    }

    /** @return the line, or "" when missing or null */
    public static String lineAt(ArrayList<String> lines, int idx) {
        if (lines == null || idx < 0 || idx >= lines.size()) {
            return "";
        }
        return orEmpty(lines.get(idx));
    }

    public static String orEmpty(String s) {
        return s != null ? s : "";
    }

    /** Pads the list with empty lines up to the given size. */
    public static void ensureSize(ArrayList<String> lines, int size) {
        if (lines == null) return;
        while (lines.size() < size) {
            lines.add("");
        }
    }

    /**
     * Builds the saved lines for the RestoredStrats editor, which has five generic lines.
     * Always produces the new 8-line format so the renderer and table interpret it correctly.
     * Fields the editor does not show (Position / Facing / Author / ...) keep their existing values.
     *
     * @param existing the strategy's current lines, or null
     */
    public static ArrayList<String> applyRestoredEdit(ArrayList<String> existing,
                                                      String line1, String line2, String line3,
                                                      String line4, String line5) {
        ArrayList<String> out;

        if (existing != null && existing.size() >= NEW_FORMAT_SIZE) {
            out = new ArrayList<String>(existing);
            ensureSize(out, NEW_FORMAT_SIZE);

            if (isBetterLcEightLine(existing)) {
                // BetterLC save mapping (same as display mapping); Strafe(4), Turn(5), Author(6) are preserved
                out.set(0, line1); // Position
                out.set(1, line2); // Facing
                out.set(2, line3); // Setup
                out.set(3, line4); // Input (Strategy)
                out.set(7, line5); // Comment (Tips)
            } else {
                // Legacy-normalized 8-line: "setup-start" mapping; Position(0), Facing(1), Author(6) are preserved
                out.set(2, line1); // Setup
                out.set(3, line2); // Strategy
                out.set(4, line3); // Strafe
                out.set(5, line4); // Turn
                out.set(7, line5); // Tips
            }
            return out;
        }

        // Converting an original legacy (<8) strategy: treat as legacy-normalized (setup-start), NOT BetterLC.
        out = new ArrayList<String>();
        out.add("");    // Position
        out.add("");    // Facing
        out.add(line1); // Setup
        out.add(line2); // Strategy
        out.add(line3); // Strafe
        out.add(line4); // Turn
        out.add("");    // Author
        out.add(line5); // Tips
        return out;
    }

    /**
     * The five generic lines the RestoredStrats editor shows for a strategy:
     * BetterLinkCraft 8-line [Position, Facing, Setup, Input, Comment], legacy-normalized 8-line
     * [Setup, Strategy, Strafe, Turn, Tips], or the first five lines of an old strategy.
     * The inverse of {@link #applyRestoredEdit}.
     */
    public static String[] restoredEditorLines(ArrayList<String> lines) {
        int[] source;
        if (lines != null && lines.size() >= NEW_FORMAT_SIZE) {
            source = isBetterLcEightLine(lines) ? new int[] { 0, 1, 2, 3, 7 } : new int[] { 2, 3, 4, 5, 7 };
        } else {
            source = new int[] { 0, 1, 2, 3, 4 };
        }

        String[] out = new String[source.length];
        for (int i = 0; i < source.length; i++) {
            out[i] = lineAt(lines, source[i]);
        }
        return out;
    }
}
