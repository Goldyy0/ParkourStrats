package me.texyle.startreminders.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;

/** Square button that shows a 16x16 icon instead of text. */
final class IconSquareButton extends GuiButton {

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
