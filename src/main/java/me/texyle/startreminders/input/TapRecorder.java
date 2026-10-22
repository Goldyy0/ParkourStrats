package me.texyle.startreminders.input;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import me.texyle.startreminders.template.TemplateMode;
import me.texyle.startreminders.utils.ChatUtils;

/**
 * Records the player's "taps" for the SETUP field of a strategy.
 *
 * A tap is one of W, A, S, D held for exactly one tick (50 ms). Each tap is written as a short code made of its
 * kind and the key, and equal taps in a row are counted: {@code 1uW 1sW 2ssS}.
 * <ul>
 * <li>{@code u}   unshifted: no sprint, jump or sneak</li>
 * <li>{@code r}   run: sprinting, no jump or sneak</li>
 * <li>{@code s}   shift: sneaking, no sprint or jump</li>
 * <li>{@code as}  air shift: sneaking, 1 to 11 ticks after a jump, no sprint</li>
 * <li>{@code ss}  sword shift: right button on a sword and sneaking, no sprint or jump</li>
 * <li>{@code us}  unshifted sword: right button on a sword, no sneak, sprint or jump</li>
 * <li>{@code ass} air sword shift: like {@code as} with the right button on a sword</li>
 * </ul>
 * Recording ends when the player clicks [Finish] (or [Cancel]) in chat.
 */
public class TapRecorder {

    /** The client command the chat buttons run. */
    public static final String COMMAND = "pstaps";

    private static final String[] KEY_LETTERS = { "W", "A", "S", "D" };
    private static final int AIR_WINDOW_TICKS = 11;

    private static boolean recording;
    private static Consumer<String> onFinish;
    private static GuiScreen returnTo;

    private static final List<String> taps = new ArrayList<String>();

    // Per-key state, W A S D
    private static final boolean[] wasDown = new boolean[4];
    private static final int[] heldTicks = new int[4];
    private static final String[] startKind = new String[4];

    private static boolean jumpWasDown;
    private static int ticksSinceJump = -1; // -1: no jump yet

    public static boolean isRecording() {
        return recording;
    }

    /**
     * Closes the current screen and starts recording.
     *
     * @param onFinish receives the recorded text if there was any
     * @param returnTo the screen to open again when recording ends
     */
    public static void start(Consumer<String> onFinish, GuiScreen returnTo) {
        TapRecorder.onFinish = onFinish;
        TapRecorder.returnTo = returnTo;
        taps.clear();
        for (int i = 0; i < 4; i++) {
            wasDown[i] = false;
            heldTicks[i] = 0;
            startKind[i] = null;
        }
        jumpWasDown = false;
        ticksSinceJump = -1;
        recording = true;

        Minecraft mc = Minecraft.getMinecraft();
        mc.displayGuiScreen(null);
        sendPrompt(mc);
    }

    public static void finish() {
        if (!recording) return;
        end(true);
    }

    public static void cancel() {
        if (!recording) return;
        end(false);
    }

    private static void end(final boolean keep) {
        recording = false;
        final String text = compressed();
        final Consumer<String> handler = onFinish;
        final GuiScreen back = returnTo;
        onFinish = null;
        returnTo = null;

        // Run on the next tick: a typed command is followed by the chat closing itself, which would close this too.
        final Minecraft mc = Minecraft.getMinecraft();
        mc.addScheduledTask(new Runnable() {
            @Override
            public void run() {
                if (keep && handler != null) {
                    if (text.isEmpty()) {
                        ChatUtils.sendAlways(Minecraft.getMinecraft(), ChatUtils.WARNING_PREFIX + "No taps were recorded; SETUP was left unchanged.");
                    } else {
                        handler.accept(text);
                    }
                }
                mc.displayGuiScreen(back);
            }
        });
    }

    private static void sendPrompt(Minecraft mc) {
        if (mc.thePlayer == null) return;

        ChatComponentText message = new ChatComponentText(ChatUtils.INFO_PREFIX
                + "Recording taps... ");
        message.appendSibling(button("[Finish]", EnumChatFormatting.GREEN, "/" + COMMAND + " finish", "Stop and fill SETUP"));
        message.appendText(" ");
        message.appendSibling(button("[Cancel]", EnumChatFormatting.RED, "/" + COMMAND + " cancel", "Stop without changing SETUP"));
        message.appendText(EnumChatFormatting.GRAY + " or type /" + COMMAND + " finish");
        mc.thePlayer.addChatMessage(message);
    }

