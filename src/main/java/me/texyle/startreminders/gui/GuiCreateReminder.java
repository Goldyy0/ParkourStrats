package me.texyle.startreminders.gui;

import java.io.IOException;
import java.util.ArrayList;

import me.texyle.startreminders.data.Jump;
import me.texyle.startreminders.data.ParkourMap;
import me.texyle.startreminders.data.ServerProfile;
import me.texyle.startreminders.gui.hierarchy.GuiJumpList;
import me.texyle.startreminders.reminders.ReminderManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;

public class GuiCreateReminder extends GuiScreen {

	protected static final int FIELD_WIDTH = 220;

	private static final int COLOR_TEXT = 0xFFFFFF;
	private static final int COLOR_INVALID_OVERLAY = 0x55FF0000;

	private static final String RESTORED_ID = "RestoredStrats";

	// New: "use player coords" icon button
	protected static final int BTN_USE_PLAYER_COORDS = 30;
	protected static final int ICON_BTN_SIZE = 22;

	// Icon texture (assets/sr/textures/gui/coords.png)
	private static final ResourceLocation COORDS_ICON = new ResourceLocation("sr", "textures/gui/coordinates.png");

	protected GuiScreen parentScreen;

	protected GuiButton backButton;
	protected GuiButton backToSheetButton;
	protected GuiButton saveButton;
	protected GuiButton switchMenuButton;

	// New: coordinate sync button
	protected GuiButton usePlayerCoordsButton;

	// New format fields
	protected GuiTextField textPosition;
	protected GuiTextField textFacing;

	protected GuiTextField textSetup;
	protected GuiTextField textStrategy;
	protected GuiTextField textStrafe;
	protected GuiTextField textTurn;
	protected GuiTextField textAuthor;
	protected GuiTextField textTips;

	// Legacy (RestoredStrats) fields
	protected GuiTextField textLine1;
	protected GuiTextField textLine2;
	protected GuiTextField textLine3;
	protected GuiTextField textLine4;
	protected GuiTextField textLine5;

	protected GuiTextField textX;
	protected GuiTextField textY;
	protected GuiTextField textZ;

	protected String titleText = "  Create strategy  ";

	public GuiCreateReminder() {
		this(null);
	}

	public GuiCreateReminder(GuiScreen parentScreen) {
		this.parentScreen = parentScreen;
	}

	@Override
	public void initGui() {
		super.initGui();

		backButton = new GuiButton(20, 8, 8, 60, 20, "Back");
		buttonList.add(backButton);

		backToSheetButton = new GuiButton(21, 8, 32, 100, 20, "Back to sheet");
		buttonList.add(backToSheetButton);

		saveButton = new GuiButton(5, 0, 0, "Save");
		saveButton.xPosition = (this.width - saveButton.width) / 2;
		saveButton.yPosition = this.height - 28;
		buttonList.add(saveButton);

		switchMenuButton = new GuiButton(16, 0, 0, "Edit menu");
		switchMenuButton.width = 100;
		switchMenuButton.xPosition = this.width - 102;
		switchMenuButton.yPosition = 2;
		buttonList.add(switchMenuButton);

		createFields();

		updateUsePlayerCoordsButtonState();
		updateSaveButtonState();
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		this.drawDefaultBackground();
		super.drawScreen(mouseX, mouseY, partialTicks);

		drawTitle();

		if (isRestoredContext()) {
			drawLegacyLayout();
		} else {
			drawNewLayout();
		}

		drawValidationOverlays();

		// Draw fields for the active layout only
		if (isRestoredContext()) {
			textLine1.drawTextBox();
			textLine2.drawTextBox();
			textLine3.drawTextBox();
			textLine4.drawTextBox();
			textLine5.drawTextBox();

			textX.drawTextBox();
			textY.drawTextBox();
			textZ.drawTextBox();
		} else {
			textX.drawTextBox();
			textY.drawTextBox();
			textZ.drawTextBox();

			textPosition.drawTextBox();
			textFacing.drawTextBox();

			textSetup.drawTextBox();
			textStrategy.drawTextBox();
			textStrafe.drawTextBox();
			textTurn.drawTextBox();
			textAuthor.drawTextBox();
			textTips.drawTextBox();
		}
	}

