package me.texyle.startreminders.ui;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.input.Mouse;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.util.ResourceLocation;

import me.texyle.startreminders.gui.GuiDraw;
import me.texyle.startreminders.gui.GuiScissor;
import me.texyle.startreminders.reminders.UserPreferences;

/** All user options in one place: how strategies look in the world and how the edit key behaves. */
final class HubSettingsTab implements HubTab {

    private static final char SECTION_SIGN = (char) 0xA7;

    private static final int DISPLAY_CARD_H = 168;
    private static final int BEHAVIOR_CARD_H = 132;
    private static final int CARD_GAP = 8;
    private static final int COLUMN_GAP = 12;
    private static final int PREVIEW_H = 130;
    private static final int WRAP_WIDTH = 500;
    private static final int SCROLL_STEP = 20;
    private static final int CELL = 20;
    private static final int CHART = 6 * CELL;
    private static final int LABEL_CARD_H = 24 + CHART + 10;

    private int x;
    private int y;
    private int w;
    private int h;

    private int leftW;
    private int contentW;
    private int contentH;
    private int top;
    private int scroll;
    private int maxScroll;
    private int previewX;
    private int previewY;
    private int previewW;
    private int previewH;

    private final VerticalScrollbar scrollbar = new VerticalScrollbar();

    private final UiToggle showJumpName;
    private final UiColorSwatches jumpNameColor;
    private final UiColorSwatches textColor;
    private final UiSegmented pickMode;
    private final UiSegmented sectionsMode;
    private final UiSegmented showKeyMode;
    private int labelTop;
    private int chartX;
    private int chartY;
    private boolean chartDragging;

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

        showKeyMode = new UiSegmented(new String[] { "Toggle", "Hold" },
                UserPreferences.isShowKeyToggle() ? 0 : 1,
                index -> UserPreferences.setShowKeyToggle(index == 0));

