// GuiEditSection.java
package me.texyle.startreminders.gui.hierarchy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;

import me.texyle.startreminders.data.Jump;
import me.texyle.startreminders.data.MapSection;
import me.texyle.startreminders.data.ParkourMap;
import me.texyle.startreminders.reminders.ReminderManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

public class GuiEditSection extends GuiScreen {

    private static final int BTN_SAVE = 1;
    private static final int BTN_CANCEL = 2;

    private static final int BTN_COLOR = 10;
    private static final int BTN_TEXT_COLOR = 13;

    private static final int BTN_PICK_START = 11;
    private static final int BTN_PICK_END = 12;

    private static final int TEXT_COLOR_WHITE = 0xFFFFFFFF;
    private static final int TEXT_COLOR_BLACK = 0xFF000000;

    private final GuiScreen parent;
    private final ParkourMap map;
    private final int levelOneBased; // 1..4
    private final MapSection editing; // null => create

    private GuiTextField nameField;

    private GuiButton btnColor;
    private GuiButton btnTextColor;

    private GuiButton btnPickStart;
    private GuiButton btnPickEnd;

    private GuiButton btnSave;
    private GuiButton btnCancel;

    private int colorArgb = 0xFFFFFFFF;
    private int textColorArgb = TEXT_COLOR_WHITE;

    private Integer startIdx = null; // jump index in map.getJumps()
    private Integer endIdx = null;

    // Persist user input across sub-screens (color picker / pick jump index)
    private String draftName = null;
    private int draftCursorPos = -1;

    // Cached layout (so we can re-use positions consistently)
    private int centerX;
    private int baseY;
    private int fieldW;
    private int fieldX;

    public GuiEditSection(GuiScreen parent, ParkourMap map, int levelOneBased, MapSection editing) {
        this.parent = parent;
        this.map = map;
        this.levelOneBased = levelOneBased;
        this.editing = editing;
    }

    @Override
    public void initGui() {
        super.initGui();

        centerX = this.width / 2;
        baseY = this.height / 2 - 90;

        fieldW = 260;
        fieldX = centerX - (fieldW / 2);

        // Only pull from editing when we don't already have a draft
        if (editing != null) {
            if (draftName == null) {
                String n = editing.getName();
                draftName = (n != null) ? n : "";
            }
            if (colorArgb == 0xFFFFFFFF) {
                colorArgb = editing.getColorArgb();
            }
            // Load text color only if we still have default
            if (textColorArgb == TEXT_COLOR_WHITE) {
                textColorArgb = editing.getTextColorArgb();
            }

            if (startIdx == null) {
                int v = editing.getStartJumpIndex();
                startIdx = (v >= 0) ? Integer.valueOf(v) : null;
            }
            if (endIdx == null) {
                int v = editing.getEndJumpIndex();
                endIdx = (v >= 0) ? Integer.valueOf(v) : null;
            }
        } else {
            if (draftName == null) draftName = "";
        }

        nameField = new GuiTextField(100, this.fontRendererObj, fieldX, baseY + 20, fieldW, 20);
        nameField.setFocused(true);
        nameField.setMaxStringLength(512);
        nameField.setText(draftName != null ? draftName : "");

        if (draftCursorPos >= 0) {
            nameField.setCursorPosition(Math.min(draftCursorPos, nameField.getText().length()));
        }

        // --- Color row: "Color:" + [btn] + "Text Color:" + [btn] on the SAME row ---
        int rowLeft = fieldX;
        int rowYTop = baseY + 50; // row top
        int btnY = rowYTop + 4;

        String labelColor = "Color:";
        String labelTextColor = "Text Color:";

        int labelColorW = this.fontRendererObj.getStringWidth(labelColor);
        int labelTextColorW = this.fontRendererObj.getStringWidth(labelTextColor);

        int btnW = 24;
        int btnH = 20;
        int gapAfterLabel = 6;
        int gapBetweenBlocks = 12;

        int colorBtnX = rowLeft + labelColorW + gapAfterLabel;
        btnColor = new GuiButton(BTN_COLOR, colorBtnX, btnY, btnW, btnH, "");

        int textLabelX = colorBtnX + btnW + gapBetweenBlocks;
        int textBtnX = textLabelX + labelTextColorW + gapAfterLabel;

        // Clamp so the Text Color button always fits inside the field width
        int fieldRight = fieldX + fieldW;
        int desiredRightEdge = textBtnX + btnW;
        if (desiredRightEdge > fieldRight) {
            int shiftLeft = desiredRightEdge - fieldRight;
            textLabelX -= shiftLeft;
            textBtnX -= shiftLeft;
        }

        btnTextColor = new GuiButton(BTN_TEXT_COLOR, textBtnX, btnY, btnW, btnH, "");

        // Range buttons (full width)
        btnPickStart = new GuiButton(BTN_PICK_START, fieldX, baseY + 106, fieldW, 20, getPickLabel(true));
        btnPickEnd = new GuiButton(BTN_PICK_END, fieldX, baseY + 136, fieldW, 20, getPickLabel(false));

        int buttonsY = this.height - 48;
        btnSave = new GuiButton(BTN_SAVE, centerX - 110, buttonsY, 100, 20, "Save");
        btnCancel = new GuiButton(BTN_CANCEL, centerX + 10, buttonsY, 100, 20, "Cancel");

        this.buttonList.clear();
        this.buttonList.add(btnColor);
        this.buttonList.add(btnTextColor);
        this.buttonList.add(btnPickStart);
        this.buttonList.add(btnPickEnd);
        this.buttonList.add(btnSave);
        this.buttonList.add(btnCancel);
    }

