package me.texyle.startreminders.ui;

/** Content of one tab of the {@link GuiHub} window. */
interface HubTab {

    /** Called whenever the window is (re)built; the tab gets its content area. */
    void layout(int x, int y, int w, int h);

    /** Called when the user switches to this tab, so it can refresh what other tabs may have changed. */
    default void onShow() { }

    void draw(int mouseX, int mouseY, float partialTicks);

    /** @return true if the click was used */
    boolean mouseClicked(int mouseX, int mouseY, int button);

    /** @return true if the wheel was used (positive delta = up) */
    default boolean mouseWheel(int mouseX, int mouseY, int delta) {
        return false;
    }

    /** For widgets that read mouse events themselves (lists, scrollbars). */
    default void handleMouseInput() { }

    /** @return true if the key was used (Escape then does not close the window) */
    default boolean keyTyped(char typedChar, int keyCode) {
        return false;
    }

    /** Called every tick (cursor blinking and the like). */
    default void update() { }
}