        widgets.add(pickMode);
        widgets.add(sectionsMode);
        widgets.add(showKeyMode);
    }

    @Override
    public void layout(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        reposition();
    }

    /** Places everything for the current size and scroll position; wraps the preview under the cards when narrow. */
    private void reposition() {
        boolean narrow = w < WRAP_WIDTH;
        int fullH = narrow
                ? DISPLAY_CARD_H + CARD_GAP + BEHAVIOR_CARD_H + CARD_GAP + PREVIEW_H
                : DISPLAY_CARD_H + CARD_GAP + BEHAVIOR_CARD_H;

        int baseH = fullH;
        fullH += CARD_GAP + LABEL_CARD_H;

        boolean scrolls = fullH > h;
        contentW = scrolls ? w - VerticalScrollbar.WIDTH - 6 : w;
        contentH = fullH;
        maxScroll = Math.max(0, fullH - h);
        scroll = Math.max(0, Math.min(maxScroll, scroll));

        top = y - scroll;

        if (narrow) {
            leftW = contentW;
            previewX = x;
            previewY = top + DISPLAY_CARD_H + CARD_GAP + BEHAVIOR_CARD_H + CARD_GAP;
            previewW = contentW;
            previewH = PREVIEW_H;
        } else {
            leftW = Math.min(Math.max(300, (int) (contentW * 0.55)), contentW - 180 - COLUMN_GAP);
            previewX = x + leftW + COLUMN_GAP;
            previewY = top;
            previewW = contentW - leftW - COLUMN_GAP;
            previewH = DISPLAY_CARD_H + CARD_GAP + BEHAVIOR_CARD_H;
        }

        labelTop = top + baseH + CARD_GAP;
        chartX = x + UiTheme.PADDING;
        chartY = labelTop + 24;

        int right = x + leftW - UiTheme.PADDING;

        int rowTop = top + 24;
        showJumpName.x = right - showJumpName.w;
        showJumpName.y = rowTop + 9;

        rowTop += 34;
        jumpNameColor.x = right - jumpNameColor.w;
        jumpNameColor.y = rowTop + 6;

        rowTop += 52;
        textColor.x = right - textColor.w;
        textColor.y = rowTop + 6;

        int behaviorTop = top + DISPLAY_CARD_H + CARD_GAP;
        pickMode.x = right - pickMode.w;
        pickMode.y = behaviorTop + 24 + 8;
        sectionsMode.x = right - sectionsMode.w;
        sectionsMode.y = behaviorTop + 24 + 34 + 8;
        showKeyMode.x = right - showKeyMode.w;
        showKeyMode.y = behaviorTop + 24 + 68 + 8;
    }

    @Override
    public void draw(int mouseX, int mouseY, float partialTicks) {
        scroll = Math.round(scrollbar.updateDrag(mouseX, mouseY, scroll));
        reposition();

        if (chartDragging) {
            if (Mouse.isButtonDown(0)) {
                setFromChart(mouseX, mouseY);
            } else {
                chartDragging = false;
            }
        }

        Minecraft mc = Minecraft.getMinecraft();
        GuiScissor.enable(mc, x, y, w, h);

        int labelX = x + UiTheme.PADDING;

        UiDraw.card(x, top, leftW, DISPLAY_CARD_H, "In-world display");

        int rowTop = top + 24;
        row(labelX, rowTop, "Show jump name", "Name of the jump above its strategy");
        rowTop += 34;
        row(labelX, rowTop, "Jump name color", "Color of the name line");
        rowTop += 52;
        row(labelX, rowTop, "Strategy text color", "Color of the strategy lines");

        int behaviorTop = top + DISPLAY_CARD_H + CARD_GAP;
        UiDraw.card(x, behaviorTop, leftW, BEHAVIOR_CARD_H, "Behavior");
        row(labelX, behaviorTop + 24, "Edit key target", "Jump the edit key (B) opens");
        row(labelX, behaviorTop + 24 + 34, "Sections in table", "Compact: color stripe, name on hover");
        row(labelX, behaviorTop + 24 + 68, "Show strategies (X)",
                UserPreferences.isShowKeyToggle() ? "Press to switch on and off" : "Shown while the key is held");

        for (UiWidget widget : widgets) {
            widget.draw(mouseX, mouseY);
        }

        drawPreview();

        UiDraw.card(x, labelTop, contentW, LABEL_CARD_H, "Strategy position");
        drawLabelChart();

        GuiScissor.disable();

        scrollbar.draw(y, y + h, x + w, scroll, contentH);
    }

    private static ResourceLocation skin() {
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        return player != null ? player.getLocationSkin() : DefaultPlayerSkin.getDefaultSkinLegacy();
    }

    /** Side view of the player and the block where a new label is placed, plus what the sliders currently mean. */
    private void drawLabelChart() {
        int forward = UserPreferences.getLabelForward();
        int up = UserPreferences.getLabelUp();

        UiDraw.fill(chartX, chartY, CHART, CHART, 0xFF262C38);
        for (int i = 0; i <= 6; i++) {
            UiDraw.fill(chartX + i * CELL, chartY, 1, CHART, UiTheme.CARD_BORDER);
            UiDraw.fill(chartX, chartY + i * CELL, CHART, 1, UiTheme.CARD_BORDER);
        }
        UiDraw.fill(chartX, chartY + CHART, CHART, 2, 0xFF2F3A2C); // the ground under the player's feet

        // The label block, then the player (facing right) on top of it.
        int blockX = chartX + forward * CELL;
        int blockY = chartY + CHART - (up + 1) * CELL;
        UiDraw.box(blockX + 1, blockY + 1, CELL - 1, CELL - 1, UiTheme.ACCENT, UiTheme.ACCENT_HOVER);
        drawProfile(chartX + CELL / 2, chartY + CHART);

        int tx = chartX + CHART + 24;
        UiDraw.text(forward + (forward == 1 ? " block" : " blocks") + " in front of you", tx, chartY + 6, UiTheme.TEXT);
        UiDraw.text(up + (up == 1 ? " block" : " blocks") + " above your feet", tx, chartY + 22, UiTheme.TEXT);
        UiDraw.text("Used for every new strategy label", tx, chartY + 42, UiTheme.TEXT_DISABLED);
    }

    /** The player seen from the side, facing right, made of their skin; 1.8 blocks tall with the feet at (cx, bottom). */
    private static void drawProfile(int cx, int bottom) {
        float scale = CELL * 1.8f / 32f;
        Minecraft.getMinecraft().getTextureManager().bindTexture(skin());
        GlStateManager.pushMatrix();
        GlStateManager.translate(cx, bottom, 0f);
        GlStateManager.scale(scale, scale, 1f);
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        Gui.drawScaledCustomSizeModalRect(-2, -12, 0, 20, 4, 12, 4, 12, 64, 64);   // leg
        Gui.drawScaledCustomSizeModalRect(-2, -24, 16, 20, 4, 12, 4, 12, 64, 64);  // body
        Gui.drawScaledCustomSizeModalRect(-2, -24, 40, 20, 4, 12, 4, 12, 64, 64);  // arm
        Gui.drawScaledCustomSizeModalRect(-4, -32, 0, 8, 8, 8, 8, 8, 64, 64);      // head
        Gui.drawScaledCustomSizeModalRect(-4, -32, 32, 8, 8, 8, 8, 8, 64, 64);     // hat layer
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    /** A click or drag on the chart puts the label block under the mouse. */
    private void setFromChart(int mouseX, int mouseY) {
        int forward = Math.max(0, Math.min(5, (mouseX - chartX) / CELL));
        int up = Math.max(0, Math.min(5, 5 - (mouseY - chartY) / CELL));
        if (mouseX < chartX) forward = 0;
        if (mouseY >= chartY + CHART) up = 0;

        if (forward != UserPreferences.getLabelForward()) UserPreferences.setLabelForward(forward);
        if (up != UserPreferences.getLabelUp()) UserPreferences.setLabelUp(up);
    }

    private void row(int labelX, int rowTop, String label, String description) {
        UiDraw.text(label, labelX, rowTop + 4, UiTheme.TEXT);
        UiDraw.text(description, labelX, rowTop + 16, UiTheme.TEXT_MUTED);
    }

    /** Mock of how a strategy looks in the world with the chosen colors. */
    private void drawPreview() {
        int px = previewX;
        int pw = previewW;
        int ph = previewH;

        UiDraw.card(px, previewY, pw, ph, "Preview");

        int areaX = px + UiTheme.PADDING;
        int areaY = previewY + 24;
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
        if (mouseY < y || mouseY >= y + h) {
            return false;
        }
        if (button == 0 && UiDraw.inside(mouseX, mouseY, chartX, chartY, CHART, CHART)) {
            chartDragging = true;
            setFromChart(mouseX, mouseY);
            return true;
        }
        for (UiWidget widget : widgets) {
            if (widget.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseWheel(int mouseX, int mouseY, int delta) {
        if (maxScroll <= 0 || delta == 0) {
            return false;
        }
        scroll = Math.max(0, Math.min(maxScroll, scroll - Integer.signum(delta) * SCROLL_STEP));
        return true;
    }

    @Override
    public void handleMouseInput() { }
}
