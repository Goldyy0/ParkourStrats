package me.texyle.startreminders.reminders;

import java.util.ArrayList;

import me.texyle.startreminders.data.Jump;
import me.texyle.startreminders.data.ParkourMap;
import me.texyle.startreminders.data.ServerProfile;
import me.texyle.startreminders.persistence.DataStoreRepository;
import me.texyle.startreminders.services.ServerService;
import me.texyle.startreminders.services.StrategyService;

/** Picks which server / map / jump is selected when nothing was chosen explicitly (world join, startup). */
public final class DefaultContextSelector {

	private DefaultContextSelector() {}

	public static void autoSelectDefaultContext() {
		DataStoreRepository.ensureLoaded();
		DataStoreRepository.ensureBuiltinStructure();

		ServerProfile bestS = null;
		ParkourMap bestM = null;
		Jump bestJ = null;

		for (ServerProfile s : ServerService.getServers()) {
			if (s == null || s.getMaps() == null) continue;

			for (ParkourMap m : s.getMaps()) {
				if (m == null || m.getJumps() == null) continue;

				for (Jump j : m.getJumps()) {
					if (j == null) continue;

					ArrayList<Reminder> rs = j.getReminders();
					if (rs == null || rs.isEmpty()) continue;

					int idx = j.getActiveReminderIndex();
					if (idx >= 0 && idx < rs.size()) {
						bestS = s;
						bestM = m;
						bestJ = j;
						break;
					}

					if (bestJ == null) {
						bestS = s;
						bestM = m;
						bestJ = j;
					}
				}
			}
		}

		if (bestS != null) SelectionState.setSelectedServer(bestS);
		if (bestM != null) SelectionState.setSelectedMap(bestM);
		if (bestJ != null) SelectionState.setSelectedJump(bestJ);

		if (SelectionState.getSelectedJump() != null) {
			StrategyService.getActiveReminderOrFallback(SelectionState.getSelectedJump());
		}
	}

}
