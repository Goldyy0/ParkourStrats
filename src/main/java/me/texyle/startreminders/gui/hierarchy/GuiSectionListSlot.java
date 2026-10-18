package me.texyle.startreminders.gui.hierarchy;

import java.util.ArrayList;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;

import me.texyle.startreminders.data.MapSection;
import me.texyle.startreminders.data.ParkourMap;

public class GuiSectionListSlot extends GuiStyledSlot {

    private final GuiConfigureSections parent;
    private final ParkourMap map;

    private int activeLevel = 1; // 1..4 (set by parent)

    private ArrayList<MapSection> items = new ArrayList<MapSection>();
    private int selectedIndex = -1;

    public GuiSectionListSlot(Minecraft mc, int width, int height, int top, int bottom, int slotHeight, GuiConfigureSections parent, ParkourMap map) {
        super(mc, width, height, top, bottom, slotHeight);
        this.parent = parent;
        this.map = map;

    }

    public void setActiveLevel(int levelOneBased) {
        if (levelOneBased < 1) levelOneBased = 1;
        if (levelOneBased > 4) levelOneBased = 4;
        this.activeLevel = levelOneBased;
    }

    public void refresh(ArrayList<MapSection> list) {
        this.items = (list != null) ? list : new ArrayList<MapSection>();
        if (selectedIndex >= items.size()) selectedIndex = -1;
    }

    public MapSection getSelected() {
        if (items == null) return null;
        if (selectedIndex < 0 || selectedIndex >= items.size()) return null;
        return items.get(selectedIndex);
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    public void setSelectedIndex(int idx) {
        if (items == null) {
            selectedIndex = -1;
            return;
        }
        if (idx < 0 || idx >= items.size()) {
            selectedIndex = -1;
            return;
        }
        selectedIndex = idx;
    }

    @Override
    protected int getSize() {
        return items != null ? items.size() : 0;
    }

    @Override
    protected void elementClicked(int index, boolean doubleClick, int mouseX, int mouseY) {
        selectedIndex = index;
        if (parent != null) parent.onListSelectionChanged();
    }

    @Override
    protected boolean isSelected(int index) {
        return index == selectedIndex;
    }

    private String buildParentPathPrefix(MapSection section) {
        if (map == null || section == null) return "";
        if (activeLevel <= 1) return "";

        int start = section.getStartJumpIndex();
        int end = section.getEndJumpIndex();
        if (start < 0 || end < 0) return "";

        StringBuilder sb = new StringBuilder();

        int maxLevels = 4;
        try {
            int m = map.getMaxSectionLevelsAllowed();
            if (m > 0) maxLevels = Math.min(4, m);
        } catch (Throwable ignored) { }

        int upper = Math.min(activeLevel - 1, maxLevels);

        for (int lvl = 1; lvl <= upper; lvl++) {
            ArrayList<MapSection> parents = map.getSectionsForLevel(lvl);
            if (parents == null || parents.isEmpty()) continue;

            MapSection container = null;
            for (int i = 0; i < parents.size(); i++) {
                MapSection p = parents.get(i);
                if (p == null) continue;

                int a = p.getStartJumpIndex();
                int b = p.getEndJumpIndex();

                if (a < 0 || b < 0) continue;

                if (b < a) {
                    int tmp = a;
                    a = b;
                    b = tmp;
                }

                if (start >= a && end <= b) {
                    container = p;
                    break;
                }
            }

            if (container != null) {
                String n = container.getName();
                if (n == null) n = "";
                n = n.trim();
                if (!n.isEmpty()) {
                    if (sb.length() > 0) sb.append("/");
                    sb.append(n);
                }
            }
        }

        if (sb.length() == 0) return "";
        return sb.toString() + ": ";
    }

    @Override
    protected void drawSlot(int entryID, int insideLeft, int yPos, int insideSlotHeight, int mouseXIn, int mouseYIn) {
        if (items == null || entryID < 0 || entryID >= items.size()) return;

        MapSection s = items.get(entryID);
        if (s == null) return;

        int left = insideLeft + 2;
        int right = insideLeft + this.getListWidth() - 4;

        int top = yPos;
        int bottom = yPos + insideSlotHeight;

        int zebra = (entryID % 2 == 0) ? 0x22101010 : 0x33333333;
        Gui.drawRect(left, top, right, bottom, zebra);

        if (entryID == selectedIndex) {
            Gui.drawRect(left, top, right, bottom, 0x55333333);
        }

        // Prefix for nested levels: "Area 1/S1: "
        String prefix = buildParentPathPrefix(s);

        // Section name
        String name = s.getName();
        if (name == null) name = "";

        // Range label
        String range;
        if (s.getStartJumpIndex() < 0 || s.getEndJumpIndex() < 0) {
            range = " (Not set)";
        } else {
            range = " (" + (s.getStartJumpIndex() + 1) + " - " + (s.getEndJumpIndex() + 1) + ")";
        }

        // Layout: prefix -> color square -> "name (range)"
        int x = left + 6;

        if (prefix != null && !prefix.isEmpty()) {
            font.drawString(prefix, x, top + 7, 0xFFFFFF, true);
            x += font.getStringWidth(prefix);
        }

        int square = 10;
        int sy = top + (insideSlotHeight - square) / 2;
        Gui.drawRect(x, sy, x + square, sy + square, s.getColorArgb());

        String text = name + range;
        font.drawString(text, x + square + 8, top + 7, 0xFFFFFF, true);

        Gui.drawRect(left, bottom - 1, right, bottom, 0x442A2A2A);
    }

}