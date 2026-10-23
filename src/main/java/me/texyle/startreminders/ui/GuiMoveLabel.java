package me.texyle.startreminders.ui;

import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiScreen;

import me.texyle.startreminders.utils.FacingAxis;

/**
 * Moves the position of a jump label one block per click: up and down along Y, and front, back, left and right
 * relative to the axis the player looks along. Nothing changes in the editor until Confirm.
 */
public class GuiMoveLabel extends UiScreen {

    private enum Arrow { UP, DOWN, LEFT, RIGHT }

    private static final int BUTTON_W = 72;
    private static final int ROW_H = 24;
    private static final int LEFT_W = BUTTON_W;
    private static final int RIGHT_W = BUTTON_W * 2 + 8;
    private static final int GAP = 60;

    private final int baseX;
    private final int baseY;
    private final int baseZ;
    private final Consumer<int[]> onConfirm;

    private int moveX;
    private int moveY;
    private int moveZ;

    /** @param onConfirm receives the new {x, y, z} */
    public GuiMoveLabel(GuiScreen parent, int x, int y, int z, Consumer<int[]> onConfirm) {
        super(parent);
        this.baseX = x;
        this.baseY = y;
        this.baseZ = z;
        this.onConfirm = onConfirm;
    }

    @Override
    protected String getTitle() {
        return "Move label";
    }

    @Override
    protected int getPanelWidth() {
        return 380;
    }

    @Override
    protected int getPanelHeight() {
        return 232;
    }

    @Override
    protected void build(int x, int y, int w, int h) {
        int leftX = leftPanelX();
        int rightX = rightPanelX();
        int top = y + 18;

        add(new ArrowButton(Arrow.UP, "Up", () -> moveY++), leftX, top, BUTTON_W, 20);
        add(new ArrowButton(Arrow.DOWN, "Down", () -> moveY--), leftX, top + ROW_H, BUTTON_W, 20);

        int centerX = rightX + (RIGHT_W - BUTTON_W) / 2;
        add(new ArrowButton(Arrow.UP, "Front", () -> moveAlong(1, 0)), centerX, top, BUTTON_W, 20);
        add(new ArrowButton(Arrow.LEFT, "Left", () -> moveAlong(0, -1)), rightX, top + ROW_H, BUTTON_W, 20);
        add(new ArrowButton(Arrow.RIGHT, "Right", () -> moveAlong(0, 1)), rightX + RIGHT_W - BUTTON_W, top + ROW_H, BUTTON_W, 20);
        add(new ArrowButton(Arrow.DOWN, "Back", () -> moveAlong(-1, 0)), centerX, top + ROW_H * 2, BUTTON_W, 20);

        addFooter(
                new UiButton("Cancel", UiButton.Style.SECONDARY, this::onBack),
                new UiButton("Confirm", UiButton.Style.PRIMARY, this::confirm));
    }

    /** The two control panels side by side, together centered in the window. */
    private int leftPanelX() {
        return contentX + (contentW - (LEFT_W + GAP + RIGHT_W)) / 2;
    }

    private int rightPanelX() {
        return leftPanelX() + LEFT_W + GAP;
    }

    private float yaw() {
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        return player != null ? player.rotationYaw : 0f;
    }

    /** One block along the viewing axis (forward) and sideways (right), whatever the axis is. */
    private void moveAlong(int forward, int right) {
        int fx = FacingAxis.dx(yaw());
        int fz = FacingAxis.dz(yaw());
        // Looking along (fx, fz), the right hand points to (-fz, fx).
        moveX += forward * fx + right * -fz;
        moveZ += forward * fz + right * fx;
    }

    private void confirm() {
        if (onConfirm != null) onConfirm.accept(new int[] { baseX + moveX, baseY + moveY, baseZ + moveZ });
        Minecraft.getMinecraft().displayGuiScreen(parent);
    }

    @Override
    protected void onEnter() {
        confirm();
    }

    @Override
    protected void drawContent(int mouseX, int mouseY, float partialTicks) {
        int leftX = leftPanelX();
        int rightX = rightPanelX();

        UiDraw.centered("UP/DOWN", leftX + LEFT_W / 2, contentY + 2, UiTheme.TEXT_MUTED);
        UiDraw.centered("FRONT/BACK/LEFT/RIGHT", rightX + RIGHT_W / 2, contentY + 2, UiTheme.TEXT_MUTED);
        UiDraw.fill(leftX + LEFT_W + GAP / 2, contentY + 2, 1, ROW_H * 3 + 14, UiTheme.PANEL_BORDER);

        int infoY = contentY + 18 + ROW_H * 3 + 12;
        String position = "New position: " + (baseX + moveX) + ", " + (baseY + moveY) + ", " + (baseZ + moveZ);
        UiDraw.centered(position, contentX + contentW / 2, infoY, UiTheme.TEXT);

        UiDraw.centered("Current AXIS: (" + FacingAxis.name(yaw()) + ")", contentX + contentW / 2,
                infoY + 18, UiTheme.ACCENT);
    }

    /** A button with a triangle pointing the way it moves and its name beside it. */
    private static final class ArrowButton extends UiWidget {

        private final Arrow arrow;
        private final String label;
        private final Runnable action;

        ArrowButton(Arrow arrow, String label, Runnable action) {
            this.arrow = arrow;
            this.label = label;
            this.action = action;
        }

        @Override
        public void draw(int mouseX, int mouseY) {
            if (!visible) return;

            boolean hover = contains(mouseX, mouseY);
            UiDraw.box(x, y, w, h, hover ? UiTheme.CONTROL_HOVER : UiTheme.CONTROL_BG, UiTheme.CONTROL_BORDER);

            int color = hover ? UiTheme.TEXT : UiTheme.ACCENT;
            drawTriangle(x + 8, y + (h - 9) / 2, color);
            UiDraw.text(label, x + 24, y + (h - 8) / 2 + 1, hover ? UiTheme.TEXT : UiTheme.TEXT_MUTED);
        }

        /** A 9 x 9 triangle with its top left corner at (tx, ty). */
        private void drawTriangle(int tx, int ty, int color) {
            for (int i = 0; i < 5; i++) {
                int len = 1 + i * 2;
                switch (arrow) {
                    case UP:
                        UiDraw.fill(tx + 4 - i, ty + i, len, 1, color);
                        break;
                    case DOWN:
                        UiDraw.fill(tx + 4 - i, ty + 8 - i, len, 1, color);
                        break;
                    case LEFT:
                        UiDraw.fill(tx + i, ty + 4 - i, 1, len, color);
                        break;
                    default:
                        UiDraw.fill(tx + 8 - i, ty + 4 - i, 1, len, color);
                        break;
                }
            }
        }

        @Override
        public boolean mouseClicked(int mouseX, int mouseY, int button) {
            if (!visible || button != 0 || !contains(mouseX, mouseY)) return false;
            action.run();
            return true;
        }
    }
}
