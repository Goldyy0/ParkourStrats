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
        if (str == null) {
            return;
        }

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
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.depthMask(true);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        GlStateManager.popMatrix();
    }

    public static void drawNametagAtCoords(String str, int blockX, float blockY, int blockZ, float partialTicks) {
        if (str == null) {
            return;
        }

        GlStateManager.alphaFunc(516, 0.1F);

        Entity viewer = Minecraft.getMinecraft().getRenderViewEntity();
        if (viewer == null) {
            return;
        }

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
        if (loc == null) {
            return;
        }
        drawTextAtCoords(str, loc.getX(), loc.getY(), loc.getZ(), partialTicks);
    }

    // Existing signature stays (no jump name header, no color)
    public static void drawTextAtCoords(List<String> str, int x, float yStart, int z, float partialTicks) {
        drawTextAtCoordsInternal(str, null, false, null, x, yStart, z, partialTicks);
    }

    // Existing signature stays (header, no color)
    public static void drawTextAtCoords(List<String> str, String headerLine, boolean showHeader,
                                        int x, float yStart, int z, float partialTicks) {
        drawTextAtCoordsInternal(str, headerLine, showHeader, null, x, yStart, z, partialTicks);
    }

    // NEW: optional text color prefix for non-header lines
    public static void drawTextAtCoords(List<String> str, String headerLine, boolean showHeader, String textColorPrefix,
                                        int x, float yStart, int z, float partialTicks) {
        drawTextAtCoordsInternal(str, headerLine, showHeader, textColorPrefix, x, yStart, z, partialTicks);
    }

    private static void drawTextAtCoordsInternal(List<String> str, String headerLine, boolean showHeader,
                                                 String textColorPrefix,
                                                 int x, float yStart, int z, float partialTicks) {
        float lineSpacing = 0.24f;
        float y = yStart;

        List<String> displayLines = buildDisplayLines(str);

        if (showHeader && headerLine != null && headerLine.trim().length() > 0) {
            ArrayList<String> withHeader = new ArrayList<String>(displayLines.size() + 1);
            withHeader.add(headerLine);
            withHeader.addAll(displayLines);
            displayLines = withHeader;
        }

        for (int i = 0; i < displayLines.size(); i++) {
            String s = displayLines.get(i);
            if (s == null || s.length() == 0) {
                continue;
            }

            boolean isHeaderLine = showHeader && headerLine != null && i == 0;

            String out = s;
            if (!isHeaderLine && textColorPrefix != null && !textColorPrefix.isEmpty()) {
                out = textColorPrefix + out;
            }

            y -= lineSpacing;
            drawNametagAtCoords(out, x, y, z, partialTicks);
        }
    }

    /**
     * Builds the in-world view (wrapped at MAX_CHARS_PER_LINE):
     * - Line 1: Position + "F:" + Facing + Setup
     * - Line 2: Strategy
     * - Line 3: Strafe
     * - Line 4: Turn
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
            position = safeLine(lines, 0);
            facing = safeLine(lines, 1);
            setup = safeLine(lines, 2);
            strategy = safeLine(lines, 3);
            strafe = safeLine(lines, 4);
            turn = safeLine(lines, 5);
        } else if (n >= 6) {
            setup = safeLine(lines, 0);
            strategy = safeLine(lines, 1);
            strafe = safeLine(lines, 2);
            turn = safeLine(lines, 3);
        } else {
            strategy = safeLine(lines, 0);
            strafe = safeLine(lines, 1);
            turn = safeLine(lines, 2);
        }

        ArrayList<String> g1 = new ArrayList<String>();
        addIfNotEmpty(g1, position);
        if (!isEmpty(facing)) addIfNotEmpty(g1, "F: " + facing);
        addIfNotEmpty(g1, setup);
        out.addAll(wrapBySegments(g1, MAX_CHARS_PER_LINE));

        out.addAll(wrapTextHard(strategy, MAX_CHARS_PER_LINE));

        ArrayList<String> g3 = new ArrayList<String>();
        if (!isEmpty(strafe)) {
            g3.add("Strafe:");
            g3.add(strafe);
        }
        out.addAll(wrapBySegments(g3, MAX_CHARS_PER_LINE));

        ArrayList<String> g4 = new ArrayList<String>();
        if (!isEmpty(turn)) {
            g4.add("Turn:");
            g4.add(turn);
        }
        out.addAll(wrapBySegments(g4, MAX_CHARS_PER_LINE));

        return out;
    }

    private static List<String> wrapBySegments(List<String> segments, int maxLen) {
        ArrayList<String> out = new ArrayList<String>();
        if (segments == null || segments.isEmpty()) return out;

        StringBuilder line = new StringBuilder();

        for (String raw : segments) {
            if (raw == null) continue;
            String seg = raw.trim();
            if (seg.isEmpty()) continue;

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
            } else if (line.length() + 1 + seg.length() > maxLen) {
                out.add(line.toString());
                line.setLength(0);
                line.append(seg);
            } else {
                line.append(' ').append(seg);
            }
        }

        if (line.length() > 0) out.add(line.toString());
        return out;
    }

    private static List<String> wrapTextHard(String text, int maxLen) {
        ArrayList<String> out = new ArrayList<String>();
        if (text == null) return out;

        String t = text.trim();
        if (t.isEmpty()) return out;

        for (int i = 0; i < t.length(); i += maxLen) {
            out.add(t.substring(i, Math.min(i + maxLen, t.length())));
        }
        return out;
    }

    private static void addIfNotEmpty(List<String> parts, String s) {
        if (parts == null || s == null) return;
        String t = s.trim();
        if (!t.isEmpty()) parts.add(t);
    }

    private static boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String safeLine(List<String> lines, int idx) {
        if (lines == null || idx < 0 || idx >= lines.size()) return "";
        String s = lines.get(idx);
        return s != null ? s.trim() : "";
    }
}
