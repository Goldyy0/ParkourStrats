package me.texyle.startreminders.template;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;

import me.texyle.startreminders.data.DataStore;
import me.texyle.startreminders.data.Jump;
import me.texyle.startreminders.data.MapSection;
import me.texyle.startreminders.data.ParkourMap;
import me.texyle.startreminders.data.ServerProfile;
import me.texyle.startreminders.persistence.BuiltinContainers;
import me.texyle.startreminders.persistence.DataStoreRepository;
import me.texyle.startreminders.reminders.SelectionState;
import me.texyle.startreminders.reminders.UserPreferences;
import me.texyle.startreminders.services.SectionLayoutService;
import me.texyle.startreminders.utils.ChatUtils;
import me.texyle.startreminders.utils.PlayerBlockPos;

/**
 * Template mode: the jumps of a map (usually imported from a sheet) already exist and only need positions. The create
 * key then puts the "current" jump where the player stands (see Settings, Strategy position) and moves on to the next
 * jump without a position. The current jump can be changed in the strategy editor, by jump or by (sub)section.
 */
public final class TemplateMode {

    private TemplateMode() {}

    /** 0 = step by jump, otherwise by section of this level (1..5). */
    private static int navLevel = 0;

    private static ParkourMap cursorMap;
    private static Jump cursor;

    private static final long PLACE_COOLDOWN_MS = 1000L;
    private static long lastPlaceMs;

    private static ParkourMap activeMap;
    private static ServerProfile activeServer;

    /** Finds the one map that is in template mode (and its server) in any of the stores. */
    private static void resolve() {
        activeMap = null;
        activeServer = null;
        DataStoreRepository.ensureLoaded();

        DataStore[] stores = {
                DataStoreRepository.getGlobalStore(),
                DataStoreRepository.getRestoredStore(),
                DataStoreRepository.getSharedStore()
        };
        for (DataStore store : stores) {
            if (store == null || store.getServers() == null) continue;
            for (ServerProfile server : store.getServers()) {
                if (server == null || server.getMaps() == null || BuiltinContainers.isProtectedServer(server)) continue;
                for (ParkourMap map : server.getMaps()) {
                    if (map != null && map.isTemplateMode()) {
                        activeMap = map;
                        activeServer = server;
                        return;
                    }
                }
            }
        }
    }

    /** True when some map is in template mode. */
    public static boolean isActive() {
        return map() != null;
    }

    /** True when the template map is also the map selected in the windows. */
    public static boolean isEditing() {
        ParkourMap map = map();
        return map != null && SelectionState.getSelectedMap() == map;
    }

    /** The map in template mode, or null. */
    public static ParkourMap map() {
        resolve();
        return activeMap;
    }

    /** Turns template mode on for the map (and off for every other map), or off. */
    public static void setEnabled(ParkourMap target, boolean on) {
        DataStoreRepository.ensureLoaded();

        DataStore[] stores = {
                DataStoreRepository.getGlobalStore(),
                DataStoreRepository.getRestoredStore(),
                DataStoreRepository.getSharedStore()
        };
        for (DataStore store : stores) {
            if (store == null || store.getServers() == null) continue;
            for (ServerProfile server : store.getServers()) {
                if (server == null || server.getMaps() == null) continue;
                for (ParkourMap map : server.getMaps()) {
                    if (map != null) map.setTemplateMode(false);
                }
            }
        }

        if (target != null && on) {
            target.setTemplateMode(true);
        }
        DataStoreRepository.save();

        ChatUtils.info(on && target != null
                ? "Template mode on for " + target.getId() + ": the create key places the current jump where you stand."
                : "Template mode off.");
    }

    public static boolean isPlaced(Jump jump) {
        return jump != null && !(jump.getX() == 0 && jump.getY() == 0 && jump.getZ() == 0);
    }

    // -------------------------
    // Current jump
    // -------------------------

    /** The jump the create key will place; the first one without a position if none was chosen yet. */
    public static Jump current() {
        ParkourMap map = map();
        if (map == null || map.getJumps() == null || map.getJumps().isEmpty()) return null;

        List<Jump> jumps = map.getJumps();
        if (cursorMap != map || cursor == null || !contains(jumps, cursor)) {
            cursorMap = map;
            cursor = firstUnplaced(jumps, 0);
            if (cursor == null) cursor = jumps.get(0);
        }
        return cursor;
    }

    public static int currentIndex() {
        Jump jump = current();
        ParkourMap map = map();
        return (jump == null || map == null) ? -1 : indexOf(map.getJumps(), jump);
    }

    public static void setCurrent(Jump jump) {
        ParkourMap map = map();
        if (map == null || jump == null || !contains(map.getJumps(), jump)) return;

        cursorMap = map;
        cursor = jump;
        SelectionState.select(activeServer, map, jump);
    }

    private static boolean contains(List<Jump> jumps, Jump jump) {
        return indexOf(jumps, jump) >= 0;
    }

    private static int indexOf(List<Jump> jumps, Jump jump) {
        if (jumps == null) return -1;
        for (int i = 0; i < jumps.size(); i++) {
            if (jumps.get(i) == jump) return i;
        }
        return -1;
    }

