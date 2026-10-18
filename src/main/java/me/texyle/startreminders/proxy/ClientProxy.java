package me.texyle.startreminders.proxy;

import net.minecraftforge.common.MinecraftForge;

import me.texyle.startreminders.input.KeyInputHandler;
import me.texyle.startreminders.lifecycle.WorldLifecycleHandler;
import me.texyle.startreminders.render.NearbyJumpRenderer;

public class ClientProxy extends CommonProxy{
	@Override
	public void registerRenders() {
		MinecraftForge.EVENT_BUS.register(new WorldLifecycleHandler());
		MinecraftForge.EVENT_BUS.register(new KeyInputHandler());
		MinecraftForge.EVENT_BUS.register(new NearbyJumpRenderer());
	}
}
