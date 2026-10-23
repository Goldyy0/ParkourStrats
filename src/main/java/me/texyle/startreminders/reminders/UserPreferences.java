package me.texyle.startreminders.reminders;

import me.texyle.startreminders.persistence.SettingsRepository;

/** Typed access to the user-facing options stored in {@link ModSettings}. */
public final class UserPreferences {

	private static final String DEFAULT_JUMP_NAME_COLOR = "\u00A75"; // purple
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

	/** True: sections are narrow color stripes in the jump table; false: full-width columns with names. */
	public static boolean isSectionsCompact() {
		return SettingsRepository.get().sectionsCompact;
	}

	public static void setSectionsCompact(boolean compact) {
		SettingsRepository.get().sectionsCompact = compact;
		SettingsRepository.save();
	}

	/** False: the mod prints no status messages in chat (the tap recorder's prompt is exempt). */
	public static boolean isChatMessagesEnabled() {
		return SettingsRepository.get().chatMessages;
	}

	public static void setChatMessagesEnabled(boolean enabled) {
		SettingsRepository.get().chatMessages = enabled;
		SettingsRepository.save();
	}

	/** True: the show key toggles the in-world strategies; false: they show while it is held. */
	public static boolean isShowKeyToggle() {
		return SettingsRepository.get().showKeyToggle;
	}

	public static void setShowKeyToggle(boolean toggle) {
		SettingsRepository.get().showKeyToggle = toggle;
		SettingsRepository.save();
	}

	/** How far (blocks, 1..100) the edit key looks for a jump in the crosshair. */
	public static int getCrosshairRange() {
		int range = SettingsRepository.get().crosshairRange;
		return range <= 0 ? 50 : Math.max(1, Math.min(100, range));
	}

	public static void setCrosshairRange(int blocks) {
		SettingsRepository.get().crosshairRange = Math.max(1, Math.min(100, blocks));
		SettingsRepository.save();
	}

	/** Puts every option of the Settings tab back to its default (the position readout decimals and the section indicator stay). */
	public static void resetToDefaults() {
		ModSettings current = SettingsRepository.get();
		ModSettings defaults = new ModSettings();

		current.showJumpNameInWorld = defaults.showJumpNameInWorld;
		current.inWorldJumpNameColor = defaults.inWorldJumpNameColor;
		current.inWorldTextColor = defaults.inWorldTextColor;
		current.editPickModeInCrosshair = defaults.editPickModeInCrosshair;
		current.sectionsCompact = defaults.sectionsCompact;
		current.showKeyToggle = defaults.showKeyToggle;
		current.chatMessages = defaults.chatMessages;
		current.labelForward = defaults.labelForward;
		current.labelUp = defaults.labelUp;
		current.crosshairRange = defaults.crosshairRange;
		current.nearestRange = defaults.nearestRange;
		current.labelRange = defaults.labelRange;
		SettingsRepository.save();
	}

	/** How far (blocks, 1..50) the strategy labels are drawn in the world. */
	public static int getLabelRange() {
		int range = SettingsRepository.get().labelRange;
		return range <= 0 ? 15 : Math.max(1, Math.min(50, range));
	}

	public static void setLabelRange(int blocks) {
		SettingsRepository.get().labelRange = Math.max(1, Math.min(50, blocks));
		SettingsRepository.save();
	}

	/** Template mode: the section level written under the crosshair; 0 = the innermost section. */
	public static int getSectionIndicatorLevel() {
		return Math.max(0, Math.min(5, SettingsRepository.get().sectionIndicatorLevel));
	}

	public static void setSectionIndicatorLevel(int level) {
		SettingsRepository.get().sectionIndicatorLevel = Math.max(0, Math.min(5, level));
		SettingsRepository.save();
	}

	/** How far (blocks, 1..100) the edit key looks for the nearest jump. */
	public static int getNearestRange() {
		int range = SettingsRepository.get().nearestRange;
		return range <= 0 ? 50 : Math.max(1, Math.min(100, range));
	}

	public static void setNearestRange(int blocks) {
		SettingsRepository.get().nearestRange = Math.max(1, Math.min(100, blocks));
		SettingsRepository.save();
	}

	/** Blocks in front of the player (0..2) where a new strategy label is placed. */
	public static int getLabelForward() {
		return Math.max(0, Math.min(2, SettingsRepository.get().labelForward));
	}

	public static void setLabelForward(int blocks) {
		SettingsRepository.get().labelForward = Math.max(0, Math.min(2, blocks));
		SettingsRepository.save();
	}

	/** Blocks above the player's feet block (0..2) where a new strategy label is placed. */
	public static int getLabelUp() {
		return Math.max(0, Math.min(2, SettingsRepository.get().labelUp));
	}

	public static void setLabelUp(int blocks) {
		SettingsRepository.get().labelUp = Math.max(0, Math.min(2, blocks));
		SettingsRepository.save();
	}

	/** Extra decimals (0..5) shown in the strategy editor live position readout. */
	public static int getPlayerInfoExtraDecimals() {
		return Math.max(0, Math.min(5, SettingsRepository.get().playerInfoExtraDecimals));
	}

	public static void setPlayerInfoExtraDecimals(int decimals) {
		SettingsRepository.get().playerInfoExtraDecimals = Math.max(0, Math.min(5, decimals));
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
