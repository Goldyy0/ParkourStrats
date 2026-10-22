package me.texyle.startreminders.template;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import me.texyle.startreminders.data.Jump;

/** In template mode: the innermost section and the current jump, written under the crosshair. */
public class TemplateHud {

    private static final int SECTION_COLOR = 0xFFB59CF0;
    private static final int JUMP_COLOR = 0xFFFFFFFF;

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.CROSSHAIRS) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen != null || mc.thePlayer == null || !TemplateMode.isActive()) return;

        Jump jump = TemplateMode.current();
        if (jump == null) return;

        FontRenderer font = mc.fontRendererObj;
        ScaledResolution res = event.resolution;
        int cx = res.getScaledWidth() / 2;
        int y = res.getScaledHeight() / 2 + 12;

        int count = TemplateMode.map().getJumps().size();
        String position = (TemplateMode.currentIndex() + 1) + "/" + count;
        String section = TemplateMode.sectionName(TemplateMode.map(), TemplateMode.currentIndex());
        String line = section.isEmpty() ? position : position + "  " + section;
        font.drawStringWithShadow(line, cx - font.getStringWidth(line) / 2f, y, SECTION_COLOR);
        y += font.FONT_HEIGHT + 2;

        String name = jump.getId() != null ? jump.getId() : "";
        font.drawStringWithShadow(name, cx - font.getStringWidth(name) / 2f, y, JUMP_COLOR);
    }
}
