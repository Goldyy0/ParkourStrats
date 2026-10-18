package me.texyle.startreminders.ui;

import java.util.ArrayList;

import me.texyle.startreminders.data.MapSection;
import me.texyle.startreminders.data.ParkourMap;

/** Section lookups and coloring for the section columns of the jump table. */
final class SectionColumns {

    private SectionColumns() {}

    /**
     * @param columnIndex 0-based section column (Section I..IV); the map API uses 1-based levels
     * @return the active section of that column containing the jump, or null
     */
    static MapSection sectionAt(ParkourMap map, int columnIndex, int jumpIndex) {
        if (map == null) return null;
        if (jumpIndex < 0) return null;

        int levelOneBased = columnIndex + 1;

        // Respect map configuration (e.g., if map has only 2 section levels)
        if (levelOneBased < 1 || levelOneBased > map.getMaxSectionLevelsAllowed()) {
            return null;
        }

        ArrayList<MapSection> list = map.getSectionsForLevel(levelOneBased);
        if (list == null || list.isEmpty()) return null;

        for (int i = 0; i < list.size(); i++) {
            MapSection s = list.get(i);
            if (s == null) continue;

            int a = s.getStartJumpIndex();
            int b = s.getEndJumpIndex();

            // Ignore inactive sections ("Not set")
            if (a < 0 || b < 0) {
                continue;
            }

            if (a > b) {
                int tmp = a;
                a = b;
                b = tmp;
            }

            if (jumpIndex >= a && jumpIndex <= b) {
                return s;
            }
        }

        return null;
    }

    /** Blends an ARGB color toward a base RGB color and forces it opaque. */
    static int tintOpaque(int argb, int baseRgb, float strength) {
        if (strength < 0f) strength = 0f;
        if (strength > 1f) strength = 1f;

        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = (argb) & 0xFF;

        int br = (baseRgb >> 16) & 0xFF;
        int bg = (baseRgb >> 8) & 0xFF;
        int bb = (baseRgb) & 0xFF;

        int outR = (int) (br + (r - br) * strength);
        int outG = (int) (bg + (g - bg) * strength);
        int outB = (int) (bb + (b - bb) * strength);

        return (0xFF << 24) | ((outR & 0xFF) << 16) | ((outG & 0xFF) << 8) | (outB & 0xFF);
    }
}
