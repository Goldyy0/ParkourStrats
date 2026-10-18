package me.texyle.startreminders.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import me.texyle.startreminders.data.MapSection;
import me.texyle.startreminders.data.ParkourMap;
import me.texyle.startreminders.ui.UiPopup.Entry;

/** Builds the popup entries for picking one optional section out of all levels of a map. */
public final class SectionChoices {

    private static final String[] ROMAN = { "", "I", "II", "III", "IV" };

    private SectionChoices() {}

    /**
     * @param current the picked section, or null for "None"
     * @param onPick  receives (level, section); level 0 and a null section mean "None"
     */
    public static List<Entry> entries(ParkourMap map, MapSection current, BiConsumer<Integer, MapSection> onPick) {
        List<Entry> entries = new ArrayList<Entry>();
        entries.add(Entry.choice("None", current == null, () -> onPick.accept(Integer.valueOf(0), null)));

        if (map == null) return entries;

        int levels = Math.max(1, Math.min(4, map.getMaxSectionLevelsAllowed()));
        for (int level = 1; level <= levels; level++) {
            ArrayList<MapSection> sections = map.getSectionsForLevel(level);
            if (sections == null) continue;

            for (final MapSection s : sections) {
                if (s == null) continue;
                final int lvl = level;
                entries.add(Entry.choice(label(lvl, s), s == current, () -> onPick.accept(Integer.valueOf(lvl), s)));
            }
        }
        return entries;
    }

    /** "II  Name (3 - 8)", or "(Not set)" for a section without a range. */
    public static String label(int level, MapSection s) {
        String name = (s.getName() != null && !s.getName().trim().isEmpty()) ? s.getName().trim() : "(unnamed)";
        String range = (s.getStartJumpIndex() < 0 || s.getEndJumpIndex() < 0)
                ? "(Not set)"
                : "(" + (s.getStartJumpIndex() + 1) + " - " + (s.getEndJumpIndex() + 1) + ")";
        return ROMAN[Math.max(0, Math.min(4, level))] + "  " + name + " " + range;
    }
}