	private void drawLegacyLayout() {
		fontRendererObj.drawString("Line 1: ", getLabelX(), getRowY(3) + 6, COLOR_TEXT, true);
		fontRendererObj.drawString("Line 2: ", getLabelX(), getRowY(4) + 6, COLOR_TEXT, true);
		fontRendererObj.drawString("Line 3: ", getLabelX(), getRowY(5) + 6, COLOR_TEXT, true);
		fontRendererObj.drawString("Line 4: ", getLabelX(), getRowY(6) + 6, COLOR_TEXT, true);
		fontRendererObj.drawString("Line 5: ", getLabelX(), getRowY(7) + 6, COLOR_TEXT, true);

		fontRendererObj.drawString("Location: ", getLabelX(), getRowY(9) + 6, COLOR_TEXT, true);
	}

	private void drawNewLayout() {
		Jump selectedJump = ReminderManager.getSelectedJump();
		String jumpId = (selectedJump != null && selectedJump.getId() != null) ? selectedJump.getId() : "<none>";

		fontRendererObj.drawString("Jump: ", getLabelX(), getRowY(2) + 6, COLOR_TEXT, true);
		fontRendererObj.drawString(EnumChatFormatting.YELLOW + jumpId, getFieldX(), getRowY(2) + 6, COLOR_TEXT, true);

		fontRendererObj.drawString("Coordinates: ", getLabelX(), getRowY(3) + 6, COLOR_TEXT, true);
		fontRendererObj.drawString("Position: ", getLabelX(), getRowY(4) + 6, COLOR_TEXT, true);
		fontRendererObj.drawString("Facing: ", getLabelX(), getRowY(5) + 6, COLOR_TEXT, true);

		fontRendererObj.drawString("Setup: ", getLabelX(), getRowY(6) + 6, COLOR_TEXT, true);
		fontRendererObj.drawString("Strategy: ", getLabelX(), getRowY(7) + 6, COLOR_TEXT, true);
		fontRendererObj.drawString("Strafe: ", getLabelX(), getRowY(8) + 6, COLOR_TEXT, true);
		fontRendererObj.drawString("Turn: ", getLabelX(), getRowY(9) + 6, COLOR_TEXT, true);
		fontRendererObj.drawString("Author: ", getLabelX(), getRowY(10) + 6, COLOR_TEXT, true);
		fontRendererObj.drawString("Tips: ", getLabelX(), getRowY(11) + 6, COLOR_TEXT, true);
	}

	private void drawTitle() {
		GlStateManager.pushMatrix();
		GlStateManager.scale(3, 3, 3);

		int titlePixelW = fontRendererObj.getStringWidth(titleText) * 3;
		int x = (this.width - titlePixelW) / 6;

		int y = 1;

		fontRendererObj.drawString(EnumChatFormatting.UNDERLINE + titleText, x, y, COLOR_TEXT, true);
		GlStateManager.popMatrix();
	}

	private void drawValidationOverlays() {
		boolean validX = canParseInt(textX);
		boolean validY = canParseInt(textY);
		boolean validZ = canParseInt(textZ);

		if (!validX) drawFieldOverlay(textX);
		if (!validY) drawFieldOverlay(textY);
		if (!validZ) drawFieldOverlay(textZ);
	}

	private void drawFieldOverlay(GuiTextField field) {
		if (field == null) {
			return;
		}
		int left = field.xPosition - 1;
		int top = field.yPosition - 1;
		int right = field.xPosition + field.width + 1;
		int bottom = field.yPosition + field.height + 1;
		drawRect(left, top, right, bottom, COLOR_INVALID_OVERLAY);
	}

