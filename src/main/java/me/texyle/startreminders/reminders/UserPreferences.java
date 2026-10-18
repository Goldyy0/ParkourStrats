package me.texyle.startreminders.reminders;

import me.texyle.startreminders.persistence.SettingsRepository;

/** Typed access to the user-facing options stored in {@link ModSettings}. */
public final class UserPreferences {

	private static final String DEFAULT_JUMP_NAME_COLOR = "\u00A7b"; // aqua
	private static final String DEFAULT_TEXT_COLOR = "\u00A7f"; // white

	private UserPreferences() {}

	// Edit menu jump pick mode: false = nearest, true = in crosshair.
	public static boolean isEditPickModeInCrosshair() {
		return SettingsRepository.get().editPickModeInCrosshair;
	}

	public static void setEditPickModeInCrosshair(boolean enabled) {
		SettingsRepository.get().editPickModeInCrosshair = enabled;
		SettingsRepository.save();
	}

	public static boolean isGlobalShowJumpNameEnabled() {
		return SettingsRepository.get().showJumpNameInWorld;
	}

	public static void setGlobalShowJumpNameEnabled(boolean enabled) {
		SettingsRepository.get().showJumpNameInWorld = enabled;
		SettingsRepository.save();
	}

	public static String getGlobalInWorldJumpNameColor() {
		ModSettings settings = SettingsRepository.get();

		if (!isValidColorCode(settings.inWorldJumpNameColor)) {
			settings.inWorldJumpNameColor = DEFAULT_JUMP_NAME_COLOR;
			SettingsRepository.save();
		}
		return settings.inWorldJumpNameColor;
	}

	public static void setGlobalInWorldJumpNameColor(String code) {
		if (!isValidColorCode(code)) {
			return;
		}

		SettingsRepository.get().inWorldJumpNameColor = code;
		SettingsRepository.save();
	}

	public static String getGlobalInWorldTextColor() {
		ModSettings settings = SettingsRepository.get();

		if (!isValidColorCode(settings.inWorldTextColor)) {
			settings.inWorldTextColor = DEFAULT_TEXT_COLOR;
			SettingsRepository.save();
		}
		return settings.inWorldTextColor;
	}

	public static void setGlobalInWorldTextColor(String code) {
		if (!isValidColorCode(code)) {
			return;
		}

		SettingsRepository.get().inWorldTextColor = code;
		SettingsRepository.save();
	}

	private static boolean isValidColorCode(String s) {
		if (s == null) return false;
		if (s.length() != 2) return false;
		if (s.charAt(0) != '\u00A7') return false;

		char c = Character.toLowerCase(s.charAt(1));
		return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
	}
}
