package me.texyle.startreminders.ui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Mouse;

/**
 * Base of the mod's dialogs: a centered panel with a title and a close button over a dimmed world.
 * Subclasses add widgets in {@link #build}; the base routes mouse, wheel and keys to them.
 */
public abstract class UiScreen extends GuiScreen {

    protected final GuiScreen parent;
    protected final UiPopup popup = new UiPopup();

    private final List<UiWidget> widgets = new ArrayList<UiWidget>();
    private UiButton closeButton;

    protected int panelX;
    protected int panelY;
    protected int panelW;
    protected int panelH;

    /** Area inside the panel below the title, where subclasses lay out their content. */
    protected int contentX;
    protected int contentY;
    protected int contentW;
    protected int contentH;

    protected UiScreen(GuiScreen parent) {
        this.parent = parent;
    }

    // -------------------------
    // Subclass hooks
    // -------------------------

    protected abstract String getTitle();

    protected abstract int getPanelWidth();

    protected abstract int getPanelHeight();

    /** Creates the widgets (via {@link #add}) for the given content area. */
    protected abstract void build(int x, int y, int w, int h);

    /** Extra drawing between the panel and the widgets (labels, previews). */
    protected void drawContent(int mouseX, int mouseY, float partialTicks) { }

    /** Enter pressed outside a text-field edit; default does nothing. */
    protected void onEnter() { }

    /** Back / close / Escape; default returns to the parent screen. */
    protected void onBack() {
        Minecraft.getMinecraft().displayGuiScreen(parent);
    }

    // -------------------------
    // Widgets
    // -------------------------

    protected <W extends UiWidget> W add(W widget) {
        widgets.add(widget);
        return widget;
    }

    protected <W extends UiWidget> W add(W widget, int x, int y, int w, int h) {
        widget.setBounds(x, y, w, h);
        widgets.add(widget);
        return widget;
    }

    /** Places buttons right-aligned in the footer row of the panel, last argument rightmost. */
    protected void addFooter(UiButton... buttons) {
        int right = panelX + panelW - UiTheme.PADDING - 2;
        int y = panelY + panelH - UiTheme.PADDING - UiTheme.CONTROL_HEIGHT;
        for (int i = buttons.length - 1; i >= 0; i--) {
            int bw = Math.max(70, buttons[i].preferredWidth());
            add(buttons[i], right - bw, y, bw, UiTheme.CONTROL_HEIGHT);
            right -= bw + 8;
        }
    }

    /** Y of the footer row; content should end above it. */
    protected int footerY() {
        return panelY + panelH - UiTheme.PADDING - UiTheme.CONTROL_HEIGHT;
    }

    // -------------------------
    // Lifecycle
    // -------------------------

    @Override
    public void initGui() {
        super.initGui();
        popup.close();
        widgets.clear();

        panelW = Math.min(getPanelWidth(), this.width - 16);
        panelH = Math.min(getPanelHeight(), this.height - 16);
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;

        int pad = UiTheme.PADDING;
        contentX = panelX + pad + 2;
        contentY = panelY + 34;
        contentW = panelW - (pad + 2) * 2;
        contentH = panelH - 34 - pad - UiTheme.CONTROL_HEIGHT - pad;

        closeButton = new UiButton("X", UiButton.Style.DANGER, this::onBack);
        closeButton.setBounds(panelX + panelW - pad - 20, panelY + pad - 2, 20, 20);

        build(contentX, contentY, contentW, contentH);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();

        UiDraw.box(panelX, panelY, panelW, panelH, UiTheme.PANEL_BG, UiTheme.PANEL_BORDER);
        UiDraw.text(getTitle().toUpperCase(), panelX + UiTheme.PADDING + 2, panelY + 11, UiTheme.TEXT);
        UiDraw.fill(panelX + 1, panelY + 28, panelW - 2, 1, UiTheme.PANEL_BORDER);

        closeButton.draw(mouseX, mouseY);
        drawContent(mouseX, mouseY, partialTicks);

        for (UiWidget widget : widgets) {
            widget.draw(mouseX, mouseY);
        }

        popup.draw(mouseX, mouseY);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (popup.isOpen()) {
            popup.mouseClicked(mouseX, mouseY, mouseButton);
            return;
        }

        if (closeButton.mouseClicked(mouseX, mouseY, mouseButton)) {
            return;
        }

        for (UiWidget widget : widgets) {
            if (widget instanceof UiTextField && !widget.contains(mouseX, mouseY)) {
                ((UiTextField) widget).setFocused(false);
            }
        }

        // Later widgets are drawn on top, so they get the click first.
        for (int i = widgets.size() - 1; i >= 0; i--) {
            if (widgets.get(i).mouseClicked(mouseX, mouseY, mouseButton)) {
                return;
            }
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();

        int delta = Mouse.getEventDWheel();
        if (delta == 0) return;

        int mouseX = Mouse.getEventX() * this.width / this.mc.displayWidth;
        int mouseY = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;

        if (popup.isOpen()) {
            popup.wheel(delta);
            return;
        }

        for (int i = widgets.size() - 1; i >= 0; i--) {
            if (widgets.get(i).mouseWheel(mouseX, mouseY, delta)) {
                return;
            }
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1) { // Esc
            if (popup.isOpen()) {
                popup.close();
            } else {
                onBack();
            }
            return;
        }

        if (popup.isOpen()) return;

        if (keyCode == 15) { // Tab: next text field
            focusNextTextField();
            return;
        }

        for (UiWidget widget : widgets) {
            if (widget.keyTyped(typedChar, keyCode)) {
                return;
            }
        }

        if (keyCode == 28 || keyCode == 156) { // Enter
            onEnter();
        }
    }

    private void focusNextTextField() {
        List<UiTextField> fields = new ArrayList<UiTextField>();
        for (UiWidget widget : widgets) {
            if (widget instanceof UiTextField && widget.visible) fields.add((UiTextField) widget);
        }
        if (fields.isEmpty()) return;

        int current = -1;
        for (int i = 0; i < fields.size(); i++) {
            if (fields.get(i).isFocused()) current = i;
        }
        for (UiTextField f : fields) f.setFocused(false);
        fields.get((current + 1) % fields.size()).setFocused(true);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        for (UiWidget widget : widgets) {
            widget.update();
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
