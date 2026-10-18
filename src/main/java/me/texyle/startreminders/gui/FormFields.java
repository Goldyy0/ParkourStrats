package me.texyle.startreminders.gui;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiTextField;

/** Small helpers for the text-field forms. */
public final class FormFields {

    private static final int COLOR_INVALID_OVERLAY = 0x55FF0000;

    private FormFields() {}

    public static boolean hasText(GuiTextField f) {
        return f != null && f.getText() != null && f.getText().trim().length() > 0;
    }

    /** True if the field holds a whole number ("-" alone does not count). */
    public static boolean canParseInt(GuiTextField f) {
        if (f == null || f.getText() == null) return false;

        String s = f.getText().trim();
        if (s.isEmpty() || "-".equals(s)) return false;

        try {
            Integer.parseInt(s);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    /** Makes the field display-only. */
    public static void setReadOnly(GuiTextField f) {
        if (f == null) return;
        f.setCanLoseFocus(true);
        f.setEnabled(false);
    }

    public static String trimmed(String s) {
        return s != null ? s.trim() : "";
    }

    /** Tints the field red to flag invalid input. */
    public static void drawInvalidOverlay(GuiTextField field) {
        if (field == null) {
            return;
        }
        int left = field.xPosition - 1;
        int top = field.yPosition - 1;
        int right = field.xPosition + field.width + 1;
        int bottom = field.yPosition + field.height + 1;
        Gui.drawRect(left, top, right, bottom, COLOR_INVALID_OVERLAY);
    }
}
