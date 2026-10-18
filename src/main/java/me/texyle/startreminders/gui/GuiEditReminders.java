package me.texyle.startreminders.gui;

import java.io.IOException;
import java.util.ArrayList;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;

import me.texyle.startreminders.data.Jump;
import me.texyle.startreminders.data.ParkourMap;
import me.texyle.startreminders.gui.hierarchy.GuiJumpList;
import me.texyle.startreminders.persistence.DataStoreRepository;
import me.texyle.startreminders.reminders.JumpPicker;
import me.texyle.startreminders.reminders.Reminder;
import me.texyle.startreminders.reminders.SelectionState;
import me.texyle.startreminders.reminders.UserPreferences;
import me.texyle.startreminders.services.StrategyService;
import me.texyle.startreminders.utils.ChatUtils;

public class GuiEditReminders extends GuiCreateReminder {

	private static final int BTN_TOGGLE_JUMP_NAME = 31;
	private static final int BTN_TOGGLE_PICK_MODE = 32;

	// You will provide these icons (16x16) under assets/sr/textures/gui/
	private static final ResourceLocation JUMP_NAME_VISIBLE_ICON =
			new ResourceLocation("sr", "textures/gui/icon_visible.png");
	private static final ResourceLocation JUMP_NAME_HIDDEN_ICON =
			new ResourceLocation("sr", "textures/gui/icon_hidden.png");

	private GuiButton toggleJumpNameButton;

	// New (8): [Position, Facing, Setup, Strategy, Strafe, Turn, Author, Tips]
	private static final int NEW_FMT_SIZE = 8;

	private GuiButton buttonLeft;
	private GuiButton buttonRight;
	private GuiButton buttonDelete;

	private GuiButton buttonSelect;
	private GuiButton buttonPickMode;

	private GuiButton backToSheetButton;

	private ArrayList<Reminder> reminderList;
	private Reminder selectedReminder;
	private boolean abortOpen = false;

	private int selectedIndex = 1;
	private String numStr;

	private ResourceLocation getJumpNameToggleIcon() {
		boolean show = UserPreferences.isGlobalShowJumpNameEnabled();
		return show ? JUMP_NAME_VISIBLE_ICON : JUMP_NAME_HIDDEN_ICON;
	}

	private void updateJumpNameToggleButtonState() {
		if (toggleJumpNameButton == null) {
			return;
		}

		boolean enabled = !SelectionState.isRestoredContext();
		toggleJumpNameButton.enabled = enabled;

		if (toggleJumpNameButton instanceof IconSquareButton) {
			((IconSquareButton) toggleJumpNameButton).setIcon(getJumpNameToggleIcon());
		}
	}

	private void updatePickModeButtonText() {
		if (buttonPickMode == null) {
			return;
		}

		boolean crosshair = UserPreferences.isEditPickModeInCrosshair();
		buttonPickMode.displayString = crosshair ? "In crosshair" : "Nearest";
	}

	public GuiEditReminders() {
		this(null);
	}

	public GuiEditReminders(net.minecraft.client.gui.GuiScreen parentScreen) {
		super(parentScreen);

		/*
		 * Selection policy:
		 * - Opened from keybind / GuiHandler: parentScreen == null -> pick based on mode (Nearest default).
		 * - Opened from another GUI (e.g., Jump list double-click): keep current selected jump.
		 */
		if (parentScreen == null) {
			boolean ok = pickJumpByModeForKeybindOpen();
			if (!ok) {
				this.abortOpen = true;
				return;
			}
		} else if (SelectionState.getSelectedJump() == null) {
			JumpPicker.selectNearestJumpToPlayer();
		}

		// Build list from current selection
		reminderList = new ArrayList<Reminder>(StrategyService.getReminderList());
		if (reminderList == null || reminderList.isEmpty()) {
			// No strategies yet -> go to Create screen for the currently selected jump.
			Minecraft.getMinecraft().displayGuiScreen(new GuiCreateReminder(parentScreen));
			return;
		}

		Jump jump = SelectionState.getSelectedJump();
		int startIdx = 0;
		if (jump != null) {
			int active = jump.getActiveReminderIndex();
			if (active >= 0 && active < reminderList.size()) {
				startIdx = active;
			}
		}

		selectedIndex = startIdx + 1;
		numStr = selectedIndex + "/" + reminderList.size();
		selectedReminder = reminderList.get(startIdx);
	}

