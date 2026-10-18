package me.texyle.startreminders.ui;

import java.util.ArrayList;
import java.util.List;

import me.texyle.startreminders.gui.GuiDraw;
import me.texyle.startreminders.reminders.UserPreferences;

/** All user options in one place: how strategies look in the world and how the edit key behaves. */
final class HubSettingsTab implements HubTab {

    private static final char SECTION_SIGN = (char) 0xA7;

    private static final int DISPLAY_CARD_H = 168;
    private static final int BEHAVIOR_CARD_H = 98;
    private static final int CARD_GAP = 8;
    private static final int COLUMN_GAP = 12;

    private int x;
    private int y;
    private int w;
    private int h;

    private int leftW;

    private final UiToggle showJumpName;
    private final UiColorSwatches jumpNameColor;
    private final UiColorSwatches textColor;
    private final UiSegmented pickMode;
    private final UiSegmented sectionsMode;

    private final List<UiWidget> widgets = new ArrayList<UiWidget>();

    HubSettingsTab() {
        showJumpName = new UiToggle(UserPreferences.isGlobalShowJumpNameEnabled(),
                on -> UserPreferences.setGlobalShowJumpNameEnabled(on.booleanValue()));

        jumpNameColor = new UiColorSwatches(UserPreferences.getGlobalInWorldJumpNameColor(),
                UserPreferences::setGlobalInWorldJumpNameColor);

        textColor = new UiColorSwatches(UserPreferences.getGlobalInWorldTextColor(),
                UserPreferences::setGlobalInWorldTextColor);

        pickMode = new UiSegmented(new String[] { "Nearest", "Crosshair" },
                UserPreferences.isEditPickModeInCrosshair() ? 1 : 0,
                index -> UserPreferences.setEditPickModeInCrosshair(index == 1));

        widgets.add(showJumpName);
        widgets.add(jumpNameColor);
        widgets.add(textColor);
        sectionsMode = new UiSegmented(new String[] { "Compact", "Full" },
                UserPreferences.isSectionsCompact() ? 0 : 1,
                index -> UserPreferences.setSectionsCompact(index == 0));

        widgets.add(pickMode);
        widgets.add(sectionsMode);
    }

    @Override
    public void layout(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;

        leftW = Math.min(Math.max(300, (int) (w * 0.55)), w - 180 - COLUMN_GAP);

        int right = x + leftW - UiTheme.PADDING;

        int rowTop = y + 24;
        showJumpName.x = right - showJumpName.w;
        showJumpName.y = rowTop + 9;

        rowTop += 34;
        jumpNameColor.x = right - jumpNameColor.w;
        jumpNameColor.y = rowTop + 6;

        rowTop += 52;
        textColor.x = right - textColor.w;
        textColor.y = rowTop + 6;

        int behaviorTop = y + DISPLAY_CARD_H + CARD_GAP;
        pickMode.x = right - pickMode.w;
        pickMode.y = behaviorTop + 24 + 8;
        sectionsMode.x = right - sectionsMode.w;
        sectionsMode.y = behaviorTop + 24 + 34 + 8;
    }

    @Override
    public void draw(int mouseX, int mouseY, float partialTicks) {
        int labelX = x + UiTheme.PADDING;

        UiDraw.card(x, y, leftW, DISPLAY_CARD_H, "In-world display");

        int rowTop = y + 24;
        row(labelX, rowTop, "Show jump name", "Name of the jump above its strategy");
        rowTop += 34;
        row(labelX, rowTop, "Jump name color", "Color of the name line");
        rowTop += 52;
        row(labelX, rowTop, "Strategy text color", "Color of the strategy lines");

        int behaviorTop = y + DISPLAY_CARD_H + CARD_GAP;
        UiDraw.card(x, behaviorTop, leftW, BEHAVIOR_CARD_H, "Behavior");
        row(labelX, behaviorTop + 24, "Edit key picks", "Jump the edit key (B) opens");
        row(labelX, behaviorTop + 24 + 34, "Sections in table", "Compact: color stripe, name on hover");

        for (UiWidget widget : widgets) {
            widget.draw(mouseX, mouseY);
        }

        drawPreview();
    }

    private void row(int labelX, int rowTop, String label, String description) {
        UiDraw.text(label, labelX, rowTop + 4, UiTheme.TEXT);
        UiDraw.text(description, labelX, rowTop + 16, UiTheme.TEXT_MUTED);
    }

    /** Mock of how a strategy looks in the world with the chosen colors. */
    private void drawPreview() {
        int px = x + leftW + COLUMN_GAP;
        int pw = w - leftW - COLUMN_GAP;
        int ph = DISPLAY_CARD_H + CARD_GAP + BEHAVIOR_CARD_H;

        UiDraw.card(px, y, pw, ph, "Preview");

        int areaX = px + UiTheme.PADDING;
        int areaY = y + 24;
        int areaW = pw - UiTheme.PADDING * 2;
        int areaH = ph - 24 - UiTheme.PADDING;

        GuiDraw.fillRect(areaX, areaY, areaX + areaW, areaY + areaH, 0xFF262C38);
        GuiDraw.fillRect(areaX, areaY + areaH * 2 / 3, areaX + areaW, areaY + areaH, 0xFF2F3A2C);

        List<String> lines = new ArrayList<String>();
        String nameColor = UserPreferences.getGlobalInWorldJumpNameColor();
        String bodyColor = UserPreferences.getGlobalInWorldTextColor();

        if (UserPreferences.isGlobalShowJumpNameEnabled()) {
            lines.add(nameColor + "Example jump");
        }
        lines.add(bodyColor + "Position: 12.5 / 64 / -3.5");
        lines.add(bodyColor + "Setup: sprint from the edge");
        lines.add(bodyColor + "Strategy: jump, 45 degree turn");
        lines.add(bodyColor + "Tips: release W in the air");

        int lineH = UiDraw.font().FONT_HEIGHT + 3;
        int cy = areaY + (areaH - lines.size() * lineH) / 2;
        for (String line : lines) {
            UiDraw.centered(UiDraw.fit(line, areaW - 8), areaX + areaW / 2, cy, 0xFFFFFFFF);
            cy += lineH;
        }

        UiDraw.centered("" + SECTION_SIGN + "7" + "Colors apply to all strategies",
                areaX + areaW / 2, areaY + areaH - 12, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        for (UiWidget widget : widgets) {
            if (widget.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void handleMouseInput() { }
}
