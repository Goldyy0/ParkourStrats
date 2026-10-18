package me.texyle.startreminders.gui.hierarchy;

import java.util.ArrayList;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;

import me.texyle.startreminders.data.MapSection;

public class GuiPickSectionListSlot extends GuiStyledSlot {

    private final GuiPickSectionPreview parent;

    private ArrayList<MapSection> items = new ArrayList<MapSection>();
    private int selectedIndex = -1;

    public GuiPickSectionListSlot(Minecraft mc, int width, int height, int top, int bottom, int slotHeight, GuiPickSectionPreview parent) {
        super(mc, width, height, top, bottom, slotHeight);
        this.parent = parent;

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

        int square = 10;
        int sx = left + 6;
        int sy = top + (insideSlotHeight - square) / 2;
        Gui.drawRect(sx, sy, sx + square, sy + square, s.getColorArgb());

        String name = s.getName();
        if (name == null) name = "";

        String range;
        if (s.getStartJumpIndex() < 0 || s.getEndJumpIndex() < 0) {
            range = " (Not set)";
        } else {
            range = " (" + (s.getStartJumpIndex() + 1) + " - " + (s.getEndJumpIndex() + 1) + ")";
        }
        String text = name + range;

        font.drawString(text, sx + square + 8, top + 7, 0xFFFFFF, true);

        Gui.drawRect(left, bottom - 1, right, bottom, 0x442A2A2A);
    }

}