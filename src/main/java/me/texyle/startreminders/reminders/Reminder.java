package me.texyle.startreminders.reminders;

import java.util.ArrayList;

public class Reminder {
	public ArrayList<String> lines;

	/** For strategies that came from a sheet: which sheet row they are (jump key + row in its block); else null. */
	public String sheetSource;

	public Reminder(ArrayList<String> lines) {
		this.lines = new ArrayList<String>(lines);
	}
}