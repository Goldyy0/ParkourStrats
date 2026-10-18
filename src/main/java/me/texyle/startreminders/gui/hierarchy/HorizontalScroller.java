package me.texyle.startreminders.gui.hierarchy;

import org.lwjgl.input.Mouse;

import me.texyle.startreminders.gui.GuiDraw;

/** Horizontal scroll offset plus the draggable scrollbar that controls it. */
final class HorizontalScroller {

    private static final int WHEEL_STEP = 18;

    private double offset = 0.0;

    private boolean dragging = false;
    private int grabOffsetX = 0;

    // Geometry of the last drawn bar; used for hit testing and dragging.
    private int barLeft = 0;
    private int barRight = 0;
    private int yTop = 0;
    private int yBottom = 0;
    private int thumbX = 0;
    private int thumbW = 0;
    private int maxScroll = 0;

    double getOffset() {
        return offset;
    }

    void setOffset(double px, int visible, int total) {
        offset = px;
        if (offset < 0.0) offset = 0.0;

        int max = Math.max(0, total - visible);
        if (offset > (double) max) offset = (double) max;
    }

    /** Scrolls by one wheel notch (positive wheel = left), clamped to the content. */
    void scrollByWheel(int dWheel, int visible, int total) {
        int max = Math.max(0, total - visible);

        if (max <= 0) {
            offset = 0.0;
            return;
        }

        if (dWheel > 0) {
            offset -= WHEEL_STEP;
        } else {
            offset += WHEEL_STEP;
        }

        if (offset < 0.0) offset = 0.0;
        if (offset > max) offset = max;
    }

    void draw(int barLeft, int barRight, int yTop, int yBottom, int visible, int total) {
        if (total <= visible) {
            offset = 0.0;

            this.barLeft = this.barRight = this.yTop = this.yBottom = thumbX = thumbW = 0;
            maxScroll = 0;
            dragging = false;
            return;
        }

        int barW = barRight - barLeft;

        int thumbW = Math.max(24, (int) ((barW * (double) visible) / (double) total));
        int max = total - visible;

        if (offset < 0.0) offset = 0.0;
        if (offset > max) offset = max;

        int trackW = Math.max(0, barW - thumbW);

        double t = (max <= 0) ? 0.0 : (offset / (double) max);
        if (t < 0.0) t = 0.0;
        if (t > 1.0) t = 1.0;

        int thumbX = barLeft + (int) Math.floor(t * trackW);

        this.barLeft = barLeft;
        this.barRight = barRight;
        this.yTop = yTop;
        this.yBottom = yBottom;
        this.thumbX = thumbX;
        this.thumbW = thumbW;
        this.maxScroll = max;

        GuiDraw.fillRect(barLeft, yTop, barRight, yBottom, ScrollbarSkin.BACKGROUND);
        GuiDraw.fillRect(thumbX, yTop, thumbX + thumbW, yBottom, ScrollbarSkin.THUMB);
    }

    boolean isMouseOver(int mouseX, int mouseY) {
        if (maxScroll <= 0) return false;
        return mouseX >= barLeft && mouseX <= barRight && mouseY >= yTop && mouseY <= yBottom;
    }

    /** Handles press / drag / release of the left mouse button on the bar. Call every frame. */
    void updateDrag(int mouseX, int mouseY) {
        if (maxScroll <= 0) {
            dragging = false;
            return;
        }

        boolean leftDown = Mouse.isButtonDown(0);

        if (!leftDown) {
            dragging = false;
            return;
        }

        if (!isMouseOver(mouseX, mouseY) && !dragging) {
            return;
        }

        int thumbLeftNow = thumbX;
        int thumbRightNow = thumbX + thumbW;

        if (dragging) {
            int trackW = (barRight - barLeft) - thumbW;
            if (trackW <= 0) {
                offset = 0.0;
                return;
            }

            offset = offsetForThumbLeft((double) mouseX - (double) grabOffsetX, trackW);

            if (offset < 0.0) offset = 0.0;
            if (offset > maxScroll) offset = maxScroll;
            return;
        }

        if (mouseX >= thumbLeftNow && mouseX <= thumbRightNow) {
            dragging = true;
            grabOffsetX = mouseX - thumbLeftNow;
            return;
        }

        // Click on the track: jump the thumb to the cursor and start dragging.
        int trackW = (barRight - barLeft) - thumbW;
        if (trackW <= 0) {
            offset = 0.0;
            return;
        }

        double desiredThumbLeft = (double) mouseX - (thumbW / 2.0);
        offset = offsetForThumbLeft(desiredThumbLeft, trackW);

        dragging = true;
        grabOffsetX = (int) Math.round(mouseX - clampThumbLeft(desiredThumbLeft, trackW));
    }

    private double clampThumbLeft(double desiredThumbLeft, int trackW) {
        if (desiredThumbLeft < barLeft) desiredThumbLeft = barLeft;
        if (desiredThumbLeft > barLeft + trackW) desiredThumbLeft = barLeft + trackW;
        return desiredThumbLeft;
    }

    private double offsetForThumbLeft(double desiredThumbLeft, int trackW) {
        double clamped = clampThumbLeft(desiredThumbLeft, trackW);

        double t = (clamped - barLeft) / (double) trackW;
        if (t < 0.0) t = 0.0;
        if (t > 1.0) t = 1.0;

        return t * (double) maxScroll;
    }
}
