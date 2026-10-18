package me.texyle.startreminders.ui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import me.texyle.startreminders.data.Jump;
import me.texyle.startreminders.data.MapSection;
import me.texyle.startreminders.data.ParkourMap;
import me.texyle.startreminders.services.SectionService;
import me.texyle.startreminders.ui.UiPopup.Entry;
import me.texyle.startreminders.utils.ChatUtils;

/** Creates a section or edits an existing one: name, colors and the range of jumps it covers. */
public class GuiEditSection extends UiScreen {

    private static final int TEXT_COLOR_WHITE = 0xFFFFFFFF;
    private static final int TEXT_COLOR_BLACK = 0xFF000000;

    private static final int[] PRESET_COLORS = {
            0xFFE06666, 0xFFF6B26B, 0xFFFFD966, 0xFF93C47D, 0xFF76A5AF, 0xFF6FA8DC,
            0xFF8E7CC3, 0xFFC27BA0, 0xFFCCCCCC, 0xFF999999, 0xFFFFFFFF, 0xFF434343
    };

    private static final int ROW_H = 30;
    private static final int LABEL_W = 84;

    private final ParkourMap map;
    private final int levelOneBased;
    private final MapSection editing; // null => create

    // Edited values; kept here so they survive screen rebuilds.
    private String name = null;
    private int colorArgb = 0xFFFFFFFF;
    private int textColorArgb = TEXT_COLOR_WHITE;
    private Integer startIdx = null;
    private Integer endIdx = null;
    private boolean loaded = false;

    private UiTextField nameField;
    private UiTextField hexField;

    public GuiEditSection(GuiScreen parent, ParkourMap map, int levelOneBased, MapSection editing) {
        super(parent);
        this.map = map;
        this.levelOneBased = levelOneBased;
        this.editing = editing;
    }

    @Override
    protected String getTitle() {
        return (editing != null ? "Edit section" : "New section") + " - level " + levelOneBased;
    }

    @Override
    protected int getPanelWidth() {
        return 460;
    }

    @Override
    protected int getPanelHeight() {
        return 300;
    }

    private void loadOnce() {
        if (loaded) return;
        loaded = true;

        if (editing == null) {
            name = "";
            return;
        }

        name = editing.getName() != null ? editing.getName() : "";
        colorArgb = editing.getColorArgb();
        textColorArgb = editing.getTextColorArgb();

        int s = editing.getStartJumpIndex();
        int e = editing.getEndJumpIndex();
        startIdx = (s >= 0) ? Integer.valueOf(s) : null;
        endIdx = (e >= 0) ? Integer.valueOf(e) : null;
    }

    @Override
    protected void build(int x, int y, int w, int h) {
        loadOnce();

        int fieldX = x + LABEL_W;
        int fieldW = w - LABEL_W;
        int rowY = y + 2;

        nameField = add(new UiTextField(name, 512).placeholder("Section name").onChange(t -> name = t), fieldX, rowY, fieldW, 20);
        nameField.setFocused(true);
        rowY += ROW_H;

        // Color: presets plus a hex field
        add(new PresetRow(), fieldX, rowY, fieldW - 96, 20);
        hexField = add(new UiTextField(hexOf(colorArgb), 7).onChange(this::onHexTyped), fieldX + fieldW - 88, rowY, 88, 20);
        rowY += ROW_H;

        UiSegmented textColorChoice = new UiSegmented(new String[] { "Light", "Dark" }, (textColorArgb & 0xFFFFFF) == 0 ? 1 : 0,
                index -> textColorArgb = index == 1 ? TEXT_COLOR_BLACK : TEXT_COLOR_WHITE);
        add(textColorChoice, fieldX, rowY + 1, textColorChoice.preferredWidth(), 18);
        rowY += ROW_H;

        add(new UiDropdown(popup, "", () -> pickLabel(startIdx), () -> jumpEntries(true)), fieldX, rowY, fieldW, 20);
        rowY += ROW_H;
        add(new UiDropdown(popup, "", () -> pickLabel(endIdx), () -> jumpEntries(false)), fieldX, rowY, fieldW, 20);

        addFooter(
                new UiButton("Cancel", UiButton.Style.SECONDARY, this::onBack),
                new UiButton("Save", UiButton.Style.PRIMARY, this::save));
    }

    @Override
    protected void drawContent(int mouseX, int mouseY, float partialTicks) {
        int rowY = contentY + 2;
        label("NAME", rowY);
        rowY += ROW_H;
        label("COLOR", rowY);
        rowY += ROW_H;
        label("TEXT", rowY);
        rowY += ROW_H;
        label("START", rowY);
        rowY += ROW_H;
        label("END", rowY);

        if (startIdx != null && endIdx != null && endIdx < startIdx) {
            UiDraw.text("End cannot be before start.", contentX + LABEL_W, footerY() - 16, UiTheme.DANGER_HOVER);
        } else if (startIdx == null && endIdx == null) {
            UiDraw.text("Without a range the section is saved but not shown.", contentX + LABEL_W, footerY() - 16, UiTheme.TEXT_MUTED);
        }
    }

