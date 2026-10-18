package me.texyle.startreminders.gui.hierarchy;

import org.lwjgl.input.Mouse;

import me.texyle.startreminders.gui.GuiDraw;

/** Draggable scrollbar skin for the table's vertical scroll position (owned by the GuiSlot). */
final class VerticalScrollbar {

    static final int WIDTH = 6;

    // Geometry of the last drawn bar; used for hit testing and dragging.
    private int barLeft = 0;
    private int barRight = 0;
    private int barTop = 0;
    private int barBottom = 0;
    private int thumbY = 0;
    private int thumbH = 0;
    private int maxScroll = 0;

    private boolean dragging = false;
    private int grabOffsetY = 0;

    void draw(int top, int bottom, int right, float scroll, int totalH) {
        int visibleH = bottom - top;
        int max = Math.max(0, totalH - visibleH);

        if (max <= 0) {
            barLeft = barRight = barTop = barBottom = thumbY = thumbH = 0;
            maxScroll = 0;
            dragging = false;
            return;
        }

        int barRight = right - 2;
        int barLeft = barRight - WIDTH;

        int barH = bottom - top;

        int thumbH = Math.max(24, (int) ((barH * (double) visibleH) / (double) totalH));
        int trackH = barH - thumbH;
        if (trackH < 0) trackH = 0;

        double t = scroll / (double) max;
        if (t < 0.0) t = 0.0;
        if (t > 1.0) t = 1.0;

        int thumbY = top + (int) Math.round(t * trackH);

        this.barLeft = barLeft;
        this.barRight = barRight;
        this.barTop = top;
        this.barBottom = bottom;
        this.thumbY = thumbY;
        this.thumbH = thumbH;
        this.maxScroll = max;

        GuiDraw.fillRect(barLeft, top, barRight, bottom, ScrollbarSkin.BACKGROUND);
        GuiDraw.fillRect(barLeft, thumbY, barRight, thumbY + thumbH, ScrollbarSkin.THUMB);
    }

    boolean isMouseOver(int mouseX, int mouseY) {
        if (maxScroll <= 0) return false;
        return mouseX >= barLeft && mouseX <= barRight && mouseY >= barTop && mouseY <= barBottom;
    }

    /**
     * Handles press / drag / release of the left mouse button on the bar. Call every frame.
     *
     * @return the new scroll position (the given one if the bar did not move it)
     */
    float updateDrag(int mouseX, int mouseY, float scroll) {
        if (maxScroll <= 0) {
            dragging = false;
            return scroll;
        }

        if (!Mouse.isButtonDown(0)) {
            dragging = false;
            return scroll;
        }

        if (!isMouseOver(mouseX, mouseY) && !dragging) {
            return scroll;
        }

        int trackH = (barBottom - barTop) - thumbH;
        if (trackH <= 0) {
            return 0;
        }

        if (dragging) {
            return scrollForThumbTop((double) mouseY - (double) grabOffsetY, trackH);
        }

        if (mouseY >= thumbY && mouseY <= thumbY + thumbH) {
            dragging = true;
            grabOffsetY = mouseY - thumbY;
            return scroll;
        }

        // Click on the track: jump the thumb to the cursor and start dragging.
        double desiredThumbTop = (double) mouseY - (thumbH / 2.0);

        dragging = true;
        grabOffsetY = (int) Math.round(mouseY - clampThumbTop(desiredThumbTop, trackH));
        return scrollForThumbTop(desiredThumbTop, trackH);
    }

    private double clampThumbTop(double desiredThumbTop, int trackH) {
        if (desiredThumbTop < barTop) desiredThumbTop = barTop;
        if (desiredThumbTop > barTop + trackH) desiredThumbTop = barTop + trackH;
        return desiredThumbTop;
    }

    private float scrollForThumbTop(double desiredThumbTop, int trackH) {
        double t = (clampThumbTop(desiredThumbTop, trackH) - barTop) / (double) trackH;
        if (t < 0.0) t = 0.0;
        if (t > 1.0) t = 1.0;

        return (float) (t * (double) maxScroll);
    }
}
