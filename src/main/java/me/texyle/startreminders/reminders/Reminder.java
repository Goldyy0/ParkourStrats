package me.texyle.startreminders.reminders;

import java.util.ArrayList;

public class Reminder {
	public ArrayList<String> lines;

	public Reminder(ArrayList<String> lines) {
		this.lines = new ArrayList<String>(lines);
	}
}