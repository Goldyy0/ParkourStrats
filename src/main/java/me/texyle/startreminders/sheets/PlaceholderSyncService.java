package me.texyle.startreminders.sheets;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;

import me.texyle.startreminders.StratReminders;
import me.texyle.startreminders.data.ParkourMap;
import me.texyle.startreminders.persistence.DataStoreRepository;
import me.texyle.startreminders.reminders.SelectionState;
import me.texyle.startreminders.sheets.SheetJumpNameExtractor;
import me.texyle.startreminders.sheets.SheetSyncManager;

/** Placeholder pager: feeds jump names (and sections) from a sheet into the "create jump" screen. */
public final class PlaceholderSyncService {

	private PlaceholderSyncService() {}

	public interface IPlaceholderJumpSyncCallback {
		void onSuccess(int count);
		void onError(String errorMessage);
	}

	private static final String PLACEHOLDER_SHEETS_CACHE_FILE = "StratReminders/sheets_cache/placeholder_jumps.csv";

	// Runtime cache: we persist only URL/index/enabled in ParkourMap; the list is kept in memory.
	private static java.util.List<String> placeholderJumpNames = new java.util.ArrayList<String>();

	// Runtime cache: sheet-driven Level 1 sections for placeholder pager
	private static java.util.List<SheetJumpNameExtractor.PlaceholderSection> placeholderSectionsL1
			= new java.util.ArrayList<SheetJumpNameExtractor.PlaceholderSection>();

	public static boolean isAnySheetSyncInProgress() {
		return SheetSyncManager.isSyncInProgress();
	}

	public static boolean isPlaceholderSyncEnabledForSelectedMap() {
		DataStoreRepository.ensureLoaded();
		ParkourMap map = SelectionState.getSelectedMap();
		return map != null && map.isPlaceholderSyncEnabled() && placeholderJumpNames != null && !placeholderJumpNames.isEmpty();
	}

	public static int getPlaceholderJumpCount() {
		if (placeholderJumpNames == null) return 0;
		return placeholderJumpNames.size();
	}

	public static int getPlaceholderJumpIndex() {
		DataStoreRepository.ensureLoaded();
		ParkourMap map = SelectionState.getSelectedMap();
		if (map == null) return 0;

		int idx = map.getPlaceholderJumpIndex();
		int count = getPlaceholderJumpCount();
		if (count <= 0) return 0;

		if (idx < 0) idx = 0;
		if (idx >= count) idx = count - 1;
		map.setPlaceholderJumpIndex(idx);
		return idx;
	}

	public static String getCurrentPlaceholderJumpName() {
		if (!isPlaceholderSyncEnabledForSelectedMap()) return "";
		int idx = getPlaceholderJumpIndex();
		if (placeholderJumpNames == null || placeholderJumpNames.isEmpty()) return "";
		if (idx < 0 || idx >= placeholderJumpNames.size()) return "";
		String s = placeholderJumpNames.get(idx);
		return s != null ? s : "";
	}

	public static int getCurrentPlaceholderSectionIndex() {
		if (placeholderSectionsL1 == null || placeholderSectionsL1.isEmpty()) return -1;

		int jumpIdx = getPlaceholderJumpIndex(); // clamps + persists map index
		int best = -1;

		// 1) Exact section containing the current jump index
		for (int i = 0; i < placeholderSectionsL1.size(); i++) {
			SheetJumpNameExtractor.PlaceholderSection s = placeholderSectionsL1.get(i);
			if (s == null) continue;

			int a = s.startJumpIndex;
			int b = s.endJumpIndex;

			if (a <= jumpIdx && jumpIdx <= b) {
				return i;
			}
		}

		// 2) Fallback: nearest previous section by start index
		int bestStart = Integer.MIN_VALUE;
		for (int i = 0; i < placeholderSectionsL1.size(); i++) {
			SheetJumpNameExtractor.PlaceholderSection s = placeholderSectionsL1.get(i);
			if (s == null) continue;

			int a = s.startJumpIndex;
			if (a <= jumpIdx && a > bestStart) {
				bestStart = a;
				best = i;
			}
		}

		// If jumpIdx is before the first section start, best stays -1 -> return 0 (safe default)
		if (best < 0) return 0;
		return best;
	}

	public static String getCurrentPlaceholderSectionName() {
		int idx = getCurrentPlaceholderSectionIndex();
		if (idx < 0) return "";

		if (placeholderSectionsL1 == null || idx >= placeholderSectionsL1.size()) return "";

		SheetJumpNameExtractor.PlaceholderSection s = placeholderSectionsL1.get(idx);
		if (s == null) return "";

		String nm = s.name;
		return nm != null ? nm : "";
	}

	public static void setPlaceholderJumpIndex(int idx) {
		DataStoreRepository.ensureLoaded();
		ParkourMap map = SelectionState.getSelectedMap();
		if (map == null) return;
		if (!map.isPlaceholderSyncEnabled()) return;

		int count = getPlaceholderJumpCount();
		if (count <= 0) return;

		int clamped = idx;
		if (clamped < 0) clamped = 0;
		if (clamped >= count) clamped = count - 1;

		map.setPlaceholderJumpIndex(clamped);
		DataStoreRepository.save();
	}

	/** Moves the pager by delta jumps, staying within the list. Does nothing when placeholder sync is off. */
	private static void stepJump(int delta) {
		ParkourMap map = SelectionState.getSelectedMap();
		if (map == null || !map.isPlaceholderSyncEnabled() || getPlaceholderJumpCount() <= 0) return;

		setPlaceholderJumpIndex(getPlaceholderJumpIndex() + delta);
	}

