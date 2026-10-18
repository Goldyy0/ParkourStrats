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
        final boolean danger;

        public Entry(String label, boolean enabled, boolean checked, boolean separatorBefore, Runnable action) {
            this(label, enabled, checked, separatorBefore, false, action);
        }

        private Entry(String label, boolean enabled, boolean checked, boolean separatorBefore, boolean danger, Runnable action) {
            this.danger = danger;
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

        /** A destructive action (remove, delete): red row, with a divider line above it. */
        public static Entry danger(String label, boolean enabled, Runnable action) {
            return new Entry(label, enabled, false, true, true, action);
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
    private final VerticalScrollbar scrollbar = new VerticalScrollbar();

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
        this.open = true;

        // Long lists start at the entry that is currently chosen, so it does not have to be scrolled to.
        this.scroll = 0;
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).checked) {
                this.scroll = Math.min(i, Math.max(0, entries.size() - MAX_VISIBLE_ROWS));
                break;
            }
        }
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

            boolean hover = e.enabled && !overScrollbar(mouseX, mouseY) && UiDraw.inside(mouseX, mouseY, x + 2, cy, rowW(), ROW_H);
            if (e.danger && e.enabled) {
                UiDraw.fill(x + 2, cy, rowW(), ROW_H, hover ? UiTheme.DANGER_HOVER : UiTheme.DANGER);
            } else if (hover) {
                UiDraw.fill(x + 2, cy, rowW(), ROW_H, UiTheme.ACCENT);
            }

            int color = !e.enabled ? UiTheme.TEXT_DISABLED : UiTheme.TEXT;
            if (e.checked) {
                UiDraw.text(">", x + 7, cy + 5, hover ? UiTheme.TEXT : UiTheme.ACCENT_HOVER);
            }
            UiDraw.text(UiDraw.fit(e.label, rowW() - 24), x + 18, cy + 5, color);
            cy += ROW_H;
        }

        if (entries.size() > MAX_VISIBLE_ROWS) {
            // Works in row units (each row ROW_H tall), inside the right edge of the box.
            scrollbar.draw(y + 2, y + totalH - 2, x + w, scroll * ROW_H, entries.size() * ROW_H);
            float dragged = scrollbar.updateDrag(mouseX, mouseY, scroll * ROW_H);
            scroll = Math.max(0, Math.min(Math.round(dragged / ROW_H), entries.size() - MAX_VISIBLE_ROWS));
        }
    }

    /** Width of a row; with a scrollbar the rows end before it. */
    private int rowW() {
        return w - 4 - (entries != null && entries.size() > MAX_VISIBLE_ROWS ? VerticalScrollbar.WIDTH + 4 : 0);
    }

    private boolean overScrollbar(int mouseX, int mouseY) {
        return entries != null && entries.size() > MAX_VISIBLE_ROWS && scrollbar.isMouseOver(mouseX, mouseY);
    }

    /** @return always true while open: the click is consumed, and closes the popup unless it hit a row. */
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (!open) return false;

        if (button == 0 && overScrollbar(mouseX, mouseY)) {
            return true; // the scrollbar handles the drag; the popup stays open
        }

        if (button == 0) {
            int cy = y + 2;
            for (int i = scroll; i < scroll + visibleRows() && i < entries.size(); i++) {
                Entry e = entries.get(i);
                if (e.separatorBefore && i > scroll) cy += SEPARATOR_H;

                if (UiDraw.inside(mouseX, mouseY, x + 2, cy, rowW(), ROW_H)) {
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
