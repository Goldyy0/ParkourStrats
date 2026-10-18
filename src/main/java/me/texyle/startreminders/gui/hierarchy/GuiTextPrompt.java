package me.texyle.startreminders.gui.hierarchy;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import me.texyle.startreminders.ui.UiButton;
import me.texyle.startreminders.ui.UiScreen;
import me.texyle.startreminders.ui.UiTextField;

/** Small dialog asking for one line of text. */
public class GuiTextPrompt extends UiScreen {

    public interface IResultHandler {
        void onConfirm(String text);
        void onCancel();
    }

    private final String title;
    private final String initialText;
    private final IResultHandler handler;

    private UiTextField field;

    public GuiTextPrompt(GuiScreen parent, String title, String initialText, IResultHandler handler) {
        super(parent);
        this.title = title;
        this.initialText = initialText != null ? initialText : "";
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
        return 120;
    }

    @Override
    protected void build(int x, int y, int w, int h) {
        // Keep what the user typed when the screen is rebuilt (e.g. window resize).
        String current = field != null ? field.getText() : initialText;

        field = add(new UiTextField(current, 512), x, y + 4, w, 20);
        field.setFocused(true);

        addFooter(
                new UiButton("Cancel", UiButton.Style.SECONDARY, this::onBack),
                new UiButton("OK", UiButton.Style.PRIMARY, this::confirm));
    }

    private void confirm() {
        if (handler != null) {
            handler.onConfirm(field.getText());
        }
        returnToParentUnlessReplaced();
    }

    /** The handler may have opened another screen (e.g. the next step of a flow); then leave that one alone. */
    private void returnToParentUnlessReplaced() {
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
        if (handler != null) {
            handler.onCancel();
        }
        Minecraft.getMinecraft().displayGuiScreen(parent);
    }
}
