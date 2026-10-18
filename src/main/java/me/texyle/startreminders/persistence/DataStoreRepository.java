package me.texyle.startreminders.persistence;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import me.texyle.startreminders.data.DataStore;
import me.texyle.startreminders.data.Jump;
import me.texyle.startreminders.data.ParkourMap;
import me.texyle.startreminders.data.ServerProfile;
import me.texyle.startreminders.reminders.Reminder;

/**
 * Owns the three always-loaded data stores (global, restored, shared) and their JSON files.
 */
public final class DataStoreRepository {

	private static final String GLOBAL_FILE_PATH = StoragePaths.DATASET_DIR + "/global.json";
	private static final String RESTORED_FILE_PATH = StoragePaths.DATASET_DIR + "/restored_strats.json";
	private static final String SHARED_FILE_PATH = StoragePaths.DATASET_DIR + "/shared.json";

	/** One store plus the file backing it. */
	private static final class StoreSlot {
		final String path;
		/** Built-in server/map that must always exist in this store, or null for none. */
		final String requiredServerId;
		final String requiredMapId;

		DataStore store;
		boolean loaded;

		StoreSlot(String path, String requiredServerId, String requiredMapId) {
			this.path = path;
			this.requiredServerId = requiredServerId;
			this.requiredMapId = requiredMapId;
		}
	}

	private static final StoreSlot GLOBAL = new StoreSlot(GLOBAL_FILE_PATH,
			BuiltinContainers.GLOBAL_SERVER_ID, BuiltinContainers.GLOBAL_MAP_ID);
	private static final StoreSlot RESTORED = new StoreSlot(RESTORED_FILE_PATH,
			BuiltinContainers.RESTORED_SERVER_ID, BuiltinContainers.RESTORED_MAP_ID);
	private static final StoreSlot SHARED = new StoreSlot(SHARED_FILE_PATH, null, null);

	private DataStoreRepository() {}

	// -------------------------
	// Access
	// -------------------------

	public static DataStore getGlobalStore() {
		return GLOBAL.store;
	}

	public static DataStore getRestoredStore() {
		return RESTORED.store;
	}

	public static DataStore getSharedStore() {
		return SHARED.store;
	}

	public static ServerProfile findServerInStore(DataStore store, String id) {
		if (store == null || id == null) return null;
		if (store.getServers() == null) return null;

		for (ServerProfile s : store.getServers()) {
			if (s != null && id.equals(s.getId())) return s;
		}
		return null;
	}

	public static ParkourMap findMapInServer(ServerProfile server, String id) {
		if (server == null || id == null) return null;
		if (server.getMaps() == null) return null;

		for (ParkourMap m : server.getMaps()) {
			if (m != null && id.equals(m.getId())) return m;
		}
		return null;
	}

	// -------------------------
	// Loading
	// -------------------------

	public static void ensureLoaded() {
		ensureLoaded(GLOBAL);
		ensureLoaded(RESTORED);
		ensureLoaded(SHARED);
	}

	/** Re-creates the built-in Global/RestoredStrats containers if they are missing. */
	public static void ensureBuiltinStructure() {
		ensureBuiltinStructure(GLOBAL);
		ensureBuiltinStructure(RESTORED);
	}

	private static void ensureLoaded(StoreSlot slot) {
		if (slot.loaded && slot.store != null) {
			return;
		}

		if (slot.store == null) {
			slot.store = newEmptyStore();
		}
		if (slot.store.getServers() == null) {
			slot.store.setServers(new ArrayList<ServerProfile>());
		}

		boolean needsSave = false;

		try {
			File file = new File(slot.path);
			File parent = file.getParentFile();
			if (parent != null && !parent.exists()) {
				parent.mkdirs();
			}

			boolean created = file.createNewFile();
			if (created) {
				needsSave = true;
			} else {
				DataStore loadedStore = readStore(file);

				if (loadedStore != null && loadedStore.getServers() != null) {
					slot.store = loadedStore;
				} else {
					slot.store = newEmptyStore();
					needsSave = true;
				}
			}

		} catch (IOException ex) {
			ex.printStackTrace();
			needsSave = true;
		}

		if (normalizeStore(slot.store)) {
			needsSave = true;
		}

		if (ensureBuiltinStructure(slot)) {
			needsSave = true;
		}

		if (needsSave) {
			write(slot);
		}

		slot.loaded = true;
	}

