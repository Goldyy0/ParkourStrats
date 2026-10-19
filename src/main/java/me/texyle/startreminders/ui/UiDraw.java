package me.texyle.startreminders.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

import me.texyle.startreminders.gui.GuiDraw;

/** Drawing helpers shared by the UI widgets. */
public final class UiDraw {

    private UiDraw() {}

    public static FontRenderer font() {
        return Minecraft.getMinecraft().fontRendererObj;
    }

    /** Fills a rectangle and outlines it with a 1px border. */
    public static void box(int x, int y, int w, int h, int fill, int border) {
        GuiDraw.fillRect(x, y, x + w, y + h, fill);
        GuiDraw.fillRect(x, y, x + w, y + 1, border);
        GuiDraw.fillRect(x, y + h - 1, x + w, y + h, border);
        GuiDraw.fillRect(x, y, x + 1, y + h, border);
        GuiDraw.fillRect(x + w - 1, y, x + w, y + h, border);
    }

    public static void fill(int x, int y, int w, int h, int color) {
        GuiDraw.fillRect(x, y, x + w, y + h, color);
    }

    public static void text(String s, int x, int y, int color) {
        font().drawString(s, x, y, color, true);
    }

    public static void centered(String s, int centerX, int y, int color) {
        FontRenderer f = font();
        f.drawString(s, centerX - f.getStringWidth(s) / 2, y, color, true);
    }

    /** Text vertically centered in a box of the given height. */
    public static void textInBox(String s, int x, int boxY, int boxH, int color) {
        text(s, x, boxY + (boxH - font().FONT_HEIGHT) / 2 + 1, color);
    }

    public static int width(String s) {
        return font().getStringWidth(s);
    }

    /** Shortens the text so it fits the given pixel width. */
    public static String fit(String s, int maxWidth) {
        if (s == null) return "";
        return font().trimStringToWidth(s, Math.max(0, maxWidth));
    }

    /** Like {@link #fit}, but ends in "..." when something was cut off. */
    public static String ellipsize(String s, int maxWidth) {
        if (s == null) return "";
        if (width(s) <= maxWidth) return s;
        return font().trimStringToWidth(s, Math.max(0, maxWidth - width("..."))) + "...";
    }

    public static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** Card with a small uppercase title. Content starts 22px below its top. */
    public static void card(int x, int y, int w, int h, String title) {
        box(x, y, w, h, UiTheme.CARD_BG, UiTheme.CARD_BORDER);
        if (title != null) {
            text(title.toUpperCase(), x + UiTheme.PADDING, y + 7, UiTheme.TEXT_MUTED);
        }
    }
}
