package me.texyle.startreminders.utils;


/** Validation rules for server, map and jump names. */
public final class IdRules {

	private IdRules() {}

	public static boolean isValidId(String id) {
		if (id == null) return false;
		String trimmed = id.trim();
		if (trimmed.isEmpty()) return false;
		return trimmed.length() <= 48;
	}

}
