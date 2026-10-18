package me.texyle.startreminders.services;

import java.util.ArrayList;

import me.texyle.startreminders.data.Jump;
import me.texyle.startreminders.data.ParkourMap;
import me.texyle.startreminders.data.ServerProfile;
import me.texyle.startreminders.persistence.BuiltinContainers;
import me.texyle.startreminders.persistence.DataStoreRepository;
import me.texyle.startreminders.reminders.SelectionState;
import me.texyle.startreminders.utils.IdRules;

/** Creates, configures, finds and removes maps of a server. */
public final class MapService {

	private MapService() {}

	public static ArrayList<ParkourMap> getMaps(ServerProfile server) {
		DataStoreRepository.ensureLoaded();
		if (server == null) return new ArrayList<ParkourMap>();
		if (server.getMaps() == null) server.setMaps(new ArrayList<ParkourMap>());
		return server.getMaps();
	}

	public static boolean createMap(ServerProfile server, String id) {
		// Backward-compatible overload: no sections by default.
		return createMap(server, id, false, 0, null);
	}

	public static boolean createMap(ServerProfile server, String id, boolean sectionsEnabled, int sectionsCount, String[] sectionNames) {
		DataStoreRepository.ensureLoaded();
		if (server == null) return false;

		if (BuiltinContainers.isProtectedServer(server)) return false;

		if (!IdRules.isValidId(id)) return false;
		if (findMapById(server, id) != null) return false;

		ParkourMap m = new ParkourMap();
		m.setId(id);
		m.setJumps(new ArrayList<Jump>());

		// Sections config (offline v1.6)
		m.setSectionsEnabled(sectionsEnabled);
		if (!sectionsEnabled) {
			m.setSectionsCountRaw(0);
		} else {
			// Force 1..MAX_SECTION_LEVELS
			int c = sectionsCount;
			if (c < 1) c = 1;
			if (c > ParkourMap.MAX_SECTION_LEVELS) c = ParkourMap.MAX_SECTION_LEVELS;
			m.setSectionsCountRaw(c);
		}
		if (sectionNames != null) {
			m.setSectionNames(sectionNames);
		}

		server.getMaps().add(m);

		DataStoreRepository.save();
		return true;
	}

	public static boolean updateMapConfig(ServerProfile server, ParkourMap map, String newId,
										  boolean sectionsEnabled, int sectionsCount, String[] sectionNames) {
		DataStoreRepository.ensureLoaded();
		if (server == null || map == null) return false;

		if (BuiltinContainers.isProtectedServer(server)) return false;

		String t = (newId != null) ? newId.trim() : "";
		if (!IdRules.isValidId(t)) return false;

		ParkourMap existing = findMapById(server, t);
		if (existing != null && existing != map) return false;

		// Apply id
		map.setId(t);

		// Apply sections config
		map.setSectionsEnabled(sectionsEnabled);

		if (!sectionsEnabled) {
			map.setSectionsCountRaw(0);
			// Keep names as-is or reset; keeping is fine (future re-enable).
		} else {
			int c = sectionsCount;
			if (c < 1) c = 1;
			if (c > ParkourMap.MAX_SECTION_LEVELS) c = ParkourMap.MAX_SECTION_LEVELS;
			map.setSectionsCountRaw(c);

			if (sectionNames != null) {
				map.setSectionNames(sectionNames);
			}
		}

		DataStoreRepository.save();
		return true;
	}

	public static boolean removeMap(ServerProfile server, ParkourMap map) {
		DataStoreRepository.ensureLoaded();
		if (server == null || map == null) return false;

		if (BuiltinContainers.isProtectedServer(server)) return false;

		boolean removed = server.getMaps().remove(map);
		if (removed) {
			SelectionState.clearMapIfSelected(map);
			DataStoreRepository.save();
		}
		return removed;
	}

	public static ParkourMap findMapById(ServerProfile server, String id) {
		DataStoreRepository.ensureLoaded();
		if (server == null || id == null) return null;
		if (server.getMaps() == null) server.setMaps(new ArrayList<ParkourMap>());

		for (ParkourMap m : server.getMaps()) {
			if (m != null && id.equals(m.getId())) return m;
		}
		return null;
	}

}
