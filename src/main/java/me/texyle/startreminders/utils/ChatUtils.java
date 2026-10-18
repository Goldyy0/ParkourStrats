package me.texyle.startreminders.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

/** Chat output helpers and the mod's chat prefixes. */
public final class ChatUtils {

	/** Prefixes for chat lines the mod prints; the colour after the tag shows severity. */
	public static final String INFO_PREFIX = EnumChatFormatting.DARK_AQUA + "[ParkourStrats] " + EnumChatFormatting.AQUA;
	public static final String WARNING_PREFIX = EnumChatFormatting.DARK_AQUA + "[ParkourStrats] " + EnumChatFormatting.YELLOW;
	public static final String ERROR_PREFIX = EnumChatFormatting.DARK_AQUA + "[ParkourStrats] " + EnumChatFormatting.RED;

	private ChatUtils() {}

	public static void sendChat(Minecraft mc, String msg) {
		if (mc == null || mc.thePlayer == null || msg == null) {
			return;
		}
		mc.thePlayer.addChatMessage(new ChatComponentText(msg));
	}

	public static void info(String msg) {
		sendChat(Minecraft.getMinecraft(), INFO_PREFIX + msg);
	}

	public static void error(String msg) {
		sendChat(Minecraft.getMinecraft(), ERROR_PREFIX + msg);
	}

	public static void warning(String msg) {
		sendChat(Minecraft.getMinecraft(), WARNING_PREFIX + msg);
	}
}
