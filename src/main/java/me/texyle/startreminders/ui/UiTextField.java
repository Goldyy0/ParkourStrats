package me.texyle.startreminders.ui;

import java.util.function.Consumer;

import net.minecraft.client.gui.GuiTextField;

/** Single-line text input in the mod's style (wraps the vanilla text field for editing behavior). */
public class UiTextField extends UiWidget {

    private static int nextId = 5000;

    private final GuiTextField field;
    private String placeholder = "";
    private boolean invalid;
    private Consumer<String> onChange;
    private boolean readOnly;

    public UiTextField(String initialText, int maxLength) {
        field = new GuiTextField(nextId++, UiDraw.font(), 0, 0, 10, 10);
        field.setEnableBackgroundDrawing(false);
        field.setMaxStringLength(maxLength);
        field.setTextColor(UiTheme.TEXT);
        field.setDisabledTextColour(UiTheme.TEXT_DISABLED);
        field.setText(initialText != null ? initialText : "");
        field.setCursorPositionEnd();
    }

    public UiTextField placeholder(String text) {
        this.placeholder = text != null ? text : "";
        return this;
    }

    public UiTextField onChange(Consumer<String> handler) {
        this.onChange = handler;
        return this;
    }

    public String getText() {
        String t = field.getText();
        return t != null ? t : "";
    }

    public void setText(String text) {
        field.setText(text != null ? text : "");
        field.setCursorPositionEnd();
    }

    public boolean isFocused() {
        return field.isFocused();
    }

    public void setFocused(boolean focused) {
        field.setFocused(focused);
    }

    /** Marks the content as wrong (red border). */
    public void setInvalid(boolean invalid) {
        this.invalid = invalid;
    }

    /** A read-only field shows text but cannot be edited. */
    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
        field.setEnabled(!readOnly);
        if (readOnly) field.setFocused(false);
    }

    @Override
    public void setBounds(int x, int y, int w, int h) {
        super.setBounds(x, y, w, h);
        field.xPosition = x + 6;
        field.yPosition = y + (h - 8) / 2;
        field.width = w - 12;
        field.height = h;

        // The field was created with a tiny width, which scrolled its text to the end; recompute for the real width.
        field.setCursorPositionZero();
        field.setCursorPositionEnd();
    }

    @Override
    public void draw(int mouseX, int mouseY) {
        if (!visible) return;

        int border = invalid ? UiTheme.DANGER_HOVER
                : (field.isFocused() ? UiTheme.ACCENT_HOVER : UiTheme.CONTROL_BORDER);
        UiDraw.box(x, y, w, h, readOnly ? UiTheme.CARD_BG : UiTheme.CONTROL_BG, border);

        if (getText().isEmpty() && !field.isFocused() && !placeholder.isEmpty()) {
            UiDraw.text(UiDraw.fit(placeholder, w - 12), x + 6, y + (h - 8) / 2, UiTheme.TEXT_DISABLED);
        }

        field.drawTextBox();
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (!visible) return false;
        field.mouseClicked(mouseX, mouseY, button);
        return contains(mouseX, mouseY);
    }

    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        if (!visible || readOnly || !field.isFocused()) return false;

        String before = getText();
        boolean used = field.textboxKeyTyped(typedChar, keyCode);
        if (onChange != null && !before.equals(getText())) {
            onChange.accept(getText());
        }
        return used;
    }

    @Override
    public void update() {
        field.updateCursorCounter();
    }
}