	private boolean pickJumpByModeForKeybindOpen() {
		boolean crosshair = UserPreferences.isEditPickModeInCrosshair();
		if (!crosshair) {
			JumpPicker.selectNearestJumpToPlayer();
			return SelectionState.getSelectedJump() != null;
		}

		Jump picked = JumpPicker.selectJumpInCrosshair(50.0D, 1.75D);
		if (picked == null) {
			String str = ChatUtils.WARNING_PREFIX + "No strategies in visible range.";
			ChatComponentText msg = new ChatComponentText(str);
			if (Minecraft.getMinecraft().thePlayer != null) {
				Minecraft.getMinecraft().thePlayer.addChatMessage(msg);
			}

			// IMPORTANT: do NOT fallback to nearest. Just refuse to open.
			return false;
		}

		return true;
	}

	private void reloadFromSelectionOrFallback(boolean announceIfNone) {
		reminderList = new ArrayList<Reminder>(StrategyService.getReminderList());
		if (reminderList == null || reminderList.isEmpty()) {
			if (announceIfNone) {
				String str = ChatUtils.WARNING_PREFIX + "No strategies in visible range.";
				ChatComponentText msg = new ChatComponentText(str);
				if (Minecraft.getMinecraft().thePlayer != null) {
					Minecraft.getMinecraft().thePlayer.addChatMessage(msg);
				}
			}

			// If we ended up with empty list, fallback to nearest and try again
			JumpPicker.selectNearestJumpToPlayer();
			reminderList = new ArrayList<Reminder>(StrategyService.getReminderList());
			if (reminderList == null || reminderList.isEmpty()) {
				Minecraft.getMinecraft().displayGuiScreen(new GuiCreateReminder(parentScreen));
				return;
			}
		}

		Jump jump = SelectionState.getSelectedJump();
		int startIdx = 0;
		if (jump != null) {
			int active = jump.getActiveReminderIndex();
			if (active >= 0 && active < reminderList.size()) {
				startIdx = active;
			}
		}

		selectedIndex = startIdx + 1;
		numStr = selectedIndex + "/" + reminderList.size();
		selectedReminder = reminderList.get(startIdx);

		fillFieldsFromReminder();
	}

	@Override
	public void initGui() {
		if (abortOpen) {
			Minecraft.getMinecraft().displayGuiScreen(null);
			return;
		}

		super.initGui();

		if (reminderList == null || reminderList.isEmpty()) {
			Minecraft.getMinecraft().displayGuiScreen(new GuiCreateReminder(parentScreen));
			return;
		}

		titleText = "  Edit strategies  ";

		// Add "Back to sheet" under the inherited Back button
		backToSheetButton = new GuiButton(21, 8, 32, 100, 20, "Back to sheet");
		buttonList.add(backToSheetButton);

		// Move Save to bottom-most
		saveButton.yPosition = this.height - 28;
		saveButton.id = 11;

		// Place arrows at far left, vertically centered
		int arrowsX = 8;
		int arrowsY = (this.height / 2) - 10;

		buttonLeft = new GuiButton(9, arrowsX, arrowsY, "<");
		buttonLeft.setWidth(24);
		buttonList.add(buttonLeft);

		buttonRight = new GuiButton(10, arrowsX + 28, arrowsY, ">");
		buttonRight.setWidth(24);
		buttonList.add(buttonRight);

		// Select button: right side, vertically centered (like the page arrows)
		int selectW = 80;
		int selectX = this.width - 8 - selectW;
		int selectY = (this.height / 2) - 10;

		buttonSelect = new GuiButton(14, selectX, selectY, "Select");
		buttonSelect.setWidth(selectW);
		buttonList.add(buttonSelect);

		// New: pick-mode toggle under Select
		int modeY = selectY + 24;
		buttonPickMode = new GuiButton(BTN_TOGGLE_PICK_MODE, selectX, modeY, "");
		buttonPickMode.setWidth(selectW);
		buttonList.add(buttonPickMode);
		updatePickModeButtonText();

		// Move Delete near bottom, above Save (so it never overlaps)
		buttonDelete = new GuiButton(13, saveButton.xPosition, this.height - 52, EnumChatFormatting.RED + "Delete");
		buttonList.add(buttonDelete);

		switchMenuButton.displayString = "Create menu";
		switchMenuButton.id = 17;

		// Add toggle button for showing jump name in-world (NEW editor only)
		if (!SelectionState.isRestoredContext()) {
			int x = getFieldX() - ICON_BTN_SIZE - 2;  // "next to Jump:" (left of the yellow jump id text)
			int y = getRowY(2) - 1;

			ResourceLocation icon = getJumpNameToggleIcon();
			toggleJumpNameButton = new IconSquareButton(BTN_TOGGLE_JUMP_NAME, x, y, ICON_BTN_SIZE, ICON_BTN_SIZE, icon);
			buttonList.add(toggleJumpNameButton);
		}

		fillFieldsFromReminder();
		updateSaveButtonState();
		updateSelectButtonState();
		updateUsePlayerCoordsButtonState();
		updateJumpNameToggleButtonState();
	}

