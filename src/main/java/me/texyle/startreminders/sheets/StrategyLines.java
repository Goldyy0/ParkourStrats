package me.texyle.startreminders.sheets;

import java.util.ArrayList;

import me.texyle.startreminders.data.Jump;
import me.texyle.startreminders.reminders.Reminder;

/** Comparison and clean-up helpers for the 8-line strategy format. */
public final class StrategyLines {

	private StrategyLines() {}

	public static ArrayList<String> ensureEightLines(ArrayList<String> lines) {
		ArrayList<String> out = new ArrayList<String>(8);
		for (int i = 0; i < 8; i++) {
			String v = (lines != null && i < lines.size() && lines.get(i) != null) ? lines.get(i) : "";
			out.add(v);
		}
		return out;
	}

	public static void removePlaceholderReminders(Jump j) {
		if (j == null || j.getReminders() == null) return;

		ArrayList<Reminder> rs = j.getReminders();
		for (int i = rs.size() - 1; i >= 0; i--) {
			Reminder r = rs.get(i);
			if (r == null || r.lines == null || r.lines.isEmpty()) continue;

			if (isPlaceholderReminder(r)) {
				rs.remove(i);
			}
		}

		if (rs.isEmpty()) {
			j.setActiveReminderIndex(-1);
		} else if (j.getActiveReminderIndex() < 0 || j.getActiveReminderIndex() >= rs.size()) {
			j.setActiveReminderIndex(0);
		}
	}

	private static boolean isPlaceholderReminder(Reminder r) {
		if (r == null || r.lines == null || r.lines.isEmpty()) {
			return false;
		}

		// Legacy placeholder format: first cell (index 0)
		String v0 = getLineTrim(r.lines, 0);
		if ("PLACEHOLDER".equals(v0)) {
			return true;
		}

		// New forced placeholder format: Setup (index 2)
		String v2 = getLineTrim(r.lines, 2);
		if ("PLACEHOLDER".equals(v2)) {
			return true;
		}

		// Extra safety: some bad historical cases ended up in Strategy (index 3)
		String v3 = getLineTrim(r.lines, 3);
		if ("PLACEHOLDER".equals(v3)) {
			return true;
		}

		// Extra safety: some bad historical cases ended up in Strafe (index 4)
		String v4 = getLineTrim(r.lines, 4);
        return "PLACEHOLDER".equals(v4);
    }

	private static String getLineTrim(ArrayList<String> lines, int idx) {
		if (lines == null || idx < 0 || idx >= lines.size()) return "";
		String s = lines.get(idx);
		return s != null ? s.trim() : "";
	}

	private static boolean sameCore6(ArrayList<String> a, ArrayList<String> b) {
		if (a == null || b == null) return false;

		// Core identity: indices 0..5 (Position, Facing, Setup, Strategy, Strafe, Turn)
		for (int i = 0; i <= 5; i++) {
			String sa = (i < a.size() && a.get(i) != null) ? a.get(i).trim() : "";
			String sb = (i < b.size() && b.get(i) != null) ? b.get(i).trim() : "";
			if (!sa.equals(sb)) return false;
		}
		return true;
	}

	public static Reminder findReminderByCore6(Jump j, ArrayList<String> lines) {
		if (j == null || j.getReminders() == null || lines == null) return null;

		for (Reminder r : j.getReminders()) {
			if (r == null || r.lines == null) continue;
			if (sameCore6(r.lines, lines)) {
				return r;
			}
		}
		return null;
	}

	public static void updateAuthorAndTipsIfChanged(Reminder existing, ArrayList<String> incoming) {
		if (existing == null || existing.lines == null || incoming == null) return;

		// Ensure existing has at least 8 slots to update safely
		while (existing.lines.size() < 8) {
			existing.lines.add("");
		}

		String newAuthor = (incoming.size() > 6 && incoming.get(6) != null) ? incoming.get(6) : "";
		String newTips = (incoming.size() > 7 && incoming.get(7) != null) ? incoming.get(7) : "";

		// Always update (including empty), per your rule: if only these change, we treat it as same strategy and overwrite.
		existing.lines.set(6, newAuthor);
		existing.lines.set(7, newTips);
	}

}