	private void createFields() {
		FontRenderer fr = mc.getMinecraft().fontRendererObj;

		int x = getFieldX();

		// Coordinates
		int coordsY = isRestoredContext() ? getRowY(9) : getRowY(3);
		int third = FIELD_WIDTH / 3;

		textX = new GuiTextField(100, fr, x, coordsY, third - 6, 20);
		textY = new GuiTextField(101, fr, x + third, coordsY, third - 6, 20);
		textZ = new GuiTextField(102, fr, x + third * 2, coordsY, third - 6, 20);

		EntityPlayerSP player = mc.getMinecraft().thePlayer;

		int sx = 0;
		int sy = 0;
		int sz = 0;

		Jump jump = ReminderManager.getSelectedJump();
		if (jump != null) {
			sx = jump.getX();
			sy = jump.getY();
			sz = jump.getZ();
		}

		if (sx == 0 && sy == 0 && sz == 0 && player != null) {
			sx = (int) Math.floor(player.posX);
			sy = (int) Math.floor(player.posY) + 1;
			sz = (int) Math.floor(player.posZ);
		}

		textX.setText(Integer.toString(sx));
		textY.setText(Integer.toString(sy));
		textZ.setText(Integer.toString(sz));

		// Square icon button next to coordinates fields
		int iconX = x + FIELD_WIDTH + 6;
		int iconY = coordsY - 1;
		usePlayerCoordsButton = new IconSquareButton(BTN_USE_PLAYER_COORDS, iconX, iconY, ICON_BTN_SIZE, ICON_BTN_SIZE, COORDS_ICON);
		buttonList.add(usePlayerCoordsButton);

		if (isRestoredContext()) {
			// Legacy mode: Line 1..5
			textLine1 = new GuiTextField(201, fr, x, getRowY(3), FIELD_WIDTH, 20);
			textLine2 = new GuiTextField(202, fr, x, getRowY(4), FIELD_WIDTH, 20);
			textLine3 = new GuiTextField(203, fr, x, getRowY(5), FIELD_WIDTH, 20);
			textLine4 = new GuiTextField(204, fr, x, getRowY(6), FIELD_WIDTH, 20);
			textLine5 = new GuiTextField(205, fr, x, getRowY(7), FIELD_WIDTH, 20);

			textLine1.setMaxStringLength(160);
			textLine2.setMaxStringLength(160);
			textLine3.setMaxStringLength(160);
			textLine4.setMaxStringLength(160);
			textLine5.setMaxStringLength(160);

			textLine1.setFocused(true);
			return;
		}

		// New format fields
		textPosition = new GuiTextField(103, fr, x, getRowY(4), FIELD_WIDTH, 20);
		textFacing = new GuiTextField(104, fr, x, getRowY(5), FIELD_WIDTH, 20);

		textPosition.setText("");
		textFacing.setText("");

		textSetup = new GuiTextField(1, fr, x, getRowY(6), FIELD_WIDTH, 20);
		textStrategy = new GuiTextField(2, fr, x, getRowY(7), FIELD_WIDTH, 20);
		textStrafe = new GuiTextField(3, fr, x, getRowY(8), FIELD_WIDTH, 20);
		textTurn = new GuiTextField(4, fr, x, getRowY(9), FIELD_WIDTH, 20);
		textAuthor = new GuiTextField(5, fr, x, getRowY(10), FIELD_WIDTH, 20);
		textTips = new GuiTextField(6, fr, x, getRowY(11), FIELD_WIDTH, 20);

		textPosition.setMaxStringLength(80);
		textFacing.setMaxStringLength(80);

		textSetup.setMaxStringLength(80);
		textStrategy.setMaxStringLength(80);
		textStrafe.setMaxStringLength(80);
		textTurn.setMaxStringLength(80);
		textAuthor.setMaxStringLength(80);
		textTips.setMaxStringLength(160);

		textPosition.setFocused(true);
	}

	protected void updateUsePlayerCoordsButtonState() {
		if (usePlayerCoordsButton == null) {
			return;
		}
		EntityPlayerSP p = Minecraft.getMinecraft().thePlayer;
		usePlayerCoordsButton.enabled = (p != null);
	}

	protected void applyPlayerCoordsToFields() {
		EntityPlayerSP p = Minecraft.getMinecraft().thePlayer;
		if (p == null) {
			return;
		}

		int x = (int) Math.floor(p.posX);
		int y = (int) Math.floor(p.posY) + 2;
		int z = (int) Math.floor(p.posZ);

		if (textX != null) textX.setText(Integer.toString(x));
		if (textY != null) textY.setText(Integer.toString(y));
		if (textZ != null) textZ.setText(Integer.toString(z));

		updateSaveButtonState();
	}

	private boolean isRestoredContext() {
		ServerProfile s = ReminderManager.getSelectedServer();
		if (s != null && RESTORED_ID.equals(s.getId())) {
			return true;
		}

		ParkourMap m = ReminderManager.getSelectedMap();
		return m != null && RESTORED_ID.equals(m.getId());
	}

	protected int getFieldX() {
		return (this.width - FIELD_WIDTH) / 2;
	}

	protected int getLabelX() {
		return getFieldX() - 70;
	}

	protected int getRowY(int rowIndex) {
		int base = this.height / 15;
		int topOffset = 1;
		return topOffset + (base * rowIndex);
	}

