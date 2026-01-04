package me.texyle.startreminders.gui;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import me.texyle.startreminders.reminders.ReminderManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;

public class GuiTextColors extends GuiScreen {

    private final GuiScreen parent;

    private GuiButton backButton;
    private GuiButton doneButton;

    private String selectedJumpNameCode;
    private String selectedTextCode;

    // Button IDs
    private static final int BTN_BACK = 1;
    private static final int BTN_DONE = 2;

    private static final int BTN_JUMP_BASE = 100; // 100..115
    private static final int BTN_TEXT_BASE = 200; // 200..215

    // Icon location: assets/sr/textures/gui/colors/<name>.png
    private static final String ICON_BASE_PATH = "textures/gui/colors/";

    // Button layout
    private static final int BTN_SIZE = 22;
    private static final int GAP = 4;

    // Stored code must be "§<hex>".
    private static final String[] CODES = new String[] {
            "\u00A70", "\u00A71", "\u00A72", "\u00A73",
            "\u00A74", "\u00A75", "\u00A76", "\u00A77",
            "\u00A78", "\u00A79", "\u00A7a", "\u00A7b",
            "\u00A7c", "\u00A7d", "\u00A7e", "\u00A7f"
    };

    // Icon file names aligned with CODES order.
    // Must match exactly: black.png, dark_blue.png, ..., white.png
    private static final String[] ICON_NAMES = new String[] {
            "black",
            "dark_blue",
            "dark_green",
            "dark_aqua",
            "dark_red",
            "dark_purple",
            "gold",
            "gray",
            "dark_gray",
            "blue",
            "green",
            "aqua",
            "red",
            "light_purple",
            "yellow",
            "white"
    };

    // Friendly names for hover/labels.
    private static final Map<String, String> NAME_BY_CODE = new HashMap<String, String>();
    static {
        NAME_BY_CODE.put("\u00A70", "Black");
        NAME_BY_CODE.put("\u00A71", "Dark Blue");
        NAME_BY_CODE.put("\u00A72", "Green");
        NAME_BY_CODE.put("\u00A73", "Cyan");
        NAME_BY_CODE.put("\u00A74", "Dark Red");
        NAME_BY_CODE.put("\u00A75", "Purple");
        NAME_BY_CODE.put("\u00A76", "Gold");
        NAME_BY_CODE.put("\u00A77", "Gray");
        NAME_BY_CODE.put("\u00A78", "Dark Gray");
        NAME_BY_CODE.put("\u00A79", "Blue");
        NAME_BY_CODE.put("\u00A7a", "Lime");
        NAME_BY_CODE.put("\u00A7b", "Aqua");
        NAME_BY_CODE.put("\u00A7c", "Red");
        NAME_BY_CODE.put("\u00A7d", "Pink");
        NAME_BY_CODE.put("\u00A7e", "Yellow");
        NAME_BY_CODE.put("\u00A7f", "White");
    }

    // Computed layout y positions (avoid overlaps)
    private int yJumpLabel;
    private int yJumpPalette;
    private int yJumpPreview;

    private int yTextLabel;
    private int yTextPalette;
    private int yTextPreview;

    public GuiTextColors(GuiScreen parent) {
        this.parent = parent;
        this.selectedJumpNameCode = ReminderManager.getGlobalInWorldJumpNameColor();
        this.selectedTextCode = ReminderManager.getGlobalInWorldTextColor();
    }

    @Override
    public void initGui() {
        super.initGui();

        this.buttonList.clear();

        backButton = new GuiButton(BTN_BACK, 8, 8, 60, 20, "Back");
        doneButton = new GuiButton(BTN_DONE, this.width - 8 - 80, 8, 80, 20, "Done");

        this.buttonList.add(backButton);
        this.buttonList.add(doneButton);

        // Layout: two palettes (each 2 rows of 8)
        int paletteW = (8 * BTN_SIZE) + (7 * GAP);
        int startX = (this.width - paletteW) / 2;

        int paletteH = (2 * BTN_SIZE) + GAP;

        // Fixed anchor that still works on common resolutions,
        // but computed preview positions so they never overlap.
        yJumpLabel = 48;
        yJumpPalette = 70;
        yJumpPreview = yJumpPalette + paletteH + 14;

        yTextLabel = yJumpPreview + 26;
        yTextPalette = yTextLabel + 22;
        yTextPreview = yTextPalette + paletteH + 14;

        // Jump name palette buttons (custom icon tiles)
        for (int i = 0; i < 16; i++) {
            int row = i / 8;
            int col = i % 8;

            int x = startX + col * (BTN_SIZE + GAP);
            int y = yJumpPalette + row * (BTN_SIZE + GAP);

            ResourceLocation icon = iconForIndex(i);
            GuiColorIconButton b = new GuiColorIconButton(BTN_JUMP_BASE + i, x, y, BTN_SIZE, icon);
            b.setSelected(CODES[i].equals(selectedJumpNameCode));
            this.buttonList.add(b);
        }

        // Text palette buttons (custom icon tiles)
        for (int i = 0; i < 16; i++) {
            int row = i / 8;
            int col = i % 8;

            int x = startX + col * (BTN_SIZE + GAP);
            int y = yTextPalette + row * (BTN_SIZE + GAP);

            ResourceLocation icon = iconForIndex(i);
            GuiColorIconButton b = new GuiColorIconButton(BTN_TEXT_BASE + i, x, y, BTN_SIZE, icon);
            b.setSelected(CODES[i].equals(selectedTextCode));
            this.buttonList.add(b);
        }
    }

