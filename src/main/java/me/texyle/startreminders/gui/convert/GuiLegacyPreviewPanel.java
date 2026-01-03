package me.texyle.startreminders.gui.convert;

import java.util.ArrayList;

import me.texyle.startreminders.data.Jump;
import me.texyle.startreminders.reminders.Reminder;
import net.minecraft.client.gui.FontRenderer;

public class GuiLegacyPreviewPanel {

    private final FontRenderer font;
    private final int x, y, w, h;
    private final Jump legacyJump;

    public GuiLegacyPreviewPanel(FontRenderer font, int x, int y, int w, int h, Jump legacyJump) {
        this.font = font;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.legacyJump = legacyJump;
    }

    public void draw() {
        int lineY = y;

        font.drawString("Legacy strategy (read-only)", x, lineY, 0xAAAAAA);
        lineY += 14;

        if (legacyJump == null || legacyJump.getReminders() == null || legacyJump.getReminders().isEmpty()) {
            font.drawString("No legacy data", x, lineY, 0xFF5555);
            return;
        }

        Reminder r = legacyJump.getReminders().get(0);
        ArrayList<String> lines = r.lines;

        if (lines != null) {
            // If normalized to 8-line format, display legacy-like lines (Setup..Author)
            int start = (lines.size() >= 8) ? 2 : 0;
            int endExclusive = (lines.size() >= 8) ? Math.min(lines.size(), 7) : lines.size(); // show up to index 6

            for (int i = start; i < endExclusive; i++) {
                String s = lines.get(i);
                if (s == null || s.trim().isEmpty()) {
                    continue;
                }
                font.drawString("- " + s, x, lineY, 0xFFFFFF);
                lineY += 12;
            }
        }

        lineY += 10;
        font.drawString(
                "Coords: " + legacyJump.getX() + ", " + legacyJump.getY() + ", " + legacyJump.getZ(),
                x,
                lineY,
                0xAAAAAA
        );
    }
}