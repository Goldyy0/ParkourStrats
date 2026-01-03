package me.texyle.startreminders.gui.convert;

import java.io.IOException;

import me.texyle.startreminders.data.Jump;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.EnumChatFormatting;

public class GuiConvertStep1Name extends GuiScreen {

    private final GuiScreen parent;
    private final Jump legacyJump;

    private GuiTextField nameField;
    private GuiButton confirmButton;
    private GuiButton cancelButton;

    public GuiConvertStep1Name(GuiScreen parent, Jump legacyJump) {
        this.parent = parent;
        this.legacyJump = legacyJump;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();

        int centerX = this.width / 2;
        int y = this.height / 2 - 20;

        nameField = new GuiTextField(0, this.fontRendererObj, centerX - 100, y, 200, 20);
        nameField.setMaxStringLength(48);
        nameField.setFocused(true);

        confirmButton = new GuiButton(1, centerX - 100, y + 30, 98, 20, "Continue");
        cancelButton = new GuiButton(2, centerX + 2, y + 30, 98, 20, "Cancel");

        this.buttonList.add(confirmButton);
        this.buttonList.add(cancelButton);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 2) {
            Minecraft.getMinecraft().displayGuiScreen(parent);
            return;
        }

        if (button.id == 1) {
            String name = nameField.getText().trim();
            if (name.isEmpty()) {
                return;
            }

            Minecraft.getMinecraft().displayGuiScreen(
                    new GuiConvertStep2Editor(parent, legacyJump, name)
            );
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        nameField.textboxKeyTyped(typedChar, keyCode);
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        nameField.mouseClicked(mouseX, mouseY, mouseButton);
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();

        drawCenteredString(
                this.fontRendererObj,
                EnumChatFormatting.AQUA + "Convert legacy strategy",
                this.width / 2,
                this.height / 2 - 60,
                0xFFFFFF
        );

        drawCenteredString(
                this.fontRendererObj,
                "Enter new jump name (will be created in Global dataset)",
                this.width / 2,
                this.height / 2 - 40,
                0xAAAAAA
        );

        nameField.drawTextBox();

        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}