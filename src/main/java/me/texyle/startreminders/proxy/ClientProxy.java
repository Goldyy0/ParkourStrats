package me.texyle.startreminders.proxy;

import me.texyle.startreminders.reminders.ReminderManager;
import net.minecraftforge.common.MinecraftForge;

public class ClientProxy extends CommonProxy{
	@Override
	public void registerRenders() {
		MinecraftForge.EVENT_BUS.register(new ReminderManager());
	}
}
