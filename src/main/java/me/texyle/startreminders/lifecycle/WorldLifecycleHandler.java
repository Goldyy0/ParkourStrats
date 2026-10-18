package me.texyle.startreminders.lifecycle;

import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

import me.texyle.startreminders.persistence.DataStoreRepository;
import me.texyle.startreminders.reminders.DefaultContextSelector;
import me.texyle.startreminders.reminders.SelectionState;

/** Selects a default context when a world/server is joined and saves data when it is left. */
public class WorldLifecycleHandler {

	@SubscribeEvent
	public void onWorldLoad(WorldEvent.Load event) {
		DataStoreRepository.ensureLoaded();
		DefaultContextSelector.autoSelectDefaultContext();
	}

	@SubscribeEvent
	public void onWorldUnload(WorldEvent.Unload event) {
		saveCurrentStoresBestEffort();

		SelectionState.clearAll();
	}

	@SubscribeEvent
	public void onClientConnected(FMLNetworkEvent.ClientConnectedToServerEvent event) {
		DataStoreRepository.ensureLoaded();
		DefaultContextSelector.autoSelectDefaultContext();
	}

	@SubscribeEvent
	public void onClientDisconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
		saveCurrentStoresBestEffort();

		SelectionState.clearAll();
	}

	private static void saveCurrentStoresBestEffort() {
		try {
			DataStoreRepository.save();
		} catch (Exception ignored) {
		}
	}

}
