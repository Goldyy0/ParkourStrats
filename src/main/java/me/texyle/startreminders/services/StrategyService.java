package me.texyle.startreminders.services;

import java.util.ArrayList;

import net.minecraft.client.Minecraft;
import net.minecraft.util.EnumChatFormatting;

import me.texyle.startreminders.data.DataStore;
import me.texyle.startreminders.data.Jump;
import me.texyle.startreminders.data.ParkourMap;
import me.texyle.startreminders.data.ServerProfile;
import me.texyle.startreminders.persistence.DataStoreRepository;
import me.texyle.startreminders.reminders.Reminder;
import me.texyle.startreminders.reminders.SelectionState;
import me.texyle.startreminders.utils.ChatUtils;

/** Operations on the strategies (reminders) of the currently selected jump. */
public final class StrategyService {

	private StrategyService() {}

	public static boolean hasAnyStrategies() {
		DataStoreRepository.ensureLoaded();
		return storeHasAnyStrategies(DataStoreRepository.getGlobalStore()) || storeHasAnyStrategies(DataStoreRepository.getRestoredStore()) || storeHasAnyStrategies(DataStoreRepository.getSharedStore());
	}

	private static boolean storeHasAnyStrategies(DataStore store) {
		if (store == null || store.getServers() == null) return false;

		for (ServerProfile s : store.getServers()) {
			if (s == null || s.getMaps() == null) continue;

			for (ParkourMap m : s.getMaps()) {
				if (m == null || m.getJumps() == null) continue;

				for (Jump j : m.getJumps()) {
					if (j == null) continue;

					ArrayList<Reminder> rs = j.getReminders();
					if (rs == null || rs.isEmpty()) continue;

					for (Reminder r : rs) {
						if (r == null || r.lines == null || r.lines.isEmpty()) continue;

						// At least one non-empty line counts as "strategy exists"
						for (String line : r.lines) {
							if (line != null && line.trim().length() > 0) {
								return true;
							}
						}
					}
				}
			}
		}

		return false;
	}

	public static Reminder getActiveReminderOrFallback(Jump jump) {
		if (jump == null) return null;

		ArrayList<Reminder> list = jump.getReminders();
		if (list == null || list.isEmpty()) {
			return null;
		}

		int idx = jump.getActiveReminderIndex();
		if (idx >= 0 && idx < list.size()) {
			return list.get(idx);
		}

		jump.setActiveReminderIndex(0);
		DataStoreRepository.save();

		return list.get(0);
	}

