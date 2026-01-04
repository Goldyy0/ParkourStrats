package me.texyle.startreminders.sheets;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SheetHeaderMatcher {

    private SheetHeaderMatcher() {}

    public static class Columns {
        public int jump = -1;
        public int position = -1;
        public int facing = -1;
        public int setup = -1;
        public int strategy = -1;
        public int strafe = -1;
        public int turn = -1;
        public int author = -1;
        public int tips = -1;

        public int section = -1;
        public int subsection = -1;
        public int area = -1;
        public int cp = -1;

        // Optional
        public int sp = -1;
    }

    private static String norm(String s) {
        if (s == null) return "";
        String t = s.trim().toLowerCase();

        // Remove common separators to be tolerant to "Sub-section", "sub_section", "sub section", etc.
        t = t.replace(" ", "");
        t = t.replace("_", "");
        t = t.replace("-", "");
        t = t.replace("/", "");
        t = t.replace("\\", "");
        t = t.replace(".", "");
        return t;
    }

    public static Columns match(List<String> headerRow) {
        Columns cols = new Columns();
        if (headerRow == null) return cols;

        Map<String, Integer> indexByNorm = new HashMap<String, Integer>();
        for (int i = 0; i < headerRow.size(); i++) {
            String key = norm(headerRow.get(i));
            if (key.isEmpty()) continue;

            // Keep first occurrence only
            if (!indexByNorm.containsKey(key)) {
                indexByNorm.put(key, Integer.valueOf(i));
            }
        }

        cols.jump = first(indexByNorm, "jump", "jumpname", "jumpid", "name", "jumptitle");
        cols.position = first(indexByNorm, "position", "pos", "coords", "coordinate", "location");
        cols.facing = first(indexByNorm, "facing", "face", "direction", "f", "f:", "angle");
        cols.setup = first(indexByNorm, "setup", "set", "prep", "preparation");
        cols.strategy = first(indexByNorm, "strategy", "strat", "main", "plan", "method");
        cols.strafe = first(indexByNorm, "strafe", "strafing", "strafeinput");
        cols.turn = first(indexByNorm, "turn", "rotation", "rotate", "turning");
        cols.author = first(indexByNorm, "author", "by", "creator", "madeby");
        cols.tips = first(indexByNorm, "tips", "tip", "notes", "note", "comments", "comment", "remark", "remarks");

        cols.section = first(indexByNorm, "section");
        cols.subsection = first(indexByNorm,
                "subsection", "subsectionname", "sub",
                "subsections", "subsectionsname",
                "subsections", "subsectionsname"
        );
        cols.area = first(indexByNorm, "area");
        cols.cp = first(indexByNorm, "cp", "checkpoint", "check");

        // Optional: SP variants
        cols.sp = first(indexByNorm, "sp", "stagepoint", "startpoint", "spawnpoint");

        return cols;
    }

    private static int first(Map<String, Integer> map, String... keys) {
        if (map == null || keys == null) return -1;

        for (String k : keys) {
            if (k == null) continue;
            Integer idx = map.get(k);
            if (idx != null) return idx.intValue();
        }
        return -1;
    }
}