    private static ResourceLocation iconForIndex(int idx) {
        String name = (idx >= 0 && idx < ICON_NAMES.length) ? ICON_NAMES[idx] : "unknown";
        return new ResourceLocation("sr", ICON_BASE_PATH + name + ".png");
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();

        this.drawCenteredString(this.fontRendererObj, "Text Colors", this.width / 2, 16, 0xFFFFFF);

        this.drawCenteredString(this.fontRendererObj, "Jump name color", this.width / 2, yJumpLabel, 0xFFFFFF);
        this.drawCenteredString(this.fontRendererObj, "Strategy text color", this.width / 2, yTextLabel, 0xFFFFFF);

        // Previews (moved so they do NOT overlap with palettes)
        String jumpPreview = selectedJumpNameCode + "JumpNamePreview" + EnumChatFormatting.RESET;
        String textPreview = selectedTextCode + "Position: x.300 Facing: 12.5 Setup: 1sW Strategy: max fmm" + EnumChatFormatting.RESET;

        this.drawCenteredString(this.fontRendererObj, "Preview: " + jumpPreview, this.width / 2, yJumpPreview, 0xFFFFFF);
        this.drawCenteredString(this.fontRendererObj, "Preview: " + textPreview, this.width / 2, yTextPreview, 0xFFFFFF);

        // Hover tooltip
        String hover = getHoverName(mouseX, mouseY);
        if (hover != null) {
            drawCenteredString(this.fontRendererObj, EnumChatFormatting.GRAY + hover, this.width / 2, this.height - 40, 0xFFFFFF);
        }

        // Draw buttons (our custom buttons render their own look)
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private String getHoverName(int mouseX, int mouseY) {
        for (int i = 0; i < 16; i++) {
            GuiButton bj = getButtonById(BTN_JUMP_BASE + i);
            if (bj != null && isMouseOver(mouseX, mouseY, bj)) {
                String code = CODES[i];
                String name = NAME_BY_CODE.get(code);
                return "Jump name: " + (name != null ? name : code);
            }

            GuiButton bt = getButtonById(BTN_TEXT_BASE + i);
            if (bt != null && isMouseOver(mouseX, mouseY, bt)) {
                String code = CODES[i];
                String name = NAME_BY_CODE.get(code);
                return "Text: " + (name != null ? name : code);
            }
        }
        return null;
    }

    private GuiButton getButtonById(int id) {
        for (Object o : this.buttonList) {
            if (o instanceof GuiButton) {
                GuiButton b = (GuiButton) o;
                if (b.id == id) return b;
            }
        }
        return null;
    }

    private boolean isMouseOver(int mouseX, int mouseY, GuiButton b) {
        return mouseX >= b.xPosition && mouseX < (b.xPosition + b.width)
                && mouseY >= b.yPosition && mouseY < (b.yPosition + b.height);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button == null) return;

        if (button.id == BTN_BACK) {
            Minecraft.getMinecraft().displayGuiScreen(parent);
            return;
        }

        if (button.id == BTN_DONE) {
            ReminderManager.setGlobalInWorldJumpNameColor(selectedJumpNameCode);
            ReminderManager.setGlobalInWorldTextColor(selectedTextCode);
            Minecraft.getMinecraft().displayGuiScreen(parent);
            return;
        }

        if (button.id >= BTN_JUMP_BASE && button.id < BTN_JUMP_BASE + 16) {
            int idx = button.id - BTN_JUMP_BASE;
            selectedJumpNameCode = CODES[idx];
            syncSelectionStates();
            return;
        }

        if (button.id >= BTN_TEXT_BASE && button.id < BTN_TEXT_BASE + 16) {
            int idx = button.id - BTN_TEXT_BASE;
            selectedTextCode = CODES[idx];
            syncSelectionStates();
            return;
        }

        super.actionPerformed(button);
    }

