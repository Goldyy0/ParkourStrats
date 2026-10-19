package me.texyle.startreminders.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import me.texyle.startreminders.StratReminders;
import me.texyle.startreminders.reminders.UserPreferences;
import me.texyle.startreminders.data.DataStore;
import me.texyle.startreminders.data.Jump;
import me.texyle.startreminders.data.ParkourMap;
import me.texyle.startreminders.data.ServerProfile;
import me.texyle.startreminders.persistence.BuiltinContainers;
import me.texyle.startreminders.persistence.DataStoreRepository;
import me.texyle.startreminders.reminders.Reminder;
import me.texyle.startreminders.reminders.UserPreferences;
import me.texyle.startreminders.services.StrategyService;
import me.texyle.startreminders.utils.DrawUtils;

/** Draws the strategies of nearby jumps in the world while the show key is held (HOLD) or toggled on (TOGGLE). */
public class NearbyJumpRenderer {

	private static boolean toggled = false;

	/** The show key went down: flips the display in TOGGLE mode (in HOLD mode the key state is read directly). */
	public static void onShowKeyPressed() {
		if (UserPreferences.isShowKeyToggle()) {
			toggled = !toggled;
		}
	}

	private static boolean shouldShow() {
		if (UserPreferences.isShowKeyToggle()) {
			return toggled;
		}
		toggled = false;
		return StratReminders.showKey.isKeyDown();
	}

	// Nearby rendering radius (blocks)
	private static final double NEARBY_RADIUS_BLOCKS = 24.0D;

	private static final double NEARBY_RADIUS_SQ = NEARBY_RADIUS_BLOCKS * NEARBY_RADIUS_BLOCKS;

	@SubscribeEvent
	public void onWorldRender(RenderWorldLastEvent event) {
		DataStoreRepository.ensureLoaded();

		if (!shouldShow()) {
			return;
		}

		Minecraft mc = Minecraft.getMinecraft();
		if (mc == null || mc.thePlayer == null) {
			return;
		}

		EntityPlayerSP player = mc.thePlayer;

		// Render all loaded jumps (Global + Restored + Shared) that are nearby.
		renderNearbyJumps(player, event);
	}

	private static void renderNearbyJumps(EntityPlayerSP player, RenderWorldLastEvent event) {
		if (player == null) {
			return;
		}

		// 1) Global
		renderNearbyJumpsFromStore(DataStoreRepository.getGlobalStore(), player, event);

		// 2) RestoredStrats
		renderNearbyJumpsFromStore(DataStoreRepository.getRestoredStore(), player, event);

		// 3) Shared
		renderNearbyJumpsFromStore(DataStoreRepository.getSharedStore(), player, event);
	}

	private static void renderNearbyJumpsFromStore(DataStore store, EntityPlayerSP player, RenderWorldLastEvent event) {
		if (store == null || store.getServers() == null) {
			return;
		}

		for (ServerProfile s : store.getServers()) {
			if (s == null || s.getMaps() == null) continue;

			for (ParkourMap m : s.getMaps()) {
				if (m == null || m.getJumps() == null) continue;

				for (Jump j : m.getJumps()) {
					if (j == null) continue;

					int x = j.getX();
					int y = j.getY();
					int z = j.getZ();

					// Skip unset coords
					if (x == 0 && y == 0 && z == 0) {
						continue;
					}

					// Nearby filter
					double dx = player.posX - x;
					double dy = player.posY - y;
					double dz = player.posZ - z;
					double distSq = (dx * dx) + (dy * dy) + (dz * dz);

					if (distSq > NEARBY_RADIUS_SQ) {
						continue;
					}

					Reminder active = StrategyService.getActiveReminderOrFallback(j);
					if (active == null || active.lines == null || active.lines.isEmpty()) {
						continue;
					}

					String jumpName = (j.getId() != null) ? j.getId().trim() : "";
					boolean isRestoredStratsContext =
							(s != null && BuiltinContainers.RESTORED_SERVER_ID.equals(s.getId())) ||
									(m != null && BuiltinContainers.RESTORED_MAP_ID.equals(m.getId()));

					boolean showName = !isRestoredStratsContext && UserPreferences.isGlobalShowJumpNameEnabled();

					String header = null;
					if (showName && jumpName.length() > 0) {
						String jumpColor = UserPreferences.getGlobalInWorldJumpNameColor();
						header = jumpColor + jumpName + EnumChatFormatting.RESET;
					}

					String textColor = UserPreferences.getGlobalInWorldTextColor();

					DrawUtils.drawTextAtCoords(active.lines, header, showName, textColor, x, y, z, event.partialTicks);
				}
			}
		}
	}

}
