package me.texyle.startreminders.ui;

/** A control drawn and clicked by a window; position and size are set by the layout. */
public abstract class UiWidget {

    public int x;
    public int y;
    public int w;
    public int h = UiTheme.CONTROL_HEIGHT;

    public boolean visible = true;
    public boolean enabled = true;

    public void setBounds(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    public boolean contains(int mx, int my) {
        return UiDraw.inside(mx, my, x, y, w, h);
    }

    public abstract void draw(int mouseX, int mouseY);

    /** @return true if the click was used by this widget */
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        return false;
    }

    /** @return true if the wheel was used by this widget (positive delta = up) */
    public boolean mouseWheel(int mouseX, int mouseY, int delta) {
        return false;
    }

    /** @return true if the key was used by this widget */
    public boolean keyTyped(char typedChar, int keyCode) {
        return false;
    }

    /** Called every tick (e.g. for cursor blinking). */
    public void update() { }
}
