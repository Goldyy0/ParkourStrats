package me.texyle.startreminders.gui.convert;

import java.io.IOException;

import me.texyle.startreminders.data.Jump;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.EnumChatFormatting;

public class GuiConvertStep2Editor extends GuiScreen {

    private final GuiScreen parent;
    private final Jump legacyJump;
    private final String newJumpName;

    private GuiLegacyPreviewPanel legacyPanel;

    private GuiButton cancelButton;
    private GuiButton saveButton;

    public GuiConvertStep2Editor(GuiScreen parent, Jump legacyJump, String newJumpName) {
        this.parent = parent;
        this.legacyJump = legacyJump;
        this.newJumpName = newJumpName;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();

        legacyPanel = new GuiLegacyPreviewPanel(
                this.fontRendererObj,
                10,
                40,
                (this.width / 2) - 20,
                this.height - 80,
                legacyJump
        );

        cancelButton = new GuiButton(1, this.width / 2 - 110, this.height - 30, 100, 20, "Cancel");
        saveButton = new GuiButton(2, this.width / 2 + 10, this.height - 30, 100, 20, "Convert & Save");

        this.buttonList.add(cancelButton);
        this.buttonList.add(saveButton);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 1) {
            Minecraft.getMinecraft().displayGuiScreen(parent);
            return;
        }

        if (button.id == 2) {
            // Conversion logic will be added here later
            Minecraft.getMinecraft().displayGuiScreen(parent);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();

        drawCenteredString(
                this.fontRendererObj,
                EnumChatFormatting.AQUA + "Convert to new format: " + newJumpName,
                this.width / 2,
                12,
                0xFFFFFF
        );

        legacyPanel.draw();

        // Right side placeholder
        drawCenteredString(
                this.fontRendererObj,
                EnumChatFormatting.GRAY + "New strategy editor (right side)",
                (this.width * 3) / 4,
                this.height / 2,
                0x888888
        );

        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}