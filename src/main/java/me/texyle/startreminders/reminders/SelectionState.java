package me.texyle.startreminders.reminders;

import java.util.ArrayList;

import me.texyle.startreminders.data.Jump;
import me.texyle.startreminders.data.ParkourMap;
import me.texyle.startreminders.data.ServerProfile;
import me.texyle.startreminders.persistence.BuiltinContainers;
import me.texyle.startreminders.persistence.DataStoreRepository;

/**
 * The server / map / jump the player currently works with.
 * Selecting a parent never keeps a child from another parent: clearing a server clears its map and jump too.
 */
public final class SelectionState {

	private static ServerProfile selectedServer;
	private static ParkourMap selectedMap;
	private static Jump selectedJump;

	private SelectionState() {}

	public static ServerProfile getSelectedServer() {
		DataStoreRepository.ensureLoaded();
		return selectedServer;
	}

	public static ParkourMap getSelectedMap() {
		DataStoreRepository.ensureLoaded();
		return selectedMap;
	}

	public static Jump getSelectedJump() {
		DataStoreRepository.ensureLoaded();
		return selectedJump;
	}

	public static void setSelectedServer(ServerProfile server) {
		DataStoreRepository.ensureLoaded();

		selectedServer = server;

		if (selectedServer == null) {
			selectedMap = null;
			selectedJump = null;
			return;
		}

		if (selectedServer.getMaps() == null) {
			selectedServer.setMaps(new ArrayList<ParkourMap>());
		}
	}

	public static void setSelectedMap(ParkourMap map) {
		DataStoreRepository.ensureLoaded();

		selectedMap = map;

		if (selectedMap == null) {
			selectedJump = null;
			return;
		}

		if (selectedMap.getJumps() == null) {
			selectedMap.setJumps(new ArrayList<Jump>());
		}
	}

	public static void setSelectedJump(Jump jump) {
		DataStoreRepository.ensureLoaded();
		selectedJump = jump;
		if (selectedJump != null && selectedJump.getReminders() == null) {
			selectedJump.setReminders(new ArrayList<Reminder>());
		}
	}

	/** Replaces the whole selection at once without the per-level initialisation of the setters. */
	public static void select(ServerProfile server, ParkourMap map, Jump jump) {
		selectedServer = server;
		selectedMap = map;
		selectedJump = jump;
	}

	public static void clearAll() {
		selectedServer = null;
		selectedMap = null;
		selectedJump = null;
	}

	public static void clearJumpIfSelected(Jump jump) {
		if (selectedJump == jump) {
			selectedJump = null;
		}
	}

	public static void clearMapIfSelected(ParkourMap map) {
		if (selectedMap == map) {
			selectedMap = null;
			selectedJump = null;
		}
	}

	public static void clearServerIfSelected(ServerProfile server) {
		if (selectedServer == server) {
			clearAll();
		}
	}

	/** True if the selected server or map is the RestoredStrats container. */
	public static boolean isRestoredContext() {
		return BuiltinContainers.isRestoredServer(getSelectedServer()) || BuiltinContainers.isRestoredMap(getSelectedMap());
	}
}