    private void label(String text, int rowY) {
        UiDraw.text(text, contentX, rowY + 6, UiTheme.TEXT_MUTED);
    }

    // -------------------------
    // Color
    // -------------------------

    private static String hexOf(int argb) {
        return String.format("#%06X", argb & 0xFFFFFF);
    }

    private void onHexTyped(String text) {
        String t = text.trim();
        if (t.startsWith("#")) t = t.substring(1);
        if (t.length() != 6) return;

        try {
            colorArgb = 0xFF000000 | Integer.parseInt(t, 16);
        } catch (NumberFormatException ignored) {
            // keep the previous color until the text is a valid hex value
        }
    }

    /** A row of preset color swatches; the picked color also goes into the hex field. */
    private final class PresetRow extends UiWidget {
        private static final int SIZE = 16;

        @Override
        public void draw(int mouseX, int mouseY) {
            int gap = Math.max(2, (w - PRESET_COLORS.length * SIZE) / (PRESET_COLORS.length - 1));
            for (int i = 0; i < PRESET_COLORS.length; i++) {
                int cx = x + i * (SIZE + gap);
                boolean selected = (PRESET_COLORS[i] & 0xFFFFFF) == (colorArgb & 0xFFFFFF);
                boolean hover = UiDraw.inside(mouseX, mouseY, cx, y + 2, SIZE, SIZE);
                int border = selected ? UiTheme.TEXT : (hover ? UiTheme.TEXT_MUTED : UiTheme.CONTROL_BORDER);
                UiDraw.box(cx, y + 2, SIZE, SIZE, PRESET_COLORS[i], border);
            }
        }

        @Override
        public boolean mouseClicked(int mouseX, int mouseY, int button) {
            if (button != 0 || !contains(mouseX, mouseY)) return false;

            int gap = Math.max(2, (w - PRESET_COLORS.length * SIZE) / (PRESET_COLORS.length - 1));
            for (int i = 0; i < PRESET_COLORS.length; i++) {
                int cx = x + i * (SIZE + gap);
                if (UiDraw.inside(mouseX, mouseY, cx, y + 2, SIZE, SIZE)) {
                    colorArgb = PRESET_COLORS[i];
                    hexField.setText(hexOf(colorArgb));
                    return true;
                }
            }
            return true;
        }
    }

    // -------------------------
    // Range
    // -------------------------

    private String pickLabel(Integer idx) {
        if (idx == null) return "Not set";
        return (idx.intValue() + 1) + ")  " + jumpName(idx.intValue());
    }

    private String jumpName(int index) {
        if (map == null) return "<null>";
        ArrayList<Jump> js = map.getJumps();
        if (js == null || js.isEmpty()) return "<no jumps>";
        if (index < 0 || index >= js.size()) return "<out of range>";
        Jump j = js.get(index);
        if (j == null) return "<null jump>";
        String id = j.getId();
        return (id != null && id.trim().length() > 0) ? id : "<unnamed>";
    }

    private List<Entry> jumpEntries(final boolean isStart) {
        List<Entry> entries = new ArrayList<Entry>();
        Integer current = isStart ? startIdx : endIdx;

        entries.add(Entry.choice("Not set", current == null, () -> pick(isStart, null)));

        int count = (map != null && map.getJumps() != null) ? map.getJumps().size() : 0;
        for (int i = 0; i < count; i++) {
            final int idx = i;
            entries.add(Entry.choice((i + 1) + ")  " + jumpName(i), current != null && current.intValue() == i,
                    () -> pick(isStart, Integer.valueOf(idx))));
        }
        return entries;
    }

    private void pick(boolean isStart, Integer index) {
        if (isStart) {
            startIdx = index;
            if (index != null && endIdx != null && endIdx < index) endIdx = index;
        } else {
            endIdx = index;
            if (index != null && startIdx != null && index < startIdx) startIdx = index;
        }
    }

    // -------------------------
    // Save
    // -------------------------

    private void save() {
        String error = SectionService.saveSection(map, levelOneBased, editing,
                name, colorArgb, textColorArgb, startIdx, endIdx);

        if (error != null) {
            ChatUtils.sendChat(Minecraft.getMinecraft(), net.minecraft.util.EnumChatFormatting.RED + error);
            return;
        }

        Minecraft.getMinecraft().displayGuiScreen(parent);
    }

    @Override
    protected void onEnter() {
        save();
    }
}