	public static void prevPlaceholderJump() {
		stepJump(-1);
	}

	public static void nextPlaceholderJump() {
		stepJump(1);
	}

	/**
	 * Called after "Create placeholder" is used, so next time the GUI opens
	 * the next jump name is prefilled.
	 */
	public static void advancePlaceholderAfterCreate() {
		stepJump(1);
	}

	/**
	 * Enables placeholder sync for a given map and persists the URL/index/enabled state.
	 * The actual list is fetched by requestPlaceholderJumpListSync(...).
	 */
	public static void enablePlaceholderSyncForMap(ParkourMap map, String url) {
		DataStoreRepository.ensureLoaded();
		if (map == null) return;

		String trimmed = url != null ? url.trim() : "";
		map.setPlaceholderSheetUrl(trimmed);
		map.setPlaceholderSyncEnabled(true);

		// Keep index within range later when list is loaded
		if (map.getPlaceholderJumpIndex() < 0) {
			map.setPlaceholderJumpIndex(0);
		}

		DataStoreRepository.save();
	}

	public static void disablePlaceholderSyncForMap(ParkourMap map) {
		DataStoreRepository.ensureLoaded();
		if (map == null) return;

		map.setPlaceholderSyncEnabled(false);
		map.setPlaceholderSheetUrl("");
		map.setPlaceholderJumpIndex(0);

		placeholderJumpNames = new java.util.ArrayList<String>();
		placeholderSectionsL1 = new java.util.ArrayList<SheetJumpNameExtractor.PlaceholderSection>();

		DataStoreRepository.save();
	}

	public static int getPlaceholderSectionCount() {
		return placeholderSectionsL1 != null ? placeholderSectionsL1.size() : 0;
	}

	public static void jumpToPlaceholderSection(int sectionIndex) {
		DataStoreRepository.ensureLoaded();
		ParkourMap map = SelectionState.getSelectedMap();
		if (map == null) return;

		// Keep behavior consistent with other placeholder navigation.
		if (!map.isPlaceholderSyncEnabled()) return;

		if (placeholderSectionsL1 == null || placeholderSectionsL1.isEmpty()) return;

		int count = getPlaceholderJumpCount();
		if (count <= 0) return;

		int idx = sectionIndex;
		if (idx < 0) idx = 0;
		if (idx >= placeholderSectionsL1.size()) idx = placeholderSectionsL1.size() - 1;

		SheetJumpNameExtractor.PlaceholderSection s = placeholderSectionsL1.get(idx);
		if (s == null) return;

		// Delegate clamping + persistence to the canonical setter.
		setPlaceholderJumpIndex(s.startJumpIndex);
	}

	/**
	 * Downloads CSV from placeholderSheetUrl and extracts jump names in sheet order.
	 * Stores the list in runtime cache and keeps map index clamped.
	 */
	public static void requestPlaceholderJumpListSync(ParkourMap map, IPlaceholderJumpSyncCallback cb) {
		DataStoreRepository.ensureLoaded();

		if (map == null) {
			if (cb != null) cb.onError("No map selected.");
			return;
		}

		if (!map.isPlaceholderSyncEnabled()) {
			if (cb != null) cb.onError("Placeholder sync is not enabled.");
			return;
		}

		final String url = map.getPlaceholderSheetUrl();
		if (url == null || url.trim().isEmpty()) {
			if (cb != null) cb.onError("No placeholder sheet URL configured.");
			return;
		}

		if (SheetSyncManager.isSyncInProgress()) {
			if (cb != null) cb.onError("Sync already in progress.");
			return;
		}

		// forcedGid = -1 (SheetUrlUtils can derive gid if present; otherwise export default)
		SheetSyncManager.fetchCsvAsync(url, -1, PLACEHOLDER_SHEETS_CACHE_FILE, new SheetSyncManager.ISyncCallback() {
			@Override
			public void onSuccess(String csvText, String cachePath) {
				Minecraft.getMinecraft().addScheduledTask(new Runnable() {
					@Override
					public void run() {
						try {
							SheetJumpNameExtractor.Result res = SheetJumpNameExtractor.extract(csvText);
							if (res == null || res.jumpNames == null || res.jumpNames.isEmpty()) {
								if (cb != null) cb.onError("No jump names found in CSV.");
								return;
							}

							placeholderJumpNames = new java.util.ArrayList<String>(res.jumpNames);

							// Store sheet-driven level-1 sections for placeholder pager
							if (res.sectionsL1 != null) {
								placeholderSectionsL1 = new java.util.ArrayList<SheetJumpNameExtractor.PlaceholderSection>(res.sectionsL1);
							} else {
								placeholderSectionsL1 = new java.util.ArrayList<SheetJumpNameExtractor.PlaceholderSection>();
							}

							// Clamp saved index
							int idx = map.getPlaceholderJumpIndex();
							if (idx < 0) idx = 0;
							if (idx >= placeholderJumpNames.size()) idx = placeholderJumpNames.size() - 1;
							map.setPlaceholderJumpIndex(idx);
							DataStoreRepository.save();

							if (cb != null) cb.onSuccess(placeholderJumpNames.size());
						} catch (Exception ex) {
							if (cb != null) cb.onError("Failed to parse CSV.");
						}
					}
				});
			}

			@Override
			public void onError(String errorMessage) {
				Minecraft.getMinecraft().addScheduledTask(new Runnable() {
					@Override
					public void run() {
						if (cb != null) cb.onError(errorMessage != null ? errorMessage : "Failed to download CSV.");
					}
				});
			}
		});
	}

}