    /** First jump without a position at or after {@code from}, wrapping around; null if all are placed. */
    private static Jump firstUnplaced(List<Jump> jumps, int from) {
        int n = jumps.size();
        for (int k = 0; k < n; k++) {
            Jump jump = jumps.get((from + k) % n);
            if (jump != null && !isPlaced(jump)) return jump;
        }
        return null;
    }

    // -------------------------
    // Placing
    // -------------------------

    /** Puts the current jump at the player's strategy position, then moves on to the next unplaced jump. */
    public static void place() {
        // A short cooldown against a double click placing two jumps at the same spot.
        long now = System.currentTimeMillis();
        if (now - lastPlaceMs < PLACE_COOLDOWN_MS) return;

        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        Jump jump = current();
        ParkourMap map = map();
        if (player == null || jump == null || map == null) {
            ChatUtils.warning("Template mode: this map has no jumps.");
            return;
        }

        lastPlaceMs = now;
        jump.setX(PlayerBlockPos.x(player));
        jump.setY(PlayerBlockPos.jumpY(player));
        jump.setZ(PlayerBlockPos.z(player));
        DataStoreRepository.save();

        ChatUtils.info("Placed " + jump.getId() + " at " + jump.getX() + " " + jump.getY() + " " + jump.getZ() + ".");

        int index = indexOf(map.getJumps(), jump);
        Jump next = firstUnplaced(map.getJumps(), index + 1);
        if (next != null) {
            setCurrent(next);
        } else {
            setCurrent(jump);
            ChatUtils.info("All jumps of this map are placed.");
        }
    }

    // -------------------------
    // Sections
    // -------------------------

    /** Name of the innermost section the jump is in, or "" if it is in none. */
    public static String sectionName(ParkourMap map, int jumpIndex) {
        MapSection section = innermostSection(map, jumpIndex);
        return section != null ? section.getName() : "";
    }

    /** Name of the section the player chose to see under the crosshair (see Section indicator), or "". */
    public static String indicatorSectionName(ParkourMap map, int jumpIndex) {
        int level = UserPreferences.getSectionIndicatorLevel();
        if (level == 0) return sectionName(map, jumpIndex);
        if (map == null || level > map.getMaxSectionLevelsAllowed()) return "";

        MapSection section = SectionLayoutService.findSectionContainingIndex(map.getSectionsForLevel(level), jumpIndex);
        return section != null ? section.getName() : "";
    }

    private static MapSection innermostSection(ParkourMap map, int jumpIndex) {
        if (map == null) return null;
        for (int level = map.getMaxSectionLevelsAllowed(); level >= 1; level--) {
            MapSection section = SectionLayoutService.findSectionContainingIndex(map.getSectionsForLevel(level), jumpIndex);
            if (section != null) return section;
        }
        return null;
    }

    // -------------------------
    // Navigation (strategy editor)
    // -------------------------

    /** Name of what the editor currently targets: the jump itself, or the section of the chosen level it is in. */
    public static String navTargetName() {
        ParkourMap map = map();
        Jump jump = current();
        if (map == null || jump == null) return "";

        int level = getNavLevel();
        if (level == 0) return jump.getId() != null ? jump.getId() : "";

        MapSection section = SectionLayoutService.findSectionContainingIndex(map.getSectionsForLevel(level), currentIndex());
        return section != null ? section.getName() : "(no section)";
    }

    public static int getNavLevel() {
        ParkourMap map = map();
        int max = map != null ? map.getMaxSectionLevelsAllowed() : 0;
        return Math.max(0, Math.min(max, navLevel));
    }

    public static void setNavLevel(int level) {
        navLevel = Math.max(0, level);
    }

    /** Moves the current jump one jump, or one section of the chosen level, forward (1) or back (-1). */
    public static void step(int direction) {
        ParkourMap map = map();
        Jump jump = current();
        if (map == null || jump == null) return;

        List<Jump> jumps = map.getJumps();
        int index = indexOf(jumps, jump);
        int level = getNavLevel();
        int target = index;

        if (level == 0) {
            target = index + direction;
        } else {
            target = sectionTarget(map, level, index, direction);
        }

        target = Math.max(0, Math.min(jumps.size() - 1, target));
        setCurrent(jumps.get(target));
    }

    /** Start of the next section of the level, or for "back" the start of the current / previous one. */
    private static int sectionTarget(ParkourMap map, int level, int index, int direction) {
        ArrayList<MapSection> sorted = new ArrayList<MapSection>();
        for (MapSection s : map.getSectionsForLevel(level)) {
            if (s != null && s.getStartJumpIndex() <= s.getEndJumpIndex()) sorted.add(s);
        }
        sorted.sort((a, b) -> Integer.compare(a.getStartJumpIndex(), b.getStartJumpIndex()));

        if (direction > 0) {
            for (MapSection s : sorted) {
                if (s.getStartJumpIndex() > index) return s.getStartJumpIndex();
            }
            return index;
        }

        int best = index;
        for (MapSection s : sorted) {
            if (s.getStartJumpIndex() < index) best = s.getStartJumpIndex();
        }
        return best;
    }
}
