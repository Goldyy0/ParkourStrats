package me.texyle.startreminders.ui;

import me.texyle.startreminders.data.ParkourMap;
import me.texyle.startreminders.data.ServerProfile;
import me.texyle.startreminders.persistence.BuiltinContainers;

/** Text colors that mark the two special containers: Global (lime) and RestoredStrats (aqua). */
final class SpecialColors {

    static final int GLOBAL = 0xFF55FF55;
    static final int RESTORED = 0xFF55FFFF;

    private SpecialColors() {}

    /** @return the color, or 0 for an ordinary server */
    static int of(ServerProfile server) {
        if (BuiltinContainers.isGlobalServer(server)) return GLOBAL;
        if (BuiltinContainers.isRestoredServer(server)) return RESTORED;
        return 0;
    }

    /** @return the color, or 0 for an ordinary map */
    static int of(ParkourMap map) {
        if (BuiltinContainers.isGlobalMap(map)) return GLOBAL;
        if (BuiltinContainers.isRestoredMap(map)) return RESTORED;
        return 0;
    }
}