	@Override
	protected void keyTyped(char typedChar, int keyCode) throws IOException {
		if (isRestoredContext()) {
			textLine1.textboxKeyTyped(typedChar, keyCode);
			textLine2.textboxKeyTyped(typedChar, keyCode);
			textLine3.textboxKeyTyped(typedChar, keyCode);
			textLine4.textboxKeyTyped(typedChar, keyCode);
			textLine5.textboxKeyTyped(typedChar, keyCode);
		} else {
			textPosition.textboxKeyTyped(typedChar, keyCode);
			textFacing.textboxKeyTyped(typedChar, keyCode);

			textSetup.textboxKeyTyped(typedChar, keyCode);
			textStrategy.textboxKeyTyped(typedChar, keyCode);
			textStrafe.textboxKeyTyped(typedChar, keyCode);
			textTurn.textboxKeyTyped(typedChar, keyCode);
			textAuthor.textboxKeyTyped(typedChar, keyCode);
			textTips.textboxKeyTyped(typedChar, keyCode);
		}

		if (Character.isDigit(typedChar) || keyCode == 14 || typedChar == '-') {
			textX.textboxKeyTyped(typedChar, keyCode);
			textY.textboxKeyTyped(typedChar, keyCode);
			textZ.textboxKeyTyped(typedChar, keyCode);
		}

		super.keyTyped(typedChar, keyCode);
		updateUsePlayerCoordsButtonState();
		updateSaveButtonState();
	}

	@Override
	public void updateScreen() {
		if (isRestoredContext()) {
			textLine1.updateCursorCounter();
			textLine2.updateCursorCounter();
			textLine3.updateCursorCounter();
			textLine4.updateCursorCounter();
			textLine5.updateCursorCounter();
		} else {
			textPosition.updateCursorCounter();
			textFacing.updateCursorCounter();

			textSetup.updateCursorCounter();
			textStrategy.updateCursorCounter();
			textStrafe.updateCursorCounter();
			textTurn.updateCursorCounter();
			textAuthor.updateCursorCounter();
			textTips.updateCursorCounter();
		}

		textX.updateCursorCounter();
		textY.updateCursorCounter();
		textZ.updateCursorCounter();

		super.updateScreen();
		updateUsePlayerCoordsButtonState();
		updateSaveButtonState();
	}

