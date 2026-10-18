package me.texyle.startreminders.services;

import java.util.ArrayList;

import me.texyle.startreminders.data.ParkourMap;
import me.texyle.startreminders.data.ServerProfile;
import me.texyle.startreminders.persistence.BuiltinContainers;
import me.texyle.startreminders.persistence.DataStoreRepository;
import me.texyle.startreminders.reminders.SelectionState;
import me.texyle.startreminders.utils.IdRules;

/** Lists, creates, renames and removes servers. Global and RestoredStrats are fixed and protected. */
public final class ServerService {

	private ServerService() {}

	public static ArrayList<ServerProfile> getServers() {
		DataStoreRepository.ensureLoaded();
		DataStoreRepository.ensureBuiltinStructure();

		ArrayList<ServerProfile> out = new ArrayList<ServerProfile>();

		ServerProfile g = DataStoreRepository.findServerInStore(DataStoreRepository.getGlobalStore(), BuiltinContainers.GLOBAL_SERVER_ID);
		if (g != null) {
			out.add(g);
		}

		ServerProfile r = DataStoreRepository.findServerInStore(DataStoreRepository.getRestoredStore(), BuiltinContainers.RESTORED_SERVER_ID);
		if (r != null) {
			out.add(r);
		}

		if (DataStoreRepository.getSharedStore() != null && DataStoreRepository.getSharedStore().getServers() != null) {
			for (ServerProfile s : DataStoreRepository.getSharedStore().getServers()) {
				if (s == null) continue;
				if (BuiltinContainers.GLOBAL_SERVER_ID.equals(s.getId())) continue;
				if (BuiltinContainers.RESTORED_SERVER_ID.equals(s.getId())) continue;
				out.add(s);
			}
		}

		return out;
	}

	public static boolean createServer(String id) {
		DataStoreRepository.ensureLoaded();
		if (!IdRules.isValidId(id)) return false;

		String trimmed = id.trim();

		if (BuiltinContainers.GLOBAL_SERVER_ID.equals(trimmed)) return false;
		if (BuiltinContainers.RESTORED_SERVER_ID.equals(trimmed)) return false;

		if (findServerById(trimmed) != null) return false;

		if (DataStoreRepository.getSharedStore().getServers() == null) {
			DataStoreRepository.getSharedStore().setServers(new ArrayList<ServerProfile>());
		}

		ServerProfile s = new ServerProfile();
		s.setId(trimmed);
		s.setMaps(new ArrayList<ParkourMap>());
		DataStoreRepository.getSharedStore().getServers().add(s);

		DataStoreRepository.save();
		return true;
	}

	public static boolean renameServer(ServerProfile server, String newId) {
		DataStoreRepository.ensureLoaded();
		if (server == null) return false;

		if (BuiltinContainers.isProtectedServer(server)) {
			return false;
		}

		if (!IdRules.isValidId(newId)) return false;

		String trimmed = newId.trim();
		if (BuiltinContainers.GLOBAL_SERVER_ID.equals(trimmed)) return false;
		if (BuiltinContainers.RESTORED_SERVER_ID.equals(trimmed)) return false;

		ServerProfile existing = findServerById(trimmed);
		if (existing != null && existing != server) return false;

		server.setId(trimmed);
		DataStoreRepository.save();
		return true;
	}

	public static boolean removeServer(ServerProfile server) {
		DataStoreRepository.ensureLoaded();
		if (server == null) return false;

		if (BuiltinContainers.isProtectedServer(server)) {
			return false;
		}

		if (DataStoreRepository.getSharedStore().getServers() == null) {
			DataStoreRepository.getSharedStore().setServers(new ArrayList<ServerProfile>());
		}

		boolean removed = DataStoreRepository.getSharedStore().getServers().remove(server);
		if (removed) {
			SelectionState.clearServerIfSelected(server);
			DataStoreRepository.save();
		}
		return removed;
	}

	public static ServerProfile findServerById(String id) {
		DataStoreRepository.ensureLoaded();
		if (id == null) return null;

		ServerProfile g = DataStoreRepository.findServerInStore(DataStoreRepository.getGlobalStore(), id);
		if (g != null) return g;

		ServerProfile r = DataStoreRepository.findServerInStore(DataStoreRepository.getRestoredStore(), id);
		if (r != null) return r;

		return DataStoreRepository.findServerInStore(DataStoreRepository.getSharedStore(), id);
	}

	public static ServerProfile findOwningServerForMap(ParkourMap map) {
		if (map == null) return null;

		for (ServerProfile s : getServers()) {
			if (s == null || s.getMaps() == null) continue;

			for (ParkourMap m : s.getMaps()) {
				if (m == map) {
					return s;
				}

				// Fallback: same id reference match (safer when deserialized)
				if (m != null && map.getId() != null && map.getId().equals(m.getId())) {
					// Only accept if this server actually contains the map object by id
					// (this is a best-effort fallback)
					return s;
				}
			}
		}
		return null;
	}

}
