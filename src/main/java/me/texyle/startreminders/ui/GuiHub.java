package me.texyle.startreminders.ui;

import java.io.IOException;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Mouse;

/**
 * The mod's main window: one panel with tabs instead of a chain of separate screens.
 * Dialogs (editors, prompts) open on top of it and return here.
 */
public class GuiHub extends GuiScreen {

    public static final int TAB_STRATEGIES = 0;
    public static final int TAB_LIBRARY = 1;
    public static final int TAB_SECTIONS = 2;
    public static final int TAB_SETTINGS = 3;

    private static final String[] TAB_NAMES = { "Strategies", "Library", "Sections", "Settings" };

    private static final int MARGIN = 20;
    private static final int BAR_H = 24;

    private final GuiScreen parent;
    private final UiPopup popup = new UiPopup();

    private final HubTab[] tabs;
    private int activeTab;

    private UiTabBar tabBar;
    private UiButton closeButton;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;

    /** Set while one mouse event is handled; stops list widgets from reacting to a click the UI already used. */
    private boolean clickConsumed;

    public GuiHub(GuiScreen parent) {
        this(parent, TAB_STRATEGIES);
    }

    public GuiHub(GuiScreen parent, int initialTab) {
        this.parent = parent;
        this.activeTab = initialTab;
        this.tabs = new HubTab[] {
                new HubStrategiesTab(this, popup),
                new HubLibraryTab(this, popup),
                new HubSectionsTab(this, popup),
                new HubSettingsTab(this)
        };
    }

    /** Opens another screen on top of the hub. */
    void open(GuiScreen screen) {
        Minecraft.getMinecraft().displayGuiScreen(screen);
    }

    /** Switches to a tab (it refreshes itself from the shared selection). */
    void showTab(int index) {
        activeTab = index;
        tabBar.setSelected(index);
        tabs[index].onShow();
    }

    @Override
    public void initGui() {
        super.initGui();
        popup.close();

        panelX = MARGIN;
        panelY = MARGIN;
        panelW = this.width - MARGIN * 2;
        panelH = this.height - MARGIN * 2;

        int pad = UiTheme.PADDING;

        tabBar = new UiTabBar(TAB_NAMES, activeTab, index -> {
            activeTab = index;
            tabs[index].onShow();
        });
        tabBar.setBounds(panelX + pad, panelY + pad, 340, BAR_H);

        closeButton = new UiButton("X", UiButton.Style.DANGER, this::close);
        closeButton.setBounds(panelX + panelW - pad - 22, panelY + pad + 1, 22, 22);

        int contentX = panelX + pad + 2;
        int contentY = panelY + pad + BAR_H + pad;
        int contentW = panelW - (pad + 2) * 2;
        int contentH = panelH - (contentY - panelY) - pad;

        for (HubTab tab : tabs) {
            tab.layout(contentX, contentY, contentW, contentH);
        }
        tabs[activeTab].onShow();
    }

    private void close() {
        Minecraft.getMinecraft().displayGuiScreen(parent);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();

        UiDraw.box(panelX, panelY, panelW, panelH, UiTheme.PANEL_BG, UiTheme.PANEL_BORDER);

        // While the popup is open nothing under it reacts to the mouse.
        int hoverX = popup.isOpen() ? -9999 : mouseX;
        int hoverY = popup.isOpen() ? -9999 : mouseY;

        tabBar.draw(hoverX, hoverY);
        closeButton.draw(hoverX, hoverY);

        tabs[activeTab].draw(hoverX, hoverY, partialTicks);

        // The popup is drawn last so it floats above everything.
        popup.draw(mouseX, mouseY);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (popup.isOpen()) {
            popup.mouseClicked(mouseX, mouseY, mouseButton);
            clickConsumed = true;
            return;
        }

        if (closeButton.mouseClicked(mouseX, mouseY, mouseButton)
                || tabBar.mouseClicked(mouseX, mouseY, mouseButton)
                || tabs[activeTab].mouseClicked(mouseX, mouseY, mouseButton)) {
            clickConsumed = true;
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        clickConsumed = false;
        super.handleMouseInput();

        int delta = Mouse.getEventDWheel();

        if (popup.isOpen()) {
            popup.wheel(delta);
            return;
        }

        if (delta != 0) {
            int mouseX = Mouse.getEventX() * this.width / this.mc.displayWidth;
            int mouseY = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;
            if (tabs[activeTab].mouseWheel(mouseX, mouseY, delta)) {
                return;
            }
        }

        if (!clickConsumed) {
            tabs[activeTab].handleMouseInput();
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1 && popup.isOpen()) {
            popup.close();
            return;
        }
        if (!popup.isOpen() && tabs[activeTab].keyTyped(typedChar, keyCode)) {
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        tabs[activeTab].update();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
