package me.texyle.startreminders.proxy;

import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;

import me.texyle.startreminders.input.KeyInputHandler;
import me.texyle.startreminders.input.TapRecordCommand;
import me.texyle.startreminders.input.TapRecorder;
import me.texyle.startreminders.lifecycle.WorldLifecycleHandler;
import me.texyle.startreminders.render.NearbyJumpRenderer;
import me.texyle.startreminders.template.TemplateHud;

public class ClientProxy extends CommonProxy{
	@Override
	public void registerRenders() {
		MinecraftForge.EVENT_BUS.register(new WorldLifecycleHandler());
		MinecraftForge.EVENT_BUS.register(new KeyInputHandler());
		MinecraftForge.EVENT_BUS.register(new NearbyJumpRenderer());
		MinecraftForge.EVENT_BUS.register(new TapRecorder());
		MinecraftForge.EVENT_BUS.register(new TemplateHud());
		ClientCommandHandler.instance.registerCommand(new TapRecordCommand());
	}
}