	@Override
	protected void updateSaveButtonState() {
		if (saveButton == null) {
			return;
		}

		boolean hasValidCoordinates = FormFields.canParseInt(textX) && FormFields.canParseInt(textY) && FormFields.canParseInt(textZ);

		if (SelectionState.isRestoredContext()) {
			// RestoredStrats editor: coordinates-only requirement.
			saveButton.enabled = hasValidCoordinates;
			return;
		}

		// New mode editor: no required fields, but at least one must be non-empty.
		boolean hasAnyText =
				FormFields.hasText(textPosition) ||
						FormFields.hasText(textFacing) ||
						FormFields.hasText(textSetup) ||
						FormFields.hasText(textStrategy) ||
						FormFields.hasText(textStrafe) ||
						FormFields.hasText(textTurn) ||
						FormFields.hasText(textAuthor) ||
						FormFields.hasText(textTips);

		saveButton.enabled = hasValidCoordinates && hasAnyText;
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		if (abortOpen) {
			Minecraft.getMinecraft().displayGuiScreen(null);
			return;
		}

		if (reminderList == null || reminderList.isEmpty()) {
			Minecraft.getMinecraft().displayGuiScreen(new GuiCreateReminder(parentScreen));
			return;
		}

		super.drawScreen(mouseX, mouseY, partialTicks);

		// Draw counter near the arrows
		if (buttonLeft != null && buttonRight != null) {
			int textX = buttonRight.xPosition + buttonRight.width + 6;
			int textY = buttonLeft.yPosition + 6;
			fontRendererObj.drawString(numStr, textX, textY, 0xFFFFFF, true);
		}
	}

