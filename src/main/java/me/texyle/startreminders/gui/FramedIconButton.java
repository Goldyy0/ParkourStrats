package me.texyle.startreminders.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;

/** Square button with a text-field style frame that shows a 16x16 icon. */
final class FramedIconButton extends GuiButton {

    private final ResourceLocation icon;

    FramedIconButton(int id, int x, int y, int w, int h, ResourceLocation icon) {
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

        Gui.drawRect(this.xPosition, this.yPosition, this.xPosition + this.width, this.yPosition + this.height, borderColor);
        Gui.drawRect(this.xPosition + 1, this.yPosition + 1, this.xPosition + this.width - 1, this.yPosition + this.height - 1, bgColor);

        if (icon != null) {
            mc.getTextureManager().bindTexture(icon);
            GlStateManager.color(1f, 1f, 1f, this.enabled ? 1f : 0.6f);

            int ix = this.xPosition + (this.width - 16) / 2;
            int iy = this.yPosition + (this.height - 16) / 2;

            Gui.drawModalRectWithCustomSizedTexture(ix, iy, 0, 0, 16, 16, 16, 16);
            GlStateManager.color(1f, 1f, 1f, 1f);
        }
    }
}