    private static ChatComponentText button(String label, EnumChatFormatting color, String command, String hint) {
        ChatComponentText text = new ChatComponentText(color + label);
        text.setChatStyle(new ChatStyle()
                .setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ChatComponentText(hint))));
        return text;
    }

    // -------------------------
    // Recording
    // -------------------------

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (!recording || event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.thePlayer;
        if (player == null) {
            // Left the world while recording.
            recording = false;
            onFinish = null;
            returnTo = null;
            return;
        }

        // With a screen open (chat) no key counts as pressed.
        boolean inGame = mc.currentScreen == null;

        boolean jumpDown = inGame && mc.gameSettings.keyBindJump.isKeyDown();
        if (jumpDown && !jumpWasDown) {
            ticksSinceJump = 0;
        } else if (ticksSinceJump >= 0) {
            ticksSinceJump = Math.min(ticksSinceJump + 1, 1000);
        }
        jumpWasDown = jumpDown;

        KeyBinding[] keys = {
                mc.gameSettings.keyBindForward, mc.gameSettings.keyBindLeft,
                mc.gameSettings.keyBindBack, mc.gameSettings.keyBindRight
        };

        for (int i = 0; i < 4; i++) {
            boolean down = inGame && keys[i].isKeyDown();

            if (down && !wasDown[i]) {
                heldTicks[i] = 1;
                startKind[i] = classify(mc, player, jumpDown);
            } else if (down) {
                heldTicks[i]++;
            } else if (wasDown[i]) {
                if (heldTicks[i] == 1 && startKind[i] != null) {
                    taps.add(startKind[i] + KEY_LETTERS[i]);
                }
                heldTicks[i] = 0;
                startKind[i] = null;
            }
            wasDown[i] = down;
        }
    }

    /** @return the kind of tap for the current state, or null if it is not one of the supported kinds */
    private static String classify(Minecraft mc, EntityPlayerSP player, boolean jumpDown) {
        boolean sprint = player.isSprinting() || mc.gameSettings.keyBindSprint.isKeyDown();
        boolean sneak = mc.gameSettings.keyBindSneak.isKeyDown();

        ItemStack held = player.getHeldItem();
        boolean sword = mc.gameSettings.keyBindUseItem.isKeyDown() && held != null && held.getItem() instanceof ItemSword;

        boolean jumpActive = jumpDown || (ticksSinceJump >= 0 && ticksSinceJump <= AIR_WINDOW_TICKS);
        boolean air = ticksSinceJump >= 1 && ticksSinceJump <= AIR_WINDOW_TICKS;

        if (sprint) {
            return (!sneak && !jumpActive && !sword) ? "r" : null;
        }
        if (!jumpActive) {
            if (!sneak) return sword ? "us" : "u";
            return sword ? "ss" : "s";
        }
        if (air && sneak) {
            return sword ? "ass" : "as";
        }
        return null;
    }

    /** The recorded taps in order, equal neighbours counted: "1uW 1sW 2ssS". */
    private static String compressed() {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < taps.size()) {
            String tap = taps.get(i);
            int count = 1;
            while (i + count < taps.size() && taps.get(i + count).equals(tap)) count++;

            if (out.length() > 0) out.append(' ');
            out.append(count).append(tap);
            i += count;
        }
        return out.toString();
    }

    // -------------------------
    // HUD
    // -------------------------

    /** The taps so far, under the crosshair (below the template mode lines when those are shown). */
    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post event) {
        if (!recording || event.type != RenderGameOverlayEvent.ElementType.CROSSHAIRS) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen != null) return;

        FontRenderer font = mc.fontRendererObj;
        String text = EnumChatFormatting.RED + "Taps:" + EnumChatFormatting.WHITE + (compressed().isEmpty() ? "" : " " + compressed());
        int x = event.resolution.getScaledWidth() / 2 - font.getStringWidth(text) / 2;
        int y = event.resolution.getScaledHeight() / 2 + 12 + (TemplateMode.isActive() ? 2 * (font.FONT_HEIGHT + 2) : 0);
        font.drawStringWithShadow(text, x, y, 0xFFFFFFFF);
    }
}
