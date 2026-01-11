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

        int idx = legacyJump.getActiveReminderIndex();
        if (idx < 0 || idx >= legacyJump.getReminders().size()) {
            idx = 0;
        }

        Reminder r = legacyJump.getReminders().get(idx);
        ArrayList<String> lines = (r != null) ? r.lines : null;

        if (lines != null) {
            // Requested display:
            // position -> line 1
            // facing   -> line 2
            // setup    -> line 3
            // input    -> line 4 (Strategy)
            // comment  -> line 5 (Tips)
            if (lines.size() >= 8) {
                int[] map = new int[] { 0, 1, 2, 3, 7 };

                for (int i = 0; i < map.length; i++) {
                    int realIndex = map[i];
                    String s = (realIndex >= 0 && realIndex < lines.size()) ? lines.get(realIndex) : "";
                    if (s == null || s.trim().isEmpty()) {
                        continue;
                    }
                    font.drawString((i + 1) + ") " + s, x, lineY, 0xFFFFFF);
                    lineY += 12;
                }
            } else {
                // Old legacy: show up to first 5 non-empty lines
                int shown = 0;
                for (int i = 0; i < lines.size() && shown < 5; i++) {
                    String s = lines.get(i);
                    if (s == null || s.trim().isEmpty()) {
                        continue;
                    }
                    font.drawString((shown + 1) + ") " + s, x, lineY, 0xFFFFFF);
                    lineY += 12;
                    shown++;
                }
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