	private void fillFieldsFromReminder() {
		ArrayList<String> lines = (selectedReminder != null) ? selectedReminder.lines : null;

		if (SelectionState.isRestoredContext()) {
			// RestoredStrats editor uses the generic Line 1..5 UI; see StrategyFormat for the mapping.
			String[] v = StrategyFormat.restoredEditorLines(lines);
			textLine1.setText(v[0]);
			textLine2.setText(v[1]);
			textLine3.setText(v[2]);
			textLine4.setText(v[3]);
			textLine5.setText(v[4]);

		} else {
			// New mode defaults
			textPosition.setText("");
			textFacing.setText("");

			textSetup.setText("");
			textStrategy.setText("");
			textStrafe.setText("");
			textTurn.setText("");
			textAuthor.setText("");
			textTips.setText("");

			/*
			 * Supported formats:
			 * 1) New (8): [Position, Facing, Setup, Strategy, Strafe, Turn, Author, Tips]
			 * 2) Previous (6): [Setup, Strategy, Strafe, Turn, Author, Tips]
			 * 3) Legacy (<=5): [Strategy, Strafe, Turn, Author, Tips] (no Setup)
			 */
			if (lines != null) {
				if (lines.size() >= 8) {
					textPosition.setText(StrategyFormat.orEmpty(lines.get(0)));
					textFacing.setText(StrategyFormat.orEmpty(lines.get(1)));

					textSetup.setText(StrategyFormat.orEmpty(lines.get(2)));
					textStrategy.setText(StrategyFormat.orEmpty(lines.get(3)));
					textStrafe.setText(StrategyFormat.orEmpty(lines.get(4)));
					textTurn.setText(StrategyFormat.orEmpty(lines.get(5)));
					textAuthor.setText(StrategyFormat.orEmpty(lines.get(6)));
					textTips.setText(StrategyFormat.orEmpty(lines.get(7)));
				} else if (lines.size() >= 6) {
					textSetup.setText(StrategyFormat.orEmpty(lines.get(0)));
					textStrategy.setText(StrategyFormat.orEmpty(lines.get(1)));
					textStrafe.setText(StrategyFormat.orEmpty(lines.get(2)));
					textTurn.setText(StrategyFormat.orEmpty(lines.get(3)));
					textAuthor.setText(StrategyFormat.orEmpty(lines.get(4)));
					textTips.setText(StrategyFormat.orEmpty(lines.get(5)));
				} else {
					if (lines.size() > 0) textStrategy.setText(StrategyFormat.orEmpty(lines.get(0)));
					if (lines.size() > 1) textStrafe.setText(StrategyFormat.orEmpty(lines.get(1)));
					if (lines.size() > 2) textTurn.setText(StrategyFormat.orEmpty(lines.get(2)));
					if (lines.size() > 3) textAuthor.setText(StrategyFormat.orEmpty(lines.get(3)));
					if (lines.size() > 4) textTips.setText(StrategyFormat.orEmpty(lines.get(4)));
				}
			}
		}

		// Jump fields: ONLY Coordinates are shared at jump level.
		Jump jump = SelectionState.getSelectedJump();
		if (jump != null) {
			textX.setText("" + jump.getX());
			textY.setText("" + jump.getY());
			textZ.setText("" + jump.getZ());
		} else {
			textX.setText("0");
			textY.setText("0");
			textZ.setText("0");
		}

		updateSelectButtonState();
		updateUsePlayerCoordsButtonState();
		updateJumpNameToggleButtonState();
		updatePickModeButtonText();
	}

	private void updateSelectButtonState() {
		if (buttonSelect == null) {
			return;
		}

		Jump jump = SelectionState.getSelectedJump();
		if (jump == null || jump.getReminders() == null || jump.getReminders().isEmpty() || selectedReminder == null) {
			buttonSelect.enabled = false;
			buttonSelect.displayString = "Select";
			return;
		}

		int idx = jump.getReminders().indexOf(selectedReminder);
		boolean isActive = (idx >= 0 && idx == jump.getActiveReminderIndex());

		if (isActive) {
			buttonSelect.enabled = false;
			buttonSelect.displayString = EnumChatFormatting.GREEN + "Selected";
		} else {
			buttonSelect.enabled = true;
			buttonSelect.displayString = "Select";
		}
	}

	@Override
	protected void actionPerformed(GuiButton button) throws IOException {
		if (button != null && button.id == BTN_USE_PLAYER_COORDS) {
			applyPlayerCoordsToFields();
			return;
		}

		if (button != null && button.id == BTN_TOGGLE_JUMP_NAME) {
			onToggleJumpName();
			return;
		}

		if (button != null && button.id == BTN_TOGGLE_PICK_MODE) {
			onTogglePickMode();
			return;
		}

		if (button.id == 21) {
			ParkourMap map = SelectionState.getSelectedMap();
			if (map != null) {
				Minecraft.getMinecraft().displayGuiScreen(new GuiJumpList(parentScreen, map));
			} else {
				Minecraft.getMinecraft().displayGuiScreen(parentScreen);
			}
			return;
		}

		if (button.id == buttonLeft.id) {
			showStrategy(selectedIndex - 1);
			return;
		}

		if (button.id == buttonRight.id) {
			showStrategy(selectedIndex + 1);
			return;
		}

		if (button.id == saveButton.id) {
			updateReminder();
			return;
		}

		if (buttonSelect != null && button.id == buttonSelect.id) {
			onSelectStrategy();
			return;
		}

		if (button.id == buttonDelete.id) {
			onDeleteStrategy();
			return;
		}

		if (button.id == switchMenuButton.id) {
			this.mc.displayGuiScreen(new GuiCreateReminder(parentScreen));
			return;
		}

		super.actionPerformed(button);
	}

