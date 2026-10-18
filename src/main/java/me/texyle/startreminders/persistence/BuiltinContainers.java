package me.texyle.startreminders.persistence;

import me.texyle.startreminders.data.ParkourMap;
import me.texyle.startreminders.data.ServerProfile;

/**
 * Identity of the two fixed containers ("Global" and "RestoredStrats") that always exist
 * and can be neither renamed nor removed.
 */
public final class BuiltinContainers {

	public static final String GLOBAL_SERVER_ID = "Global";
	public static final String GLOBAL_MAP_ID = "Global";

	public static final String RESTORED_SERVER_ID = "RestoredStrats";
	public static final String RESTORED_MAP_ID = "RestoredStrats";

	private BuiltinContainers() {}

	public static ParkourMap getGlobalMap() {
		return findBuiltinMap(DataStoreRepository.getGlobalStore(), GLOBAL_SERVER_ID, GLOBAL_MAP_ID);
	}

	public static ParkourMap getRestoredMap() {
		return findBuiltinMap(DataStoreRepository.getRestoredStore(), RESTORED_SERVER_ID, RESTORED_MAP_ID);
	}

	/** The map a protected server is locked to (Global / RestoredStrats), or null for any other server. */
	public static ParkourMap getForcedMap(ServerProfile server) {
		if (isGlobalServer(server)) return getGlobalMap();
		if (isRestoredServer(server)) return getRestoredMap();
		return null;
	}

	public static boolean isGlobalServer(ServerProfile server) {
		return server != null && GLOBAL_SERVER_ID.equals(server.getId());
	}

	public static boolean isRestoredServer(ServerProfile server) {
		return server != null && RESTORED_SERVER_ID.equals(server.getId());
	}

	public static boolean isProtectedServer(ServerProfile server) {
		return isGlobalServer(server) || isRestoredServer(server);
	}

	public static boolean isGlobalMap(ParkourMap map) {
		if (map == null) return false;
		ParkourMap gm = getGlobalMap();
		return map == gm || (gm != null && GLOBAL_MAP_ID.equals(map.getId()));
	}

	public static boolean isRestoredMap(ParkourMap map) {
		if (map == null) return false;
		ParkourMap rm = getRestoredMap();
		return map == rm || (rm != null && RESTORED_MAP_ID.equals(map.getId()));
	}

	private static ParkourMap findBuiltinMap(me.texyle.startreminders.data.DataStore store, String serverId, String mapId) {
		DataStoreRepository.ensureLoaded();
		ServerProfile server = DataStoreRepository.findServerInStore(store, serverId);
		if (server == null || server.getMaps() == null) {
			return null;
		}
		return DataStoreRepository.findMapInServer(server, mapId);
	}
}