    private void stashDraft() {
        if (nameField != null) {
            draftName = nameField.getText() != null ? nameField.getText() : "";
            draftCursorPos = nameField.getCursorPosition();
        }
    }

    private String getPickLabel(boolean isStart) {
        Integer idx = isStart ? startIdx : endIdx;
        if (idx == null) return "Not set";

        String jumpLabel = getJumpLabel(idx.intValue());
        return (idx.intValue() + 1) + ") " + jumpLabel;
    }

    private String getJumpLabel(int jumpIndex) {
        if (map == null) return "<null>";
        ArrayList<Jump> js = map.getJumps();
        if (js == null || js.isEmpty()) return "<no jumps>";
        if (jumpIndex < 0 || jumpIndex >= js.size()) return "<out of range>";
        Jump j = js.get(jumpIndex);
        if (j == null) return "<null jump>";
        String id = j.getId();
        return (id != null && id.trim().length() > 0) ? id : "<unnamed>";
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();

        String title = (editing != null) ? "Edit section" : "Create section";
        this.drawCenteredString(this.fontRendererObj, title, centerX, baseY - 14, 0xFFFFFF);

        // Name
        this.fontRendererObj.drawString("Section name:", fieldX, baseY + 8, 0xFFFFFF, true);
        nameField.drawTextBox();

        // Color row labels
        int rowYText = baseY + 58;

        String labelColor = "Color:";
        String labelTextColor = "Text Color:";

        int rowLeft = fieldX;
        int labelColorW = this.fontRendererObj.getStringWidth(labelColor);

        // Compute label positions matching initGui logic
        int colorBtnX = rowLeft + labelColorW + 6;
        int textLabelX = colorBtnX + 24 + 12;

        // In case initGui had to shift left to fit, align to actual button positions:
        if (btnTextColor != null) {
            textLabelX = (btnTextColor.xPosition - 6) - this.fontRendererObj.getStringWidth(labelTextColor);
        }

        this.fontRendererObj.drawString(labelColor, rowLeft, rowYText, 0xFFFFFF, true);
        this.fontRendererObj.drawString(labelTextColor, textLabelX, rowYText, 0xFFFFFF, true);

        // Section range
        this.fontRendererObj.drawString("Section range", fieldX, baseY + 86, 0xFFFFFF, true);
        this.fontRendererObj.drawString("Start of section:", fieldX, baseY + 112, 0xFFFFFF, true);
        this.fontRendererObj.drawString("End of section:", fieldX, baseY + 142, 0xFFFFFF, true);

        // Update button labels live
        btnPickStart.displayString = getPickLabel(true);
        btnPickEnd.displayString = getPickLabel(false);

        // Let vanilla render all buttons first (hover/pressed effects)
        super.drawScreen(mouseX, mouseY, partialTicks);

        // Draw swatches inside buttons
        drawColorSwatchInButton(btnColor, colorArgb);
        drawColorSwatchInButton(btnTextColor, textColorArgb);

        // Validation hint
        if (startIdx != null && endIdx != null && endIdx < startIdx) {
            this.fontRendererObj.drawString(EnumChatFormatting.RED + "End cannot be before start.", fieldX, baseY + 168, 0xFFFFFF, true);
        }
    }

    private void drawColorSwatchInButton(GuiButton btn, int argb) {
        if (btn == null) return;

        int pad = 4;

        int x1 = btn.xPosition + pad;
        int y1 = btn.yPosition + pad;
        int x2 = btn.xPosition + btn.width - pad;
        int y2 = btn.yPosition + btn.height - pad;

        if (x2 <= x1 || y2 <= y1) return;

        // Border
        drawRect(x1 - 1, y1 - 1, x2 + 1, y2 + 1, 0xFF101010);
        drawRect(x1, y1, x2, y2, argb);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button == null) return;

        if (button.id == BTN_CANCEL) {
            Minecraft.getMinecraft().displayGuiScreen(parent);
            return;
        }

        if (button.id == BTN_COLOR) {
            stashDraft();
            Minecraft.getMinecraft().displayGuiScreen(new GuiColorPicker(this, colorArgb, new GuiColorPicker.IColorHandler() {
                @Override
                public void onPick(int argb) {
                    colorArgb = argb;
                }

                @Override
                public void onCancel() { }
            }));
            return;
        }

        if (button.id == BTN_TEXT_COLOR) {
            // Toggle between white and black
            if ((textColorArgb & 0x00FFFFFF) == 0x000000) {
                textColorArgb = TEXT_COLOR_WHITE;
            } else {
                textColorArgb = TEXT_COLOR_BLACK;
            }
            return;
        }

