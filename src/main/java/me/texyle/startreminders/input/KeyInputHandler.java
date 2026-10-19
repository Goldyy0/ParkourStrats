package me.texyle.startreminders.input;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;

import me.texyle.startreminders.StratReminders;
import me.texyle.startreminders.render.NearbyJumpRenderer;
import me.texyle.startreminders.services.StrategyService;
import me.texyle.startreminders.ui.GuiHub;
import me.texyle.startreminders.utils.ChatUtils;

/** Reacts to the mod's key bindings. */
public class KeyInputHandler {

	@SubscribeEvent
	public void onKeyPress(InputEvent.KeyInputEvent event) {
		Minecraft minecraft = Minecraft.getMinecraft();
		EntityPlayerSP player = minecraft.thePlayer;
		WorldClient world = minecraft.theWorld;

		if (player == null || world == null) {
			return;
		}

		if (StratReminders.hierarchyKey.isKeyDown()) {
			Minecraft.getMinecraft().displayGuiScreen(new GuiHub(null));
			return;
		}

		if (StratReminders.createKey.isKeyDown()) {
			player.openGui(StratReminders.instance, 1, world,
					(int) player.posX, (int) (player.posY + 0.4), (int) player.posZ);
		} else if (StratReminders.editKey.isKeyDown()) {
			// Safety: if the mod has no strategies at all, do not open the edit GUI.
			if (!StrategyService.hasAnyStrategies()) {
				ChatUtils.sendChat(Minecraft.getMinecraft(),
						ChatUtils.WARNING_PREFIX + "No available strategies were found.");
				return;
			}

			player.openGui(StratReminders.instance, 2, world,
					(int) player.posX, (int) player.posY, (int) player.posZ);
		}
		else if (StratReminders.showKey.isKeyDown()) {
			NearbyJumpRenderer.onShowKeyPressed();
		}
	}
}
