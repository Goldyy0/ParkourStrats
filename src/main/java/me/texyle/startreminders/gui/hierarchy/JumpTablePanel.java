package me.texyle.startreminders.gui.hierarchy;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;

import me.texyle.startreminders.data.ParkourMap;
import me.texyle.startreminders.gui.GuiScissor;

/** Frame around the jump table: panel geometry, background and the column header row. */
final class JumpTablePanel {

    // Colors (ARGB)
    private static final int COLOR_PANEL_BG = 0xAA0B0B0B;
    private static final int COLOR_PANEL_BORDER = 0xCC2A2A2A;
    private static final int COLOR_HEADER_BG = 0xFF4A4A4A;
    private static final int COLOR_GRID = 0x662A2A2A;

    final int left;
    final int right;
    final int top;
    final int bottom;

    // Header row inside the panel
    final int headerTop;
    final int headerBottom;

    JumpTablePanel(int screenWidth, int screenHeight) {
        left = 10;
        right = screenWidth - 10;
        top = 34;
        bottom = screenHeight - 54;

        headerTop = top + 6;
        headerBottom = headerTop + 16;
    }

    /** Top of the area the table rows are drawn in. */
    int tableTop() {
        return headerBottom + 4;
    }

    int tableBottom() {
        return bottom - 12;
    }

    void drawBackground() {
        Gui.drawRect(left, top, right, bottom, COLOR_PANEL_BG);

        // Keep left/right borders only (top/bottom border removed)
        Gui.drawRect(left, top, left + 1, bottom, COLOR_PANEL_BORDER);
        Gui.drawRect(right - 1, top, right, bottom, COLOR_PANEL_BORDER);

        Gui.drawRect(left + 1, headerTop - 2, right - 1, headerBottom + 2, COLOR_HEADER_BG);

        // Subtle separator under header
        Gui.drawRect(left + 1, headerBottom + 2, right - 1, headerBottom + 3, COLOR_GRID);
    }

    void drawHeader(FontRenderer font, ParkourMap map, GuiJumpTableSlot table) {
        // Header needs its own scissor because it's not drawn by GuiSlot.
        int clipLeft = left + 1;
        int clipRight = right - 1;
        int clipTop = headerTop - 2;
        int clipBottom = headerBottom + 2;

        GuiScissor.enable(Minecraft.getMinecraft(), clipLeft, clipTop, clipRight - clipLeft, clipBottom - clipTop);
        try {
            int xStart = table.getHeaderStartX();
            int y = headerTop;

            int x = xStart;

            drawHeaderCell(font, "Row", x, y, GuiJumpTableSlot.COL_ROW_W);
            x += GuiJumpTableSlot.COL_ROW_W;

            // Section headers based on map config
            int sc = (map != null) ? map.getEffectiveSectionsCount() : 0;
            for (int i = 0; i < sc; i++) {
                String name = (map != null) ? map.getSectionName(i) : "";
                drawHeaderCell(font, name, x, y, GuiJumpTableSlot.COL_SECTION_W);
                x += GuiJumpTableSlot.COL_SECTION_W;
            }

            drawHeaderCell(font, "Jump", x, y, GuiJumpTableSlot.COL_JUMP_W); x += GuiJumpTableSlot.COL_JUMP_W;
            drawHeaderCell(font, "Position", x, y, GuiJumpTableSlot.COL_POS_W); x += GuiJumpTableSlot.COL_POS_W;
            drawHeaderCell(font, "Facing", x, y, GuiJumpTableSlot.COL_FACING_W); x += GuiJumpTableSlot.COL_FACING_W;
            drawHeaderCell(font, "Setup", x, y, GuiJumpTableSlot.COL_SETUP_W); x += GuiJumpTableSlot.COL_SETUP_W;
            drawHeaderCell(font, "Strategy", x, y, GuiJumpTableSlot.COL_STRAT_W); x += GuiJumpTableSlot.COL_STRAT_W;
            drawHeaderCell(font, "Strafe", x, y, GuiJumpTableSlot.COL_STRAFE_W); x += GuiJumpTableSlot.COL_STRAFE_W;
            drawHeaderCell(font, "Turn", x, y, GuiJumpTableSlot.COL_TURN_W); x += GuiJumpTableSlot.COL_TURN_W;
            drawHeaderCell(font, "Author", x, y, GuiJumpTableSlot.COL_AUTHOR_W); x += GuiJumpTableSlot.COL_AUTHOR_W;

            int tipsW = Math.max(40, table.getVisibleContentWidth() - (x - xStart));
            drawHeaderCell(font, "Tips", x, y, tipsW);

            table.drawVerticalSeparators(headerTop - 2, headerBottom + 2);
        } finally {
            GuiScissor.disable();
        }
    }

    private static void drawHeaderCell(FontRenderer font, String text, int x, int y, int w) {
        if (w <= 6) {
            return;
        }
        String trimmed = font.trimStringToWidth(text, Math.max(0, w - 6));
        font.drawString(trimmed, x + 3, y + 3, 0xFFFFFF, true);
    }
}