	private static DataStore readStore(File file) throws IOException {
		Reader reader = new FileReader(file);
		try {
			return new Gson().fromJson(reader, DataStore.class);
		} catch (JsonSyntaxException ignored) {
			return null;
		} finally {
			reader.close();
		}
	}

	private static DataStore newEmptyStore() {
		DataStore store = new DataStore();
		store.setServers(new ArrayList<ServerProfile>());
		return store;
	}

	// -------------------------
	// Saving
	// -------------------------

	public static void save() {
		ensureLoaded();

		write(GLOBAL);
		write(RESTORED);
		write(SHARED);
	}

	private static void write(StoreSlot slot) {
		if (slot.store == null || slot.path == null) {
			return;
		}

		String jsonString = new GsonBuilder().setPrettyPrinting().create().toJson(slot.store);

		try {
			File file = new File(slot.path);
			File parent = file.getParentFile();
			if (parent != null && !parent.exists()) {
				parent.mkdirs();
			}
			file.createNewFile();

			FileWriter fw = new FileWriter(slot.path);
			try {
				fw.write(jsonString);
			} finally {
				fw.close();
			}

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// -------------------------
	// Structure / normalization
	// -------------------------

	/** @return true if anything had to be created. */
	private static boolean ensureBuiltinStructure(StoreSlot slot) {
		if (slot.requiredServerId == null) {
			return false;
		}

		boolean changed = false;

		if (slot.store == null) {
			slot.store = newEmptyStore();
			changed = true;
		}
		if (slot.store.getServers() == null) {
			slot.store.setServers(new ArrayList<ServerProfile>());
			changed = true;
		}

		ServerProfile server = findServerInStore(slot.store, slot.requiredServerId);
		if (server == null) {
			server = new ServerProfile();
			server.setId(slot.requiredServerId);
			server.setMaps(new ArrayList<ParkourMap>());
			slot.store.getServers().add(server);
			changed = true;
		}

		if (server.getMaps() == null) {
			server.setMaps(new ArrayList<ParkourMap>());
			changed = true;
		}

		ParkourMap map = findMapInServer(server, slot.requiredMapId);
		if (map == null) {
			map = new ParkourMap();
			map.setId(slot.requiredMapId);
			map.setJumps(new ArrayList<Jump>());
			server.getMaps().add(map);
			changed = true;
		}

		if (map.getJumps() == null) {
			map.setJumps(new ArrayList<Jump>());
			changed = true;
		}

		return changed;
	}

	/** Fills in null collections / invalid indices left by older save files. @return true if changed. */
	private static boolean normalizeStore(DataStore store) {
		boolean changed = false;

		if (store == null) return false;

		if (store.getServers() == null) {
			store.setServers(new ArrayList<ServerProfile>());
			changed = true;
		}

		for (ServerProfile s : store.getServers()) {
			if (s == null) continue;
			if (s.getMaps() == null) {
				s.setMaps(new ArrayList<ParkourMap>());
				changed = true;
			}

			for (ParkourMap m : s.getMaps()) {
				if (m == null) continue;
				if (m.getJumps() == null) {
					m.setJumps(new ArrayList<Jump>());
					changed = true;
				}

				for (Jump j : m.getJumps()) {
					if (j == null) continue;

					if (j.getReminders() == null) {
						j.setReminders(new ArrayList<Reminder>());
						changed = true;
					}

					boolean isRestoredStratsContext =
							BuiltinContainers.RESTORED_SERVER_ID.equals(s.getId()) ||
									BuiltinContainers.RESTORED_MAP_ID.equals(m.getId());

					// Ensure explicit persisted value (no fallback reliance).
					if (j.ensureShowJumpNameInWorldInitialized(isRestoredStratsContext)) {
						changed = true;
					}

					int idx = j.getActiveReminderIndex();
					ArrayList<Reminder> rs = j.getReminders();
					if (rs == null || rs.isEmpty()) {
						if (j.getActiveReminderIndex() != -1) {
							j.setActiveReminderIndex(-1);
							changed = true;
						}
					} else {
						if (idx < -1 || idx >= rs.size()) {
							j.setActiveReminderIndex(-1);
							changed = true;
						}
					}
				}
			}
		}

		return changed;
	}
}
