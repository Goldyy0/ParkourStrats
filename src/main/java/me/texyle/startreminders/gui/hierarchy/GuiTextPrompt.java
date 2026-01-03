package me.texyle.startreminders.gui.hierarchy;

import java.io.IOException;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

public class GuiTextPrompt extends GuiScreen {

    public interface IResultHandler {
        void onConfirm(String text);
        void onCancel();
    }

    private final GuiScreen parent;
    private final String title;
    private final String initialText;
    private final IResultHandler handler;

    private GuiTextField textField;
    private GuiButton okButton;
    private GuiButton cancelButton;

    public GuiTextPrompt(GuiScreen parent, String title, String initialText, IResultHandler handler) {
        this.parent = parent;
        this.title = title;
        this.initialText = initialText != null ? initialText : "";
        this.handler = handler;
    }

    @Override
    public void initGui() {
        super.initGui();

        int w = 220;
        int x = (this.width - w) / 2;
        int y = this.height / 2 - 10;

        textField = new GuiTextField(0, this.fontRendererObj, x, y, w, 20);
        textField.setFocused(true);
        textField.setText(initialText);
        textField.setMaxStringLength(512);

        okButton = new GuiButton(1, this.width / 2 - 110, y + 30, 100, 20, "OK");
        cancelButton = new GuiButton(2, this.width / 2 + 10, y + 30, 100, 20, "Cancel");

        this.buttonList.add(okButton);
        this.buttonList.add(cancelButton);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        this.drawCenteredString(this.fontRendererObj, title, this.width / 2, this.height / 2 - 40, 0xFFFFFF);

        textField.drawTextBox();

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 1) {
            if (handler != null) {
                handler.onConfirm(textField.getText());
            }
            Minecraft.getMinecraft().displayGuiScreen(parent);
        } else if (button.id == 2) {
            if (handler != null) {
                handler.onCancel();
            }
            Minecraft.getMinecraft().displayGuiScreen(parent);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        textField.textboxKeyTyped(typedChar, keyCode);

        // Enter
        if (keyCode == 28) {
            if (handler != null) {
                handler.onConfirm(textField.getText());
            }
            Minecraft.getMinecraft().displayGuiScreen(parent);
            return;
        }

        // Esc
        if (keyCode == 1) {
            if (handler != null) {
                handler.onCancel();
            }
            Minecraft.getMinecraft().displayGuiScreen(parent);
            return;
        }

        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        textField.mouseClicked(mouseX, mouseY, mouseButton);
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }
}