	private void onToggleJumpName() {
		if (!SelectionState.isRestoredContext()) {
			boolean cur = UserPreferences.isGlobalShowJumpNameEnabled();
			UserPreferences.setGlobalShowJumpNameEnabled(!cur);
			updateJumpNameToggleButtonState();
		}
	}

	private void onTogglePickMode() {
		boolean cur = UserPreferences.isEditPickModeInCrosshair();
		UserPreferences.setEditPickModeInCrosshair(!cur);

		// IMPORTANT: Do not auto-pick anything while already inside the menu.
		// This toggle only changes the mode for the NEXT time the menu is opened via keybind.
		updatePickModeButtonText();
	}

	/** Shows the strategy with the given 1-based position, if it exists. */
	private void showStrategy(int newIndexOneBased) {
		if (newIndexOneBased < 1 || newIndexOneBased > reminderList.size()) {
			return;
		}

		selectedIndex = newIndexOneBased;
		selectedReminder = reminderList.get(selectedIndex - 1);
		numStr = selectedIndex + "/" + reminderList.size();
		fillFieldsFromReminder();
	}

	private void onSelectStrategy() {
		if (selectedReminder != null) {
			StrategyService.setActiveReminder(selectedReminder);
			ChatUtils.info("Selected strategy");
		}

		updateSelectButtonState();
	}

	private void onDeleteStrategy() {
		StrategyService.deleteReminder(selectedReminder);

		reminderList = new ArrayList<Reminder>(StrategyService.getReminderList());
		if (reminderList.isEmpty()) {
			Minecraft.getMinecraft().displayGuiScreen(new GuiCreateReminder(parentScreen));
			return;
		}

		showStrategy(1);
	}

	private void updateReminder() {
		if (saveButton != null && !saveButton.enabled) {
			return;
		}

		int x = Integer.parseInt(textX.getText().trim());
		int y = Integer.parseInt(textY.getText().trim());
		int z = Integer.parseInt(textZ.getText().trim());

		ArrayList<String> out;

		if (SelectionState.isRestoredContext()) {
			ArrayList<String> existing = (selectedReminder != null) ? selectedReminder.lines : null;
			out = StrategyFormat.applyRestoredEdit(existing,
					FormFields.trimmed(textLine1.getText()),
					FormFields.trimmed(textLine2.getText()),
					FormFields.trimmed(textLine3.getText()),
					FormFields.trimmed(textLine4.getText()),
					FormFields.trimmed(textLine5.getText()));
		} else {
			// New format: [Position, Facing, Setup, Strategy, Strafe, Turn, Author, Tips]
			out = new ArrayList<String>();
			out.add(FormFields.trimmed(textPosition.getText()));
			out.add(FormFields.trimmed(textFacing.getText()));

			out.add(FormFields.trimmed(textSetup.getText()));
			out.add(FormFields.trimmed(textStrategy.getText()));
			out.add(FormFields.trimmed(textStrafe.getText()));
			out.add(FormFields.trimmed(textTurn.getText()));
			out.add(FormFields.trimmed(textAuthor.getText()));
			out.add(FormFields.trimmed(textTips.getText()));
		}

		selectedReminder.lines = out;

		// Persist ONLY jump-level coordinates (shared within jump container).
		Jump jump = SelectionState.getSelectedJump();
		if (jump != null) {
			jump.setX(x);
			jump.setY(y);
			jump.setZ(z);
		}

		ChatUtils.info("Strategy updated");

		Minecraft.getMinecraft().displayGuiScreen(null);
		DataStoreRepository.save();
	}

}