	protected void updateSaveButtonState() {
		if (saveButton == null) {
			return;
		}

		boolean hasValidCoordinates = canParseInt(textX) && canParseInt(textY) && canParseInt(textZ);

		if (isRestoredContext()) {
			saveButton.enabled = hasValidCoordinates;
			return;
		}

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

	protected static boolean hasText(GuiTextField f) {
		return f != null && f.getText() != null && f.getText().trim().length() > 0;
	}

	protected static boolean canParseInt(GuiTextField f) {
		if (f == null || f.getText() == null) {
			return false;
		}
		String s = f.getText().trim();
		if (s.isEmpty() || "-".equals(s)) {
			return false;
		}
		try {
			Integer.parseInt(s);
			return true;
		} catch (Exception ignored) {
			return false;
		}
	}

	@Override
	protected void actionPerformed(GuiButton button) throws IOException {
		if (button.id == BTN_USE_PLAYER_COORDS) {
			applyPlayerCoordsToFields();
			return;
		}

		if (button.id == 20) {
			if (parentScreen != null) {
				Minecraft.getMinecraft().displayGuiScreen(parentScreen);
			} else {
				Minecraft.getMinecraft().displayGuiScreen(null);
			}
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

		if (button.id == 5) {
			if (!saveButton.enabled) {
				return;
			}

			int x = Integer.parseInt(textX.getText().trim());
			int y = Integer.parseInt(textY.getText().trim());
			int z = Integer.parseInt(textZ.getText().trim());

			if (isRestoredContext()) {
				saveLegacyStrategy(x, y, z);
			} else {
				saveNewStrategy(x, y, z);
			}

			Minecraft.getMinecraft().displayGuiScreen(null);
			return;
		}

		if (button.id == 16) {
			this.mc.displayGuiScreen(new GuiEditReminders(parentScreen));
			return;
		}

		super.actionPerformed(button);
	}

	private void saveLegacyStrategy(int x, int y, int z) {
		ParkourMap map = ReminderManager.getSelectedMap();
		if (map == null) {
			return;
		}

		Jump sel = ReminderManager.getSelectedJump();
		if (sel == null) {
			String newJumpId = "Jump" + (ReminderManager.getJumps(map).size() + 1);
			sel = ReminderManager.getOrCreateJumpByNameAndCoords(map, newJumpId, x, y, z);
			ReminderManager.setSelectedJump(sel);
		}

		if (sel != null) {
			sel.setX(x);
			sel.setY(y);
			sel.setZ(z);
		}

		ArrayList<String> lines = new ArrayList<String>();
		lines.add(safe(textLine1.getText()));
		lines.add(safe(textLine2.getText()));
		lines.add(safe(textLine3.getText()));
		lines.add(safe(textLine4.getText()));
		lines.add(safe(textLine5.getText()));

		ReminderManager.createReminder(lines, x, y, z);
	}

	private void saveNewStrategy(int x, int y, int z) {
		Jump jump = ReminderManager.getSelectedJump();
		if (jump != null) {
			jump.setX(x);
			jump.setY(y);
			jump.setZ(z);
		}

		ArrayList<String> lines = new ArrayList<String>();
		lines.add(safe(textPosition.getText()));
		lines.add(safe(textFacing.getText()));
		lines.add(safe(textSetup.getText()));
		lines.add(safe(textStrategy.getText()));
		lines.add(safe(textStrafe.getText()));
		lines.add(safe(textTurn.getText()));
		lines.add(safe(textAuthor.getText()));
		lines.add(safe(textTips.getText()));

		ReminderManager.createReminder(lines, x, y, z);
	}

	protected static String safe(String s) {
		return s != null ? s.trim() : "";
	}

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
		if (isRestoredContext()) {
			textLine1.mouseClicked(mouseX, mouseY, mouseButton);
			textLine2.mouseClicked(mouseX, mouseY, mouseButton);
			textLine3.mouseClicked(mouseX, mouseY, mouseButton);
			textLine4.mouseClicked(mouseX, mouseY, mouseButton);
			textLine5.mouseClicked(mouseX, mouseY, mouseButton);
		} else {
			textPosition.mouseClicked(mouseX, mouseY, mouseButton);
			textFacing.mouseClicked(mouseX, mouseY, mouseButton);

			textSetup.mouseClicked(mouseX, mouseY, mouseButton);
			textStrategy.mouseClicked(mouseX, mouseY, mouseButton);
			textStrafe.mouseClicked(mouseX, mouseY, mouseButton);
			textTurn.mouseClicked(mouseX, mouseY, mouseButton);
			textAuthor.mouseClicked(mouseX, mouseY, mouseButton);
			textTips.mouseClicked(mouseX, mouseY, mouseButton);
		}

		textX.mouseClicked(mouseX, mouseY, mouseButton);
		textY.mouseClicked(mouseX, mouseY, mouseButton);
		textZ.mouseClicked(mouseX, mouseY, mouseButton);

		super.mouseClicked(mouseX, mouseY, mouseButton);
	}

	// Square icon button styled to match vanilla text field look.
	protected class IconSquareButton extends GuiButton {

		private final ResourceLocation icon;

		public IconSquareButton(int id, int x, int y, int w, int h, ResourceLocation icon) {
			super(id, x, y, w, h, "");
			this.icon = icon;
		}

		@Override
		public void drawButton(Minecraft mc, int mouseX, int mouseY) {
			if (!this.visible) {
				return;
			}

			this.hovered = mouseX >= this.xPosition && mouseY >= this.yPosition
					&& mouseX < this.xPosition + this.width && mouseY < this.yPosition + this.height;

			// Match GuiTextField colors (1.8-style)
			int borderColor = 0xFF5A5A5A;
			int bgColor = 0xFF000000;

			if (!this.enabled) {
				borderColor = 0xFF404040;
				bgColor = 0xFF101010;
			} else if (this.hovered) {
				borderColor = 0xFF808080;
			}

			drawRect(this.xPosition, this.yPosition, this.xPosition + this.width, this.yPosition + this.height, borderColor);
			drawRect(this.xPosition + 1, this.yPosition + 1, this.xPosition + this.width - 1, this.yPosition + this.height - 1, bgColor);

			if (icon != null) {
				mc.getTextureManager().bindTexture(icon);
				GlStateManager.color(1f, 1f, 1f, this.enabled ? 1f : 0.6f);

				int ix = this.xPosition + (this.width - 16) / 2;
				int iy = this.yPosition + (this.height - 16) / 2;

				GuiCreateReminder.this.drawModalRectWithCustomSizedTexture(ix, iy, 0, 0, 16, 16, 16, 16);
				GlStateManager.color(1f, 1f, 1f, 1f);
			}
		}
	}
}