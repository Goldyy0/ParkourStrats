package me.texyle.startreminders.gui.hierarchy;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import me.texyle.startreminders.ui.UiButton;
import me.texyle.startreminders.ui.UiLabel;
import me.texyle.startreminders.ui.UiScreen;
import me.texyle.startreminders.ui.UiTheme;

/** Yes / No question. */
public class GuiConfirm extends UiScreen {

    public interface IConfirmHandler {
        void onYes();
        void onNo();
    }

    private final String title;
    private final String message;
    private final IConfirmHandler handler;

    public GuiConfirm(GuiScreen parent, String title, String message, IConfirmHandler handler) {
        super(parent);
        this.title = title;
        this.message = message;
        this.handler = handler;
    }

    @Override
    protected String getTitle() {
        return title;
    }

    @Override
    protected int getPanelWidth() {
        return 320;
    }

    @Override
    protected int getPanelHeight() {
        return 110;
    }

    @Override
    protected void build(int x, int y, int w, int h) {
        add(new UiLabel(message, UiTheme.TEXT), x, y + 8, w, 10);

        addFooter(
                new UiButton("No", UiButton.Style.SECONDARY, this::onBack),
                new UiButton("Yes", UiButton.Style.PRIMARY, this::confirm));
    }

    private void confirm() {
        if (handler != null) handler.onYes();

        // The handler may have opened another screen; then leave that one alone.
        if (Minecraft.getMinecraft().currentScreen == this) {
            Minecraft.getMinecraft().displayGuiScreen(parent);
        }
    }

    @Override
    protected void onEnter() {
        confirm();
    }

    @Override
    protected void onBack() {
        if (handler != null) handler.onNo();
        Minecraft.getMinecraft().displayGuiScreen(parent);
    }
}
