package me.texyle.startreminders.gui.hierarchy;

import java.io.IOException;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

public class GuiConfirm extends GuiScreen {

    public interface IConfirmHandler {
        void onYes();
        void onNo();
    }

    private final GuiScreen parent;
    private final String title;
    private final String message;
    private final IConfirmHandler handler;

    public GuiConfirm(GuiScreen parent, String title, String message, IConfirmHandler handler) {
        this.parent = parent;
        this.title = title;
        this.message = message;
        this.handler = handler;
    }

    @Override
    public void initGui() {
        super.initGui();

        int y = this.height / 2 + 10;
        this.buttonList.add(new GuiButton(1, this.width / 2 - 110, y, 100, 20, "Yes"));
        this.buttonList.add(new GuiButton(2, this.width / 2 + 10, y, 100, 20, "No"));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        this.drawCenteredString(this.fontRendererObj, title, this.width / 2, this.height / 2 - 40, 0xFFFFFF);
        this.drawCenteredString(this.fontRendererObj, message, this.width / 2, this.height / 2 - 20, 0xFFFFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 1) {
            if (handler != null) handler.onYes();
            Minecraft.getMinecraft().displayGuiScreen(parent);
        } else if (button.id == 2) {
            if (handler != null) handler.onNo();
            Minecraft.getMinecraft().displayGuiScreen(parent);
        }
    }
}