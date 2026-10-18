package me.texyle.startreminders.ui;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;

/**
 * The single floating list of a window, used for dropdowns and "more actions" menus.
 * While it is open it takes all clicks; a click outside closes it.
 */
public final class UiPopup {

    /** One row of the popup. */
    public static final class Entry {
        final String label;
        final boolean enabled;
        final boolean checked;
        final Runnable action;
        final boolean separatorBefore;

        public Entry(String label, boolean enabled, boolean checked, boolean separatorBefore, Runnable action) {
            this.label = label;
            this.enabled = enabled;
            this.checked = checked;
            this.separatorBefore = separatorBefore;
            this.action = action;
        }

        public static Entry item(String label, Runnable action) {
            return new Entry(label, true, false, false, action);
        }

        public static Entry item(String label, boolean enabled, Runnable action) {
            return new Entry(label, enabled, false, false, action);
        }

        public static Entry choice(String label, boolean checked, Runnable action) {
            return new Entry(label, true, checked, false, action);
        }

        /** Same as {@link #item(String, boolean, Runnable)} with a divider line above it. */
        public static Entry afterSeparator(String label, boolean enabled, Runnable action) {
            return new Entry(label, enabled, false, true, action);
        }
    }

    private static final int ROW_H = 18;
    private static final int SEPARATOR_H = 5;
    private static final int MAX_VISIBLE_ROWS = 10;

    private List<Entry> entries;
    private int x;
    private int y;
    private int w;
    private int scroll;
    private boolean open;

    public boolean isOpen() {
        return open;
    }

    public void close() {
        open = false;
        entries = null;
    }

    /** Opens the popup at the given position, kept inside the screen. */
    public void open(int x, int y, int minWidth, List<Entry> entries) {
        if (entries == null || entries.isEmpty()) {
            close();
            return;
        }

        int width = minWidth;
        for (Entry e : entries) {
            width = Math.max(width, UiDraw.width(e.label) + 28);
        }

        ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
        this.entries = entries;
        this.w = width;
        this.x = Math.max(4, Math.min(x, sr.getScaledWidth() - width - 4));
        this.y = Math.max(4, Math.min(y, sr.getScaledHeight() - contentHeight() - 4));
        this.scroll = 0;
        this.open = true;
    }

    private int visibleRows() {
        return Math.min(entries.size(), MAX_VISIBLE_ROWS);
    }

    private int contentHeight() {
        int h = 0;
        int rows = visibleRows();
        for (int i = scroll; i < scroll + rows && i < entries.size(); i++) {
            h += ROW_H + (entries.get(i).separatorBefore && i > scroll ? SEPARATOR_H : 0);
        }
        return h + 4;
    }

    public void draw(int mouseX, int mouseY) {
        if (!open) return;

        int totalH = contentHeight();
        UiDraw.box(x, y, w, totalH, UiTheme.PANEL_BG | 0xFF000000, UiTheme.CONTROL_BORDER);

        int cy = y + 2;
        for (int i = scroll; i < scroll + visibleRows() && i < entries.size(); i++) {
            Entry e = entries.get(i);

            if (e.separatorBefore && i > scroll) {
                UiDraw.fill(x + 6, cy + SEPARATOR_H / 2, w - 12, 1, UiTheme.CARD_BORDER);
                cy += SEPARATOR_H;
            }

            boolean hover = e.enabled && UiDraw.inside(mouseX, mouseY, x + 2, cy, w - 4, ROW_H);
            if (hover) {
                UiDraw.fill(x + 2, cy, w - 4, ROW_H, UiTheme.ACCENT);
            }

            int color = !e.enabled ? UiTheme.TEXT_DISABLED : UiTheme.TEXT;
            if (e.checked) {
                UiDraw.text(">", x + 7, cy + 5, hover ? UiTheme.TEXT : UiTheme.ACCENT_HOVER);
            }
            UiDraw.text(UiDraw.fit(e.label, w - 28), x + 18, cy + 5, color);
            cy += ROW_H;
        }

        if (entries.size() > MAX_VISIBLE_ROWS) {
            UiDraw.text("...", x + w - 16, y + totalH - 12, UiTheme.TEXT_MUTED);
        }
    }

    /** @return always true while open: the click is consumed, and closes the popup unless it hit a row. */
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (!open) return false;

        if (button == 0) {
            int cy = y + 2;
            for (int i = scroll; i < scroll + visibleRows() && i < entries.size(); i++) {
                Entry e = entries.get(i);
                if (e.separatorBefore && i > scroll) cy += SEPARATOR_H;

                if (UiDraw.inside(mouseX, mouseY, x + 2, cy, w - 4, ROW_H)) {
                    if (e.enabled) {
                        Runnable action = e.action;
                        close();
                        if (action != null) action.run();
                    }
                    return true;
                }
                cy += ROW_H;
            }
        }

        close();
        return true;
    }

    public void wheel(int delta) {
        if (!open || entries.size() <= MAX_VISIBLE_ROWS || delta == 0) return;

        int maxScroll = entries.size() - MAX_VISIBLE_ROWS;
        scroll += (delta > 0) ? -1 : 1;
        scroll = Math.max(0, Math.min(scroll, maxScroll));
    }
}
