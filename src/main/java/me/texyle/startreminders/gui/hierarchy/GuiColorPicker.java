package me.texyle.startreminders.gui.hierarchy;

import java.io.IOException;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import org.lwjgl.input.Mouse;

public class GuiColorPicker extends GuiScreen {

    public interface IColorHandler {
        void onPick(int argb);
        void onCancel();
    }

    private static final int BTN_OK = 1;
    private static final int BTN_CANCEL = 2;

    private final GuiScreen parent;
    private final IColorHandler handler;

    private int selectedArgb;

    // Layout
    private int centerX;
    private int baseY;
    private int panelW;

    // Sliders
    private Slider sliderR;
    private Slider sliderG;
    private Slider sliderB;

    // Hex input
    private GuiTextField hexField;
    private boolean isDraggingSlider = false;

    // Prevent feedback loops between slider updates and text field updates
    private boolean suppressHexUpdate = false;
    private boolean suppressSliderUpdateFromHex = false;

    public GuiColorPicker(GuiScreen parent, int initialArgb, IColorHandler handler) {
        this.parent = parent;
        this.handler = handler;
        this.selectedArgb = initialArgb;
    }

    @Override
    public void initGui() {
        super.initGui();

        this.buttonList.clear();

        centerX = this.width / 2;
        panelW = 320;
        baseY = this.height / 2 - 90;

        int buttonsY = this.height - 48;
        this.buttonList.add(new GuiButton(BTN_OK, centerX - 110, buttonsY, 100, 20, "OK"));
        this.buttonList.add(new GuiButton(BTN_CANCEL, centerX + 10, buttonsY, 100, 20, "Cancel"));

        int startX = centerX - panelW / 2;

        int r = (selectedArgb >>> 16) & 0xFF;
        int g = (selectedArgb >>> 8) & 0xFF;
        int b = (selectedArgb) & 0xFF;

        sliderR = new Slider("R", startX, baseY + 28, panelW, 16, r);
        sliderG = new Slider("G", startX, baseY + 58, panelW, 16, g);
        sliderB = new Slider("B", startX, baseY + 88, panelW, 16, b);

        hexField = new GuiTextField(200, this.fontRendererObj, startX + 86, baseY + 142, 160, 20);
        hexField.setMaxStringLength(16);
        hexField.setFocused(false);

        syncHexFromColor();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // Keep it consistent with hierarchy style (not dirt)
        drawRect(0, 0, this.width, this.height, 0xFF101010);

        this.drawCenteredString(this.fontRendererObj, "Pick a color", this.width / 2, 12, 0xFFFFFF);

        int startX = centerX - panelW / 2;

        // Preview box (center)
        int pvW = 120;
        int pvH = 34;
        int pvX = centerX - pvW / 2;
        int pvY = baseY - 6;
        drawRect(pvX - 1, pvY - 1, pvX + pvW + 1, pvY + pvH + 1, 0xFF2A2A2A);
        drawRect(pvX, pvY, pvX + pvW, pvY + pvH, 0xFF101010);
        drawRect(pvX + 2, pvY + 2, pvX + pvW - 2, pvY + pvH - 2, selectedArgb);

        // Sliders
        sliderR.draw(mouseX, mouseY);
        sliderG.draw(mouseX, mouseY);
        sliderB.draw(mouseX, mouseY);

        // Hex input
        this.fontRendererObj.drawString("Hex code:", startX, baseY + 148, 0xFFFFFF, true);
        hexField.drawTextBox();

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (hexField != null) {
            hexField.updateCursorCounter();
        }

        // If user types valid hex, update sliders immediately (live)
        if (!suppressSliderUpdateFromHex && hexField != null && hexField.isFocused()) {
            Integer rgb = tryParseHexToRgb(hexField.getText());
            if (rgb != null) {
                int r = (rgb >>> 16) & 0xFF;
                int g = (rgb >>> 8) & 0xFF;
                int b = (rgb) & 0xFF;

                suppressHexUpdate = true;
                sliderR.setValue(r);
                sliderG.setValue(g);
                sliderB.setValue(b);
                updateSelectedFromSliders();
                suppressHexUpdate = false;
            }
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (hexField != null) {
            hexField.mouseClicked(mouseX, mouseY, mouseButton);
        }

        if (mouseButton == 0) {
            if (sliderR.mousePressed(mouseX, mouseY) || sliderG.mousePressed(mouseX, mouseY) || sliderB.mousePressed(mouseX, mouseY)) {
                isDraggingSlider = true;
                updateSelectedFromSliders();
                if (!suppressHexUpdate) syncHexFromColor();
                return; // don't pass through to super yet; prevents weird focus issues
            }
        }

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (isDraggingSlider && clickedMouseButton == 0) {
            sliderR.mouseDragged(mouseX, mouseY);
            sliderG.mouseDragged(mouseX, mouseY);
            sliderB.mouseDragged(mouseX, mouseY);

            updateSelectedFromSliders();
            if (!suppressHexUpdate) syncHexFromColor();
        }

        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        if (state == 0) {
            isDraggingSlider = false;
            sliderR.mouseReleased();
            sliderG.mouseReleased();
            sliderB.mouseReleased();
        }
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();

        // Optional: mouse wheel over a slider to fine-adjust
        int dWheel = Mouse.getEventDWheel();
        if (dWheel != 0) {
            int mouseX = Mouse.getEventX() * this.width / this.mc.displayWidth;
            int mouseY = this.height - (Mouse.getEventY() * this.height / this.mc.displayHeight) - 1;

            int delta = (dWheel > 0) ? 1 : -1;

            boolean changed = false;
            if (sliderR.isMouseOver(mouseX, mouseY)) { sliderR.nudge(delta); changed = true; }
            else if (sliderG.isMouseOver(mouseX, mouseY)) { sliderG.nudge(delta); changed = true; }
            else if (sliderB.isMouseOver(mouseX, mouseY)) { sliderB.nudge(delta); changed = true; }

            if (changed) {
                updateSelectedFromSliders();
                if (!suppressHexUpdate) syncHexFromColor();
            }
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        // Esc
        if (keyCode == 1) {
            if (handler != null) handler.onCancel();
            Minecraft.getMinecraft().displayGuiScreen(parent);
            return;
        }

        // Enter -> OK
        if (keyCode == 28) {
            if (handler != null) handler.onPick(selectedArgb);
            Minecraft.getMinecraft().displayGuiScreen(parent);
            return;
        }

        if (hexField != null && hexField.isFocused()) {
            suppressSliderUpdateFromHex = true;
            hexField.textboxKeyTyped(typedChar, keyCode);
            suppressSliderUpdateFromHex = false;

            // Normalize: allow user to type with or without '#'
            String t = hexField.getText();
            if (t != null && t.length() > 0 && t.charAt(0) == '#') {
                // keep, but parse ignores it
            }

            // If invalid, don't spam, but keep what user typed.
            super.keyTyped(typedChar, keyCode);
            return;
        }

        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button == null) return;

        if (button.id == BTN_OK) {
            if (handler != null) handler.onPick(selectedArgb);
            Minecraft.getMinecraft().displayGuiScreen(parent);
            return;
        }

        if (button.id == BTN_CANCEL) {
            if (handler != null) handler.onCancel();
            Minecraft.getMinecraft().displayGuiScreen(parent);
        }
    }

    private void updateSelectedFromSliders() {
        int r = sliderR.getValue();
        int g = sliderG.getValue();
        int b = sliderB.getValue();

        selectedArgb = 0xFF000000 | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    private void syncHexFromColor() {
        if (hexField == null) return;

        int r = (selectedArgb >>> 16) & 0xFF;
        int g = (selectedArgb >>> 8) & 0xFF;
        int b = (selectedArgb) & 0xFF;

        String hex = String.format("#%02X%02X%02X", r, g, b);

        // Keep user's cursor reasonable
        int cursor = hexField.getCursorPosition();

        suppressSliderUpdateFromHex = true;
        hexField.setText(hex);
        suppressSliderUpdateFromHex = false;

        if (cursor >= 0) {
            hexField.setCursorPosition(Math.min(cursor, hexField.getText().length()));
        }
    }

    // Parses #RRGGBB or #AARRGGBB (if user supplies alpha, we ignore alpha but accept it)
    // Returns 0x00RRGGBB or null if invalid/incomplete.
    private static Integer tryParseHexToRgb(String text) {
        if (text == null) return null;

        String t = text.trim();
        if (t.startsWith("#")) t = t.substring(1);
        if (t.length() == 0) return null;

        // allow user to type gradually; only parse when length is 6 or 8
        if (t.length() != 6 && t.length() != 8) return null;

        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            boolean ok = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!ok) return null;
        }

        try {
            long v = Long.parseLong(t, 16);
            if (t.length() == 6) {
                return (int) (v & 0xFFFFFF);
            } else {
                // AARRGGBB -> ignore alpha part
                return (int) (v & 0xFFFFFF);
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    // -----------------------
    // Slider helper (0..255)
    // -----------------------
    private class Slider {
        private final String label;
        private final int x;
        private final int y;
        private final int w;
        private final int h;

        private int value; // 0..255
        private boolean dragging = false;

        Slider(String label, int x, int y, int w, int h, int initial) {
            this.label = label;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            setValue(initial);
        }

        int getValue() {
            return value;
        }

        void setValue(int v) {
            if (v < 0) v = 0;
            if (v > 255) v = 255;
            this.value = v;
        }

        void nudge(int delta) {
            setValue(this.value + delta);
        }

        boolean isMouseOver(int mx, int my) {
            return mx >= x && mx <= x + w && my >= y && my <= y + h;
        }

        boolean mousePressed(int mx, int my) {
            if (!isMouseOver(mx, my)) return false;
            dragging = true;
            setValueFromMouse(mx);
            return true;
        }

        void mouseDragged(int mx, int my) {
            if (!dragging) return;
            setValueFromMouse(mx);
        }

        void mouseReleased() {
            dragging = false;
        }

        private void setValueFromMouse(int mx) {
            int innerX = x + 36;
            int innerW = w - 36 - 44;
            if (innerW < 1) innerW = 1;

            int rel = mx - innerX;
            if (rel < 0) rel = 0;
            if (rel > innerW) rel = innerW;

            float t = (float) rel / (float) innerW;
            int v = (int) Math.round(t * 255.0f);
            setValue(v);
        }

        void draw(int mx, int my) {
            // Row background
            drawRect(x, y, x + w, y + h, 0xFF0D0D0D);
            drawRect(x, y, x + w, y + 1, 0xFF2A2A2A);
            drawRect(x, y + h - 1, x + w, y + h, 0xFF2A2A2A);

            // Label + numeric
            fontRendererObj.drawString(label + ":", x + 4, y + 4, 0xFFFFFF, true);
            String valTxt = String.valueOf(value);
            fontRendererObj.drawString(valTxt, x + w - 6 - fontRendererObj.getStringWidth(valTxt), y + 4, 0xFFFFFF, true);

            // Track
            int trackX = x + 36;
            int trackW = w - 36 - 44;
            if (trackW < 1) trackW = 1;

            int trackY = y + (h / 2) - 2;
            drawRect(trackX, trackY, trackX + trackW, trackY + 4, 0xFF2A2A2A);

            // Thumb
            float t = value / 255.0f;
            int thumbX = trackX + (int) Math.round(t * trackW);

            int thumbW = 6;
            int tx1 = thumbX - (thumbW / 2);
            int tx2 = tx1 + thumbW;
            drawRect(tx1, y + 2, tx2, y + h - 2, 0xFFAAAAAA);

            // Hover outline
            if (isMouseOver(mx, my) || dragging) {
                drawRect(x, y, x + w, y + 1, 0xFF555555);
                drawRect(x, y + h - 1, x + w, y + h, 0xFF555555);
                drawRect(x, y, x + 1, y + h, 0xFF555555);
                drawRect(x + w - 1, y, x + w, y + h, 0xFF555555);
            }
        }
    }

    @SuppressWarnings("unused")
    private void sendClientChat(String msg) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null || msg == null) return;
        mc.thePlayer.addChatMessage(new ChatComponentText(msg));
    }
}