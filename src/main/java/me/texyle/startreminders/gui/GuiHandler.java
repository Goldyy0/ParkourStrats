package me.texyle.startreminders.gui;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;

public class GuiHandler implements IGuiHandler {

	@Override
	public Object getServerGuiElement(int id, EntityPlayer var2, World var3, int var4, int var5, int var6) {
		// This mod uses client-side screens via openGui; keep existing behavior.
		if (id == 1) {
			return new GuiCreateJumpContext();
		}

		if (id == 2) {
			return new GuiEditReminders();
		}

		return null;
	}

	@Override
	public Object getClientGuiElement(int id, EntityPlayer var2, World var3, int var4, int var5, int var6) {
		if (id == 1) {
			return new GuiCreateJumpContext();
		}

		if (id == 2) {
			return new GuiEditReminders();
		}

		return null;
	}
}