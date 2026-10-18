package me.texyle.startreminders.gui;

import java.util.ArrayList;

import me.texyle.startreminders.data.MapSection;
import me.texyle.startreminders.data.ParkourMap;
import me.texyle.startreminders.data.ServerProfile;

/**
 * Remembers the section picked while creating jumps, so repeated creation in the same server + map
 * keeps landing in the same section. The memory is dropped when the server or map changes.
 */
final class StickySectionCache {

    /** A remembered section together with its level. */
    static final class Pick {
        final int levelOneBased;
        final MapSection section;

        Pick(int levelOneBased, MapSection section) {
            this.levelOneBased = levelOneBased;
            this.section = section;
        }
    }

    private static String cachedServerId = null;
    private static String cachedMapId = null;
    private static int cachedSectionLevelOneBased = 0; // 0 = none
    private static String cachedSectionId = null; // MapSection.getId()

    private StickySectionCache() {}

    /** @return the remembered section if it belongs to this server + map and still exists, else null */
    static Pick restore(ServerProfile server, ParkourMap map) {
        if (server == null || map == null) {
            clear();
            return null;
        }

        String sid = trimmed(server.getId());
        String mid = trimmed(map.getId());

        if (sid.isEmpty() || mid.isEmpty()) {
            clear();
            return null;
        }

        if (!sid.equals(cachedServerId) || !mid.equals(cachedMapId)) {
            // Different context => do not restore
            return null;
        }

        if (cachedSectionLevelOneBased < 1 || cachedSectionLevelOneBased > 4) {
            return null;
        }

        if (cachedSectionId == null || cachedSectionId.trim().isEmpty()) {
            return null;
        }

        MapSection resolved = findSectionById(map, cachedSectionLevelOneBased, cachedSectionId);
        return resolved != null ? new Pick(cachedSectionLevelOneBased, resolved) : null;
    }

    /** Remembers the section for this server + map; a null section remembers "no section". */
    static void remember(ServerProfile server, ParkourMap map, int levelOneBased, MapSection section) {
        String sid = (server != null) ? trimmed(server.getId()) : "";
        String mid = (map != null) ? trimmed(map.getId()) : "";

        if (sid.isEmpty() || mid.isEmpty()) {
            clear();
            return;
        }

        cachedServerId = sid;
        cachedMapId = mid;

        if (section == null || levelOneBased < 1 || levelOneBased > 4) {
            cachedSectionLevelOneBased = 0;
            cachedSectionId = null;
            return;
        }

        cachedSectionLevelOneBased = levelOneBased;
        cachedSectionId = section.getId();
    }

    static void clear() {
        cachedServerId = null;
        cachedMapId = null;
        cachedSectionLevelOneBased = 0;
        cachedSectionId = null;
    }

    private static MapSection findSectionById(ParkourMap map, int levelOneBased, String sectionId) {
        ArrayList<MapSection> list = map.getSectionsForLevel(levelOneBased);
        if (list == null || list.isEmpty()) return null;

        for (int i = 0; i < list.size(); i++) {
            MapSection s = list.get(i);
            if (s == null) continue;
            String id = s.getId();
            if (id != null && id.equals(sectionId)) {
                return s;
            }
        }

        return null;
    }

    private static String trimmed(String s) {
        return s != null ? s.trim() : "";
    }
}
