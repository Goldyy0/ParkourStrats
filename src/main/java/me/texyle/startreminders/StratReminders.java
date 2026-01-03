package me.texyle.startreminders;

import org.lwjgl.input.Keyboard;

import me.texyle.startreminders.gui.GuiHandler;
import me.texyle.startreminders.proxy.CommonProxy;
import me.texyle.startreminders.reminders.ReminderManager;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;

@Mod(modid=Reference.MOD_ID,name=Reference.NAME, version=Reference.VERSION)
public class StratReminders {

	@SidedProxy(serverSide = Reference.SERVER_PROXY_CLASS, clientSide = Reference.CLIENT_PROXY_CLASS)
	public static CommonProxy proxy;

	@Mod.Instance("sr")
	public static StratReminders instance;

	public static KeyBinding createKey = new KeyBinding("Create strategies", Keyboard.KEY_Y, "Parkour Strats");
	public static KeyBinding showKey = new KeyBinding("Show nearby strategies", Keyboard.KEY_Z, "Parkour Strats");
	public static KeyBinding toggleKey = new KeyBinding("Toggle nearby strategies", Keyboard.KEY_V, "Parkour Strats");
	public static KeyBinding editKey = new KeyBinding("Edit strategies", Keyboard.KEY_B, "Parkour Strats");

	// Step C: open hierarchy UI (Server -> Map -> Jump).
	public static KeyBinding hierarchyKey = new KeyBinding("Open hierarchy UI", Keyboard.KEY_N, "Parkour Strats");

	@Mod.EventHandler
	public void preInit(FMLPreInitializationEvent  event) {
		proxy.registerEntityRenders();
	}

	@Mod.EventHandler
	public void init(FMLInitializationEvent  event) {
		proxy.registerRenders();

		NetworkRegistry.INSTANCE.registerGuiHandler(this, new GuiHandler());

		ClientRegistry.registerKeyBinding(createKey);
		ClientRegistry.registerKeyBinding(showKey);
		ClientRegistry.registerKeyBinding(toggleKey);
		ClientRegistry.registerKeyBinding(editKey);
		ClientRegistry.registerKeyBinding(hierarchyKey);
	}

	@Mod.EventHandler
	public void postInit(FMLPostInitializationEvent  event) {
		ReminderManager.loadFromFile();
	}
}