    private void syncSelectionStates() {
        for (int i = 0; i < 16; i++) {
            GuiButton bj = getButtonById(BTN_JUMP_BASE + i);
            if (bj instanceof GuiColorIconButton) {
                ((GuiColorIconButton) bj).setSelected(CODES[i].equals(selectedJumpNameCode));
            }
            GuiButton bt = getButtonById(BTN_TEXT_BASE + i);
            if (bt instanceof GuiColorIconButton) {
                ((GuiColorIconButton) bt).setSelected(CODES[i].equals(selectedTextCode));
            }
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1) { // Esc
            Minecraft.getMinecraft().displayGuiScreen(parent);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    /**
     * Flat square icon button (no vanilla gradient), with selection border.
     * Icon texture is expected to be a standalone image (e.g. 16x16 or 18x18).
     */
    private static class GuiColorIconButton extends GuiButton {

        private final ResourceLocation icon;
        private boolean selected;

        // Colors (ARGB)
        private static final int BG_NORMAL = 0xFF000000;
        private static final int BG_HOVER = 0xFF101010;
        private static final int BG_DISABLED = 0xFF0B0B0B;

        private static final int BORDER_NORMAL = 0xFF5A5A5A;
        private static final int BORDER_HOVER = 0xFF808080;
        private static final int BORDER_DISABLED = 0xFF404040;

        private static final int BORDER_SELECTED = 0xFFFFFFFF;

        public GuiColorIconButton(int id, int x, int y, int size, ResourceLocation icon) {
            super(id, x, y, size, size, "");
            this.icon = icon;
        }

        public void setSelected(boolean selected) {
            this.selected = selected;
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY) {
            if (!this.visible) {
                return;
            }

            this.hovered = mouseX >= this.xPosition && mouseY >= this.yPosition
                    && mouseX < this.xPosition + this.width
                    && mouseY < this.yPosition + this.height;

            int border = BORDER_NORMAL;
            int bg = BG_NORMAL;

            if (!this.enabled) {
                border = BORDER_DISABLED;
                bg = BG_DISABLED;
            } else if (this.hovered) {
                border = BORDER_HOVER;
                bg = BG_HOVER;
            }

            // Outer border
            drawRect(this.xPosition, this.yPosition, this.xPosition + this.width, this.yPosition + this.height, border);
            // Inner background
            drawRect(this.xPosition + 1, this.yPosition + 1, this.xPosition + this.width - 1, this.yPosition + this.height - 1, bg);

            // Selected border overlay (thin white outline outside)
            if (this.selected) {
                drawRect(this.xPosition - 1, this.yPosition - 1, this.xPosition + this.width + 1, this.yPosition, BORDER_SELECTED);
                drawRect(this.xPosition - 1, this.yPosition + this.height, this.xPosition + this.width + 1, this.yPosition + this.height + 1, BORDER_SELECTED);
                drawRect(this.xPosition - 1, this.yPosition, this.xPosition, this.yPosition + this.height, BORDER_SELECTED);
                drawRect(this.xPosition + this.width, this.yPosition, this.xPosition + this.width + 1, this.yPosition + this.height, BORDER_SELECTED);
            }

            // Icon (centered)
            if (icon != null) {
                mc.getTextureManager().bindTexture(icon);

                GlStateManager.color(1f, 1f, 1f, this.enabled ? 1f : 0.6f);
                GlStateManager.enableBlend();
                GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);

                // Draw icon slightly inset so it fits nicely even if icon is 18x18
                int iconMax = this.width - 6;
                int iw = Math.min(18, iconMax);
                int ih = Math.min(18, iconMax);

                int ix = this.xPosition + (this.width - iw) / 2;
                int iy = this.yPosition + (this.height - ih) / 2;

                // Assume the icon texture is exactly iw x ih or larger; take from (0,0)
                this.drawModalRectWithCustomSizedTexture(ix, iy, 0, 0, iw, ih, iw, ih);

                GlStateManager.disableBlend();
                GlStateManager.color(1f, 1f, 1f, 1f);
            }
        }
    }
}