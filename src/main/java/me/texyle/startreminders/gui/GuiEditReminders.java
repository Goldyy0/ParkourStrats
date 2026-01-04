package me.texyle.startreminders.gui;

import java.io.IOException;
import java.util.ArrayList;

import me.texyle.startreminders.data.Jump;
import me.texyle.startreminders.data.ParkourMap;
import me.texyle.startreminders.data.ServerProfile;
import me.texyle.startreminders.gui.hierarchy.GuiJumpList;
import me.texyle.startreminders.reminders.Reminder;
import me.texyle.startreminders.reminders.ReminderManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;
import net.minecraft.client.renderer.GlStateManager;

public class GuiEditReminders extends GuiCreateReminder {

	private static final String RESTORED_ID = "RestoredStrats";

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
		boolean show = ReminderManager.isGlobalShowJumpNameEnabled();
		return show ? JUMP_NAME_VISIBLE_ICON : JUMP_NAME_HIDDEN_ICON;
	}

	private void updateJumpNameToggleButtonState() {
		if (toggleJumpNameButton == null) {
			return;
		}

		boolean enabled = !isRestoredContextLocal();
		toggleJumpNameButton.enabled = enabled;

		if (toggleJumpNameButton instanceof IconSquareButton) {
			((IconSquareButton) toggleJumpNameButton).setIcon(getJumpNameToggleIcon());
		}
	}

	private void updatePickModeButtonText() {
		if (buttonPickMode == null) {
			return;
		}

		boolean crosshair = ReminderManager.isEditPickModeInCrosshair();
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
		} else if (ReminderManager.getSelectedJump() == null) {
			ReminderManager.selectNearestJumpToPlayer();
		}

		// Build list from current selection
		reminderList = new ArrayList<Reminder>(ReminderManager.getReminderList());
		if (reminderList == null || reminderList.isEmpty()) {
			// No strategies yet -> go to Create screen for the currently selected jump.
			Minecraft.getMinecraft().displayGuiScreen(new GuiCreateReminder(parentScreen));
			return;
		}

		Jump jump = ReminderManager.getSelectedJump();
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
		boolean crosshair = ReminderManager.isEditPickModeInCrosshair();
		if (!crosshair) {
			ReminderManager.selectNearestJumpToPlayer();
			return ReminderManager.getSelectedJump() != null;
		}

		Jump picked = ReminderManager.selectJumpInCrosshair(50.0D, 1.75D);
		if (picked == null) {
			String str = EnumChatFormatting.DARK_AQUA + "[ParkourStrats] "
					+ EnumChatFormatting.YELLOW + "No strategies in visible range.";
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
		reminderList = new ArrayList<Reminder>(ReminderManager.getReminderList());
		if (reminderList == null || reminderList.isEmpty()) {
			if (announceIfNone) {
				String str = EnumChatFormatting.DARK_AQUA + "[ParkourStrats] "
						+ EnumChatFormatting.YELLOW + "No strategies in visible range.";
				ChatComponentText msg = new ChatComponentText(str);
				if (Minecraft.getMinecraft().thePlayer != null) {
					Minecraft.getMinecraft().thePlayer.addChatMessage(msg);
				}
			}

			// If we ended up with empty list, fallback to nearest and try again
			ReminderManager.selectNearestJumpToPlayer();
			reminderList = new ArrayList<Reminder>(ReminderManager.getReminderList());
			if (reminderList == null || reminderList.isEmpty()) {
				Minecraft.getMinecraft().displayGuiScreen(new GuiCreateReminder(parentScreen));
				return;
			}
		}

		Jump jump = ReminderManager.getSelectedJump();
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
		if (!isRestoredContextLocal()) {
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

		boolean hasValidCoordinates = canParseInt(textX) && canParseInt(textY) && canParseInt(textZ);

		if (isRestoredContextLocal()) {
			// RestoredStrats editor: coordinates-only requirement.
			saveButton.enabled = hasValidCoordinates;
			return;
		}

		// New mode editor: no required fields, but at least one must be non-empty.
		boolean hasAnyText =
				hasText(textPosition) ||
						hasText(textFacing) ||
						hasText(textSetup) ||
						hasText(textStrategy) ||
						hasText(textStrafe) ||
						hasText(textTurn) ||
						hasText(textAuthor) ||
						hasText(textTips);

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

	private boolean isRestoredContextLocal() {
		ServerProfile s = ReminderManager.getSelectedServer();
		if (s != null && RESTORED_ID.equals(s.getId())) {
			return true;
		}

		ParkourMap m = ReminderManager.getSelectedMap();
		return m != null && RESTORED_ID.equals(m.getId());
	}

	private void fillFieldsFromReminder() {
		ArrayList<String> lines = (selectedReminder != null) ? selectedReminder.lines : null;

		if (isRestoredContextLocal()) {
			/*
			 * RestoredStrats editor uses legacy UI (Line 1..5), BUT:
			 * - If data is already in new 8-line format, we want to start from "Setup" (index 2)
			 *   so user sees values from Line 1, not Line 3.
			 * - If data is old/legacy (<8), show it as-is (0..4) for viewing/editing.
			 */
			boolean isNewFormat = (lines != null && lines.size() >= NEW_FMT_SIZE);

			if (isNewFormat) {
				// Line 1..5 -> Setup/Strategy/Strafe/Turn/Tips
				textLine1.setText(getLineSafe(lines, 2));
				textLine2.setText(getLineSafe(lines, 3));
				textLine3.setText(getLineSafe(lines, 4));
				textLine4.setText(getLineSafe(lines, 5));
				textLine5.setText(getLineSafe(lines, 7));
			} else {
				// Legacy: map lines[0..4] to Line 1..5
				textLine1.setText(getLineSafe(lines, 0));
				textLine2.setText(getLineSafe(lines, 1));
				textLine3.setText(getLineSafe(lines, 2));
				textLine4.setText(getLineSafe(lines, 3));
				textLine5.setText(getLineSafe(lines, 4));
			}

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
					textPosition.setText(nullSafe(lines.get(0)));
					textFacing.setText(nullSafe(lines.get(1)));

					textSetup.setText(nullSafe(lines.get(2)));
					textStrategy.setText(nullSafe(lines.get(3)));
					textStrafe.setText(nullSafe(lines.get(4)));
					textTurn.setText(nullSafe(lines.get(5)));
					textAuthor.setText(nullSafe(lines.get(6)));
					textTips.setText(nullSafe(lines.get(7)));
				} else if (lines.size() >= 6) {
					textSetup.setText(nullSafe(lines.get(0)));
					textStrategy.setText(nullSafe(lines.get(1)));
					textStrafe.setText(nullSafe(lines.get(2)));
					textTurn.setText(nullSafe(lines.get(3)));
					textAuthor.setText(nullSafe(lines.get(4)));
					textTips.setText(nullSafe(lines.get(5)));
				} else {
					if (lines.size() > 0) textStrategy.setText(nullSafe(lines.get(0)));
					if (lines.size() > 1) textStrafe.setText(nullSafe(lines.get(1)));
					if (lines.size() > 2) textTurn.setText(nullSafe(lines.get(2)));
					if (lines.size() > 3) textAuthor.setText(nullSafe(lines.get(3)));
					if (lines.size() > 4) textTips.setText(nullSafe(lines.get(4)));
				}
			}
		}

		// Jump fields: ONLY Coordinates are shared at jump level.
		Jump jump = ReminderManager.getSelectedJump();
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

		Jump jump = ReminderManager.getSelectedJump();
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

	private static String nullSafe(String s) {
		return s != null ? s : "";
	}

	private static String getLineSafe(ArrayList<String> lines, int idx) {
		if (lines == null || idx < 0 || idx >= lines.size()) {
			return "";
		}
		String s = lines.get(idx);
		return s != null ? s : "";
	}

	private static void ensureSize(ArrayList<String> lines, int size) {
		if (lines == null) return;
		while (lines.size() < size) {
			lines.add("");
		}
	}

	@Override
	protected void actionPerformed(GuiButton button) throws IOException {
		if (button != null && button.id == BTN_USE_PLAYER_COORDS) {
			applyPlayerCoordsToFields();
			return;
		}

		if (button != null && button.id == BTN_TOGGLE_JUMP_NAME) {
			if (!isRestoredContextLocal()) {
				boolean cur = ReminderManager.isGlobalShowJumpNameEnabled();
				ReminderManager.setGlobalShowJumpNameEnabled(!cur);
				updateJumpNameToggleButtonState();
			}
			return;
		}

		if (button != null && button.id == BTN_TOGGLE_PICK_MODE) {
			boolean cur = ReminderManager.isEditPickModeInCrosshair();
			boolean next = !cur;
			ReminderManager.setEditPickModeInCrosshair(next);

			// IMPORTANT: Do not auto-pick anything while already inside the menu.
			// This toggle only changes the mode for the NEXT time the menu is opened via keybind.
			updatePickModeButtonText();
			return;
		}

		if (button.id == 21) {
			ParkourMap map = ReminderManager.getSelectedMap();
			if (map != null) {
				Minecraft.getMinecraft().displayGuiScreen(new GuiJumpList(parentScreen, map));
			} else {
				Minecraft.getMinecraft().displayGuiScreen(parentScreen);
			}
			return;
		}

		if (button.id == buttonLeft.id) {
			if (selectedIndex > 1) {
				selectedReminder = reminderList.get(--selectedIndex - 1);
				numStr = selectedIndex + "/" + reminderList.size();
				fillFieldsFromReminder();
			}
			return;
		}

		if (button.id == buttonRight.id) {
			if (selectedIndex < reminderList.size()) {
				selectedReminder = reminderList.get(++selectedIndex - 1);
				numStr = selectedIndex + "/" + reminderList.size();
				fillFieldsFromReminder();
			}
			return;
		}

		if (button.id == saveButton.id) {
			updateReminder();
			return;
		}

		if (buttonSelect != null && button.id == buttonSelect.id) {
			if (selectedReminder != null) {
				ReminderManager.setActiveReminder(selectedReminder);

				String str = EnumChatFormatting.DARK_AQUA + "[ParkourStrats] "
						+ EnumChatFormatting.AQUA + "Selected strategy";
				ChatComponentText message = new ChatComponentText(str);
				if (Minecraft.getMinecraft().thePlayer != null) {
					Minecraft.getMinecraft().thePlayer.addChatMessage(message);
				}
			}

			updateSelectButtonState();
			return;
		}

		if (button.id == buttonDelete.id) {
			ReminderManager.deleteReminder(selectedReminder);

			reminderList = new ArrayList<Reminder>(ReminderManager.getReminderList());
			if (reminderList == null || reminderList.isEmpty()) {
				Minecraft.getMinecraft().displayGuiScreen(new GuiCreateReminder(parentScreen));
				return;
			}

			selectedIndex = 1;
			numStr = selectedIndex + "/" + reminderList.size();
			selectedReminder = reminderList.get(0);
			fillFieldsFromReminder();
			return;
		}

		if (button.id == switchMenuButton.id) {
			this.mc.displayGuiScreen(new GuiCreateReminder(parentScreen));
			return;
		}

		super.actionPerformed(button);
	}

	private void updateReminder() {
		if (saveButton != null && !saveButton.enabled) {
			return;
		}

		int x = Integer.parseInt(textX.getText().trim());
		int y = Integer.parseInt(textY.getText().trim());
		int z = Integer.parseInt(textZ.getText().trim());

		ArrayList<String> out = new ArrayList<String>();

		if (isRestoredContextLocal()) {
			/*
			 * RestoredStrats editor:
			 * Always save using the new 8-line format so the renderer and table interpret it correctly.
			 * UI Line1..Line5 maps to:
			 *  - Setup, Strategy, Strafe, Turn, Tips
			 * Author is not edited here -> keep existing author if possible, otherwise empty.
			 * Position/Facing are not edited here -> keep existing if possible, otherwise empty.
			 */
			ArrayList<String> existing = (selectedReminder != null) ? selectedReminder.lines : null;
			boolean isNewFormat = (existing != null && existing.size() >= NEW_FMT_SIZE);

			if (isNewFormat) {
				// Start from existing to preserve Position/Facing/Author if present
				out = new ArrayList<String>(existing);
				ensureSize(out, NEW_FMT_SIZE);

				out.set(2, safe(textLine1.getText())); // Setup
				out.set(3, safe(textLine2.getText())); // Strategy
				out.set(4, safe(textLine3.getText())); // Strafe
				out.set(5, safe(textLine4.getText())); // Turn
				out.set(7, safe(textLine5.getText())); // Tips
				// out[0], out[1], out[6] preserved
			} else {
				// Convert legacy to new format
				out.add(""); // Position
				out.add(""); // Facing

				out.add(safe(textLine1.getText())); // Setup
				out.add(safe(textLine2.getText())); // Strategy
				out.add(safe(textLine3.getText())); // Strafe
				out.add(safe(textLine4.getText())); // Turn
				out.add("");                        // Author
				out.add(safe(textLine5.getText())); // Tips
			}

		} else {
			// New format: [Position, Facing, Setup, Strategy, Strafe, Turn, Author, Tips]
			out.add(safe(textPosition.getText()));
			out.add(safe(textFacing.getText()));

			out.add(safe(textSetup.getText()));
			out.add(safe(textStrategy.getText()));
			out.add(safe(textStrafe.getText()));
			out.add(safe(textTurn.getText()));
			out.add(safe(textAuthor.getText()));
			out.add(safe(textTips.getText()));
		}

		selectedReminder.lines = out;

		// Persist ONLY jump-level coordinates (shared within jump container).
		Jump jump = ReminderManager.getSelectedJump();
		if (jump != null) {
			jump.setX(x);
			jump.setY(y);
			jump.setZ(z);
		}

		String str = EnumChatFormatting.DARK_AQUA + "[ParkourStrats] ";
		str += EnumChatFormatting.AQUA + "Strategy updated";
		ChatComponentText message = new ChatComponentText(str);

		if (Minecraft.getMinecraft().thePlayer != null) {
			Minecraft.getMinecraft().thePlayer.addChatMessage(message);
		}

		Minecraft.getMinecraft().displayGuiScreen(null);
		ReminderManager.saveToFile();
	}

	// Put this at the end of GuiEditReminders.java (inside the class)
	private static final class IconSquareButton extends GuiButton {

		private ResourceLocation icon;
		private final int iconSize; // in pixels, e.g. 16

		public IconSquareButton(int buttonId, int x, int y, int widthIn, int heightIn, ResourceLocation icon) {
			super(buttonId, x, y, widthIn, heightIn, "");
			this.icon = icon;
			this.iconSize = 16;
		}

		public void setIcon(ResourceLocation icon) {
			this.icon = icon;
		}

		@Override
		public void drawButton(Minecraft mc, int mouseX, int mouseY) {
			if (!this.visible) return;

			this.hovered = mouseX >= this.xPosition && mouseY >= this.yPosition
					&& mouseX < this.xPosition + this.width
					&& mouseY < this.yPosition + this.height;

			if (icon == null) return;

			GlStateManager.pushMatrix();
			try {
				GlStateManager.disableLighting();
				GlStateManager.disableDepth();
				GlStateManager.enableBlend();
				GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0); // standard GUI blend
				GlStateManager.color(1f, 1f, 1f, 1f);

				mc.getTextureManager().bindTexture(icon);

				int ix = this.xPosition + (this.width - iconSize) / 2;
				int iy = this.yPosition + (this.height - iconSize) / 2;

				drawModalRectWithCustomSizedTexture(ix, iy, 0f, 0f, iconSize, iconSize, 16f, 16f);
			} finally {
				GlStateManager.disableBlend();
				GlStateManager.enableDepth();
				GlStateManager.popMatrix();
			}
		}
	}
}