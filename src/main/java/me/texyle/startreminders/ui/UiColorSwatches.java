package me.texyle.startreminders.ui;

import java.util.function.Consumer;

/** Picker for the 16 Minecraft chat colors. Values are formatting codes such as "section-sign + b". */
public class UiColorSwatches extends UiWidget {

    private static final char SECTION_SIGN = (char) 0xA7;
    private static final String HEX_DIGITS = "0123456789abcdef";

    private static final int[] RGB = {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
    };

    private static final int COLUMNS = 8;
    private static final int SIZE = 14;
    private static final int GAP = 4;

    private String selectedCode;
    private final Consumer<String> onSelect;

    public UiColorSwatches(String selectedCode, Consumer<String> onSelect) {
        this.selectedCode = selectedCode;
        this.onSelect = onSelect;
        this.w = COLUMNS * SIZE + (COLUMNS - 1) * GAP;
        this.h = 2 * SIZE + GAP;
    }

    public static int rgbOfCode(String code) {
        if (code != null && code.length() == 2 && code.charAt(0) == SECTION_SIGN) {
            int idx = HEX_DIGITS.indexOf(Character.toLowerCase(code.charAt(1)));
            if (idx >= 0) return RGB[idx];
        }
        return 0xFFFFFF;
    }

    public void setSelectedCode(String code) {
        this.selectedCode = code;
    }

    private int cellX(int index) {
        return x + (index % COLUMNS) * (SIZE + GAP);
    }

    private int cellY(int index) {
        return y + (index / COLUMNS) * (SIZE + GAP);
    }

    private static String codeOf(int index) {
        return "" + SECTION_SIGN + HEX_DIGITS.charAt(index);
    }

    @Override
    public void draw(int mouseX, int mouseY) {
        if (!visible) return;

        for (int i = 0; i < RGB.length; i++) {
            int cx = cellX(i);
            int cy = cellY(i);
            boolean selected = codeOf(i).equalsIgnoreCase(selectedCode);
            boolean hover = UiDraw.inside(mouseX, mouseY, cx, cy, SIZE, SIZE);

            int border = selected ? UiTheme.TEXT : (hover ? UiTheme.TEXT_MUTED : UiTheme.CONTROL_BORDER);
            UiDraw.box(cx, cy, SIZE, SIZE, 0xFF000000 | RGB[i], border);
            if (selected) {
                UiDraw.box(cx - 1, cy - 1, SIZE + 2, SIZE + 2, 0x00000000, UiTheme.ACCENT_HOVER);
            }
        }
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (!visible || !enabled || button != 0 || !contains(mouseX, mouseY)) {
            return false;
        }

        for (int i = 0; i < RGB.length; i++) {
            if (UiDraw.inside(mouseX, mouseY, cellX(i), cellY(i), SIZE, SIZE)) {
                selectedCode = codeOf(i);
                if (onSelect != null) onSelect.accept(selectedCode);
                return true;
            }
        }
        return false;
    }
}