        if (button.id == BTN_PICK_START) {
            stashDraft();
            Minecraft.getMinecraft().displayGuiScreen(new GuiPickJumpIndex(this, map, "Pick start of section", new GuiPickJumpIndex.IResultHandler() {
                @Override
                public void onPick(int jumpIndex) {
                    startIdx = jumpIndex;
                    if (endIdx != null && endIdx < startIdx) {
                        endIdx = startIdx;
                    }
                }

                @Override
                public void onCancel() { }
            }));
            return;
        }

        if (button.id == BTN_PICK_END) {
            stashDraft();
            Minecraft.getMinecraft().displayGuiScreen(new GuiPickJumpIndex(this, map, "Pick end of section", new GuiPickJumpIndex.IResultHandler() {
                @Override
                public void onPick(int jumpIndex) {
                    endIdx = jumpIndex;
                    if (startIdx != null && endIdx < startIdx) {
                        startIdx = endIdx;
                    }
                }

                @Override
                public void onCancel() { }
            }));
            return;
        }

        if (button.id == BTN_SAVE) {
            stashDraft();

            if (map == null) {
                sendClientChat(EnumChatFormatting.RED + "Map is null.");
                return;
            }

            String name = (draftName != null) ? draftName.trim() : "";
            if (name.length() == 0) {
                sendClientChat(EnumChatFormatting.RED + "Section name cannot be empty.");
                return;
            }

            ArrayList<Jump> jumps = (map != null) ? map.getJumps() : null;
            int jumpCount = (jumps != null) ? jumps.size() : 0;

            // Range is optional: either BOTH set, or BOTH unset.
            boolean hasStart = (startIdx != null);
            boolean hasEnd = (endIdx != null);

            if (hasStart != hasEnd) {
                sendClientChat(EnumChatFormatting.RED + "Please set both Start and End, or leave both Not set.");
                return;
            }

            boolean hasRange = hasStart && hasEnd;

            int s = -1;
            int e = -1;

            if (hasRange) {
                s = startIdx.intValue();
                e = endIdx.intValue();

                if (jumpCount <= 0) {
                    sendClientChat(EnumChatFormatting.RED + "This map has no jumps. Clear the range to save this section.");
                    return;
                }

                if (s < 0 || e < 0 || s >= jumpCount || e >= jumpCount) {
                    sendClientChat(EnumChatFormatting.RED + "Invalid section range.");
                    return;
                }

                if (e < s) {
                    int t = s;
                    s = e;
                    e = t;
                }

                // Disallow overlaps within the same level.
                if (isOverlappingExistingSection(levelOneBased, s, e)) {
                    sendClientChat(EnumChatFormatting.RED + "Section range overlaps another section on this level.");
                    return;
                }
            }

            if (e < s) {
                int t = s;
                s = e;
                e = t;
            }

            MapSection target = (editing != null) ? editing : new MapSection();
            if (editing == null) {
                target.setId(UUID.randomUUID().toString());
            }

            target.setName(name);
            target.setColorArgb(colorArgb);
            target.setTextColorArgb(textColorArgb);

            if (hasRange) {
                target.setStartJumpIndex(s);
                target.setEndJumpIndex(e);
                target.normalizeRange();
            } else {
                // No range => section exists but is not rendered anywhere.
                target.setStartJumpIndex(-1);
                target.setEndJumpIndex(-1);
            }

            if (editing == null) {
                map.getSectionsForLevel(levelOneBased).add(target);
            }

            ReminderManager.saveToFile();
            Minecraft.getMinecraft().displayGuiScreen(parent);
        }
    }

    private boolean isOverlappingExistingSection(int levelOneBased, int s, int e) {
        if (map == null) return false;

        ArrayList<MapSection> list = map.getSectionsForLevel(levelOneBased);
        if (list == null || list.isEmpty()) return false;

        for (int i = 0; i < list.size(); i++) {
            MapSection other = list.get(i);
            if (other == null) continue;

            // Skip the section being edited.
            if (editing != null && other == editing) continue;

            int a = other.getStartJumpIndex();
            int b = other.getEndJumpIndex();

            // Inactive sections do not participate in overlap checks.
            if (a < 0 || b < 0) continue;

            if (b < a) {
                int t = a;
                a = b;
                b = t;
            }

            // Inclusive overlap: [a,b] intersects [s,e]
            int left = Math.max(a, s);
            int right = Math.min(b, e);
            if (left <= right) {
                return true;
            }
        }

        return false;
    }

    private void sendClientChat(String msg) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null || msg == null) return;
        mc.thePlayer.addChatMessage(new ChatComponentText(msg));
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1) {
            Minecraft.getMinecraft().displayGuiScreen(parent);
            return;
        }

        if (nameField != null) {
            nameField.textboxKeyTyped(typedChar, keyCode);
            stashDraft();
        }

        if (keyCode == 28) {
            actionPerformed(btnSave);
            return;
        }

        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (nameField != null) {
            nameField.mouseClicked(mouseX, mouseY, mouseButton);
            stashDraft();
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (nameField != null) {
            nameField.updateCursorCounter();
            stashDraft();
        }
    }
}