package me.texyle.startreminders.utils;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockPos;

public class DrawUtils {

    private static final int MAX_CHARS_PER_LINE = 60;

    public static void drawNametag(String str) {
        FontRenderer fontrenderer = Minecraft.getMinecraft().fontRendererObj;
        float f = 1.6F;
        float f1 = 0.016666668F * f;
        GlStateManager.pushMatrix();
        GL11.glNormal3f(0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(-Minecraft.getMinecraft().getRenderManager().playerViewY, 0.0F, 1.0F, 0.0F);
        GlStateManager.scale(-f1, -f1, f1);
        GlStateManager.disableLighting();
        GlStateManager.depthMask(false);
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldrenderer = tessellator.getWorldRenderer();
        int i = 0;

        int j = fontrenderer.getStringWidth(str) / 2;
        GlStateManager.disableTexture2D();
        worldrenderer.begin(7, DefaultVertexFormats.POSITION_COLOR);
        worldrenderer.pos(-j - 1, -1 + i, 0.0D).color(0.0F, 0.0F, 0.0F, 0.25F).endVertex();
        worldrenderer.pos(-j - 1, 8 + i, 0.0D).color(0.0F, 0.0F, 0.0F, 0.25F).endVertex();
        worldrenderer.pos(j + 1, 8 + i, 0.0D).color(0.0F, 0.0F, 0.0F, 0.25F).endVertex();
        worldrenderer.pos(j + 1, -1 + i, 0.0D).color(0.0F, 0.0F, 0.0F, 0.25F).endVertex();
        tessellator.draw();
        GlStateManager.enableTexture2D();
        fontrenderer.drawString(str, -fontrenderer.getStringWidth(str) / 2, i, 553648127);
        GlStateManager.depthMask(true);

        fontrenderer.drawString(str, -fontrenderer.getStringWidth(str) / 2, i, -1);

        GlStateManager.enableDepth();
        GlStateManager.enableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
    }

    public static void drawNametagAtCoords(String str, int blockX, float blockY, int blockZ, float partialTicks) {
        GlStateManager.alphaFunc(516, 0.1F);

        Entity viewer = Minecraft.getMinecraft().getRenderViewEntity();
        double viewerX = viewer.lastTickPosX + (viewer.posX - viewer.lastTickPosX) * partialTicks;
        double viewerY = viewer.lastTickPosY + (viewer.posY - viewer.lastTickPosY) * partialTicks;
        double viewerZ = viewer.lastTickPosZ + (viewer.posZ - viewer.lastTickPosZ) * partialTicks;

        double x = blockX + 0.5 - viewerX;
        double y = blockY - viewerY - viewer.getEyeHeight();
        double z = blockZ + 0.5 - viewerZ;

        double distSq = x * x + y * y + z * z;
        if (distSq > 144) {
            return;
        }

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        GlStateManager.translate(0, viewer.getEyeHeight(), 0);

        drawNametag(str);

        GlStateManager.popMatrix();
    }

    // Legacy helper kept for compatibility with BlockPos-based calls.
    public static void drawTextAtCoords(List<String> str, BlockPos loc, float partialTicks) {
        drawTextAtCoords(str, loc.getX(), loc.getY(), loc.getZ(), partialTicks);
    }

    // Allows float Y so we can stack multiple strategies cleanly.
    public static void drawTextAtCoords(List<String> str, int x, float yStart, int z, float partialTicks) {
        float lineSpacing = 0.24f;
        float y = yStart;

        // Render only the minimal view, without changing how data is stored.
        List<String> displayLines = buildDisplayLines(str);

        for (String s : displayLines) {
            if (s != null && s.length() > 0) {
                y -= lineSpacing;
                DrawUtils.drawNametagAtCoords(s, x, y, z, partialTicks);
            }
        }
    }

    /**
     * Builds the in-world view (wrapped at MAX_CHARS_PER_LINE):
     * - Line group 1: Position + "F:" + Facing + Setup (space-separated, smart-wrapped)
     * - Line group 2: Strategy (wrapped if needed)
     * - Line group 3: "Strafe:" + Strafe + "Turn:" + Turn (space-separated, smart-wrapped)
     *
     * This does NOT change how Reminder lines are stored, it only changes what is displayed.
     *
     * Supported stored formats:
     * - New (8): [Position, Facing, Setup, Strategy, Strafe, Turn, Author, Tips]
     * - Previous (6): [Setup, Strategy, Strafe, Turn, Author, Tips]
     * - Legacy (<=5): [Strategy, Strafe, Turn, Author, Tips] (no Setup)
     */
    private static List<String> buildDisplayLines(List<String> lines) {
        ArrayList<String> out = new ArrayList<String>();
        if (lines == null) {
            return out;
        }

        String position = "";
        String facing = "";
        String setup = "";
        String strategy = "";
        String strafe = "";
        String turn = "";

        int n = lines.size();

        if (n >= 8) {
            // New (8)
            position = safeLine(lines, 0);
            facing = safeLine(lines, 1);
            setup = safeLine(lines, 2);
            strategy = safeLine(lines, 3);
            strafe = safeLine(lines, 4);
            turn = safeLine(lines, 5);
        } else if (n >= 6) {
            // Previous (6)
            setup = safeLine(lines, 0);
            strategy = safeLine(lines, 1);
            strafe = safeLine(lines, 2);
            turn = safeLine(lines, 3);
        } else {
            // Legacy (<=5): [Strategy, Strafe, Turn, ...]
            strategy = safeLine(lines, 0);
            strafe = safeLine(lines, 1);
            turn = safeLine(lines, 2);
        }

        // Group 1: Position + F:Facing + Setup (smart wrapped by segments).
        ArrayList<String> g1Parts = new ArrayList<String>();
        addIfNotEmpty(g1Parts, position);
        if (!isEmpty(facing)) {
            addIfNotEmpty(g1Parts, "F: " + facing);
        }
        addIfNotEmpty(g1Parts, setup);

        out.addAll(wrapBySegments(g1Parts, MAX_CHARS_PER_LINE));

        // Group 2: Strategy (wrap if needed).
        out.addAll(wrapTextHard(strategy, MAX_CHARS_PER_LINE));

        // Group 3: Strafe/Turn (smart wrapped by segments).
        ArrayList<String> g3Parts = new ArrayList<String>();
        if (!isEmpty(strafe)) {
            g3Parts.add("Strafe:");
            g3Parts.add(strafe);
        }
        if (!isEmpty(turn)) {
            g3Parts.add("Turn:");
            g3Parts.add(turn);
        }

        out.addAll(wrapBySegments(g3Parts, MAX_CHARS_PER_LINE));

        // Remove any accidental empties.
        for (int i = out.size() - 1; i >= 0; i--) {
            if (out.get(i) == null || out.get(i).trim().isEmpty()) {
                out.remove(i);
            }
        }

        return out;
    }

    /**
     * Smart wrapping by "segments" (labels) separated by a single space.
     * If adding the next segment (including the space) would exceed maxLen,
     * it starts a new line.
     *
     * If a single segment exceeds maxLen, it is hard-split into chunks.
     */
    private static List<String> wrapBySegments(List<String> segments, int maxLen) {
        ArrayList<String> out = new ArrayList<String>();
        if (segments == null || segments.isEmpty()) {
            return out;
        }

        StringBuilder line = new StringBuilder();

        for (String raw : segments) {
            if (raw == null) continue;
            String seg = raw.trim();
            if (seg.isEmpty()) continue;

            // If the segment itself is too long, flush current line and hard-split the segment.
            if (seg.length() > maxLen) {
                if (line.length() > 0) {
                    out.add(line.toString());
                    line.setLength(0);
                }
                out.addAll(wrapTextHard(seg, maxLen));
                continue;
            }

            if (line.length() == 0) {
                line.append(seg);
                continue;
            }

            int wouldBeLen = line.length() + 1 + seg.length(); // +1 for the space
            if (wouldBeLen > maxLen) {
                out.add(line.toString());
                line.setLength(0);
                line.append(seg);
            } else {
                line.append(' ').append(seg);
            }
        }

        if (line.length() > 0) {
            out.add(line.toString());
        }

        return out;
    }

    /**
     * Hard wrap for a single text (used when a single segment is longer than maxLen,
     * or for Strategy which is one field).
     */
    private static List<String> wrapTextHard(String text, int maxLen) {
        ArrayList<String> out = new ArrayList<String>();
        if (text == null) {
            return out;
        }
        String t = text.trim();
        if (t.isEmpty()) {
            return out;
        }

        int i = 0;
        while (i < t.length()) {
            int end = Math.min(i + maxLen, t.length());
            out.add(t.substring(i, end));
            i = end;
        }
        return out;
    }

    private static void addIfNotEmpty(List<String> parts, String s) {
        if (parts == null) return;
        if (s == null) return;
        String t = s.trim();
        if (!t.isEmpty()) parts.add(t);
    }

    private static boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String safeLine(List<String> lines, int idx) {
        if (lines == null || idx < 0 || idx >= lines.size()) {
            return "";
        }
        String s = lines.get(idx);
        return s != null ? s.trim() : "";
    }
}