	public static void createReminder(ArrayList<String> lines, int x, int y, int z) {
		DataStoreRepository.ensureLoaded();

		Minecraft mc = Minecraft.getMinecraft();

		if (SelectionState.getSelectedJump() == null) {
			ChatUtils.sendChat(mc, EnumChatFormatting.RED + "No jump selected. Create/select a jump first.");
			return;
		}

		if (lines == null) {
			System.out.println("[ParkourStrats] ERROR: lines is null, aborting createReminder.");
			return;
		}

		// Keep the original negative coordinate correction behavior.
		// IMPORTANT: This method no longer adds +1 to Y internally.
		// All callers must already provide the correct Y (player block Y + 1).
		if (x < 0) x--;
		if (y < 0) y--;
		if (z < 0) z--;

		// Keep existing jump coords if already set (jump-level coords are the category key)
		boolean jumpHasCoords = !(SelectionState.getSelectedJump().getX() == 0 && SelectionState.getSelectedJump().getY() == 0 && SelectionState.getSelectedJump().getZ() == 0);
		if (!jumpHasCoords) {
			SelectionState.getSelectedJump().setX(x);
			SelectionState.getSelectedJump().setY(y);
			SelectionState.getSelectedJump().setZ(z);
		}

		// Only apply Position/Facing when using the new 8-line format.
		if (lines.size() >= 8) {
			SelectionState.getSelectedJump().setPosition(getLineSafe(lines, 0));
			SelectionState.getSelectedJump().setFacing(getLineSafe(lines, 1));
		}

		SelectionState.getSelectedJump().setSetup(getLineSafe(lines, 2));
		SelectionState.getSelectedJump().setStrategy(getLineSafe(lines, 3));
		SelectionState.getSelectedJump().setStrafe(getLineSafe(lines, 4));
		SelectionState.getSelectedJump().setTurn(getLineSafe(lines, 5));
		SelectionState.getSelectedJump().setAuthor(getLineSafe(lines, 6));
		SelectionState.getSelectedJump().setTips(getLineSafe(lines, 7));

		if (SelectionState.getSelectedJump().getReminders() == null) {
			SelectionState.getSelectedJump().setReminders(new ArrayList<Reminder>());
		}

		Reminder reminder = new Reminder(lines);
		SelectionState.getSelectedJump().getReminders().add(reminder);

		if (SelectionState.getSelectedJump().getActiveReminderIndex() < 0) {
			SelectionState.getSelectedJump().setActiveReminderIndex(0);
		}

		try {
			DataStoreRepository.save();
		} catch (Exception e) {
			System.out.println("[ParkourStrats] ERROR while saving data: " + e.getClass().getName() + " " + e.getMessage());
			e.printStackTrace();
		}

		String coordInfo;
		if (jumpHasCoords) {
			coordInfo = EnumChatFormatting.GRAY + "[" + SelectionState.getSelectedJump().getX() + ", " + SelectionState.getSelectedJump().getY() + ", " + SelectionState.getSelectedJump().getZ() + "]";
		} else {
			coordInfo = EnumChatFormatting.GRAY + "[" + x + ", " + y + ", " + z + "]";
		}

		String str = ChatUtils.INFO_PREFIX + "Strategy saved at jump coordinates: "
				+ coordInfo;

		ChatUtils.sendChat(mc, str);
	}

	public static void deleteReminder(Reminder rem) {
		DataStoreRepository.ensureLoaded();

		if (SelectionState.getSelectedJump() == null || SelectionState.getSelectedJump().getReminders() == null) {
			return;
		}

		int oldIndex = SelectionState.getSelectedJump().getReminders().indexOf(rem);

		if (rem != null) {
			SelectionState.getSelectedJump().getReminders().remove(rem);
		}

		ArrayList<Reminder> list = SelectionState.getSelectedJump().getReminders();
		if (list == null || list.isEmpty()) {
			SelectionState.getSelectedJump().setActiveReminderIndex(-1);
		} else {
			int active = SelectionState.getSelectedJump().getActiveReminderIndex();
			if (active == oldIndex) {
				SelectionState.getSelectedJump().setActiveReminderIndex(0);
			} else if (active > oldIndex) {
				SelectionState.getSelectedJump().setActiveReminderIndex(active - 1);
			} else if (active >= list.size()) {
				SelectionState.getSelectedJump().setActiveReminderIndex(0);
			}
		}

		DataStoreRepository.save();

		String str = ChatUtils.INFO_PREFIX + "Strategy deleted";

		Minecraft mc = Minecraft.getMinecraft();
		ChatUtils.sendChat(mc, str);
	}

	public static ArrayList<Reminder> getReminderList() {
		DataStoreRepository.ensureLoaded();
		if (SelectionState.getSelectedJump() == null || SelectionState.getSelectedJump().getReminders() == null) {
			return new ArrayList<Reminder>();
		}
		return SelectionState.getSelectedJump().getReminders();
	}

	public static void setActiveReminder(Reminder rem) {
		DataStoreRepository.ensureLoaded();
		if (SelectionState.getSelectedJump() == null || SelectionState.getSelectedJump().getReminders() == null || rem == null) {
			return;
		}

		int idx = SelectionState.getSelectedJump().getReminders().indexOf(rem);
		if (idx < 0) {
			return;
		}

		SelectionState.getSelectedJump().setActiveReminderIndex(idx);
		DataStoreRepository.save();
	}

	private static String getLineSafe(ArrayList<String> lines, int idx) {
		if (lines == null || idx < 0 || idx >= lines.size()) {
			return "";
		}
		String s = lines.get(idx);
		return s != null ? s : "";
	}

}
