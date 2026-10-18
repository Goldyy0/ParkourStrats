package me.texyle.startreminders.reminders;

import java.util.ArrayList;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.Vec3;

import me.texyle.startreminders.data.DataStore;
import me.texyle.startreminders.data.Jump;
import me.texyle.startreminders.data.ParkourMap;
import me.texyle.startreminders.data.ServerProfile;
import me.texyle.startreminders.persistence.DataStoreRepository;

/** Finds the jump the player means: the one in the crosshair or the nearest one, and selects it. */
public final class JumpPicker {

	private JumpPicker() {}

	public static Jump selectJumpInCrosshair(double maxRangeBlocks, double maxPerpDistBlocks) {
		DataStoreRepository.ensureLoaded();

		Minecraft mc = Minecraft.getMinecraft();
		if (mc == null || mc.thePlayer == null) {
			return null;
		}

		EntityPlayerSP player = mc.thePlayer;

		JumpPickResult best = null;

		JumpPickResult g = findBestJumpInCrosshairInStore(DataStoreRepository.getGlobalStore(), player, maxRangeBlocks, maxPerpDistBlocks);
		if (g != null && g.jump != null) {
			best = g;
		}

		JumpPickResult r = findBestJumpInCrosshairInStore(DataStoreRepository.getRestoredStore(), player, maxRangeBlocks, maxPerpDistBlocks);
		if (r != null && r.jump != null) {
			if (best == null || isCrosshairPickBetter(r, best)) {
				best = r;
			}
		}

		JumpPickResult s = findBestJumpInCrosshairInStore(DataStoreRepository.getSharedStore(), player, maxRangeBlocks, maxPerpDistBlocks);
		if (s != null && s.jump != null) {
			if (best == null || isCrosshairPickBetter(s, best)) {
				best = s;
			}
		}

		if (best == null || best.jump == null) {
			return null;
		}

		SelectionState.select(best.server, best.map, best.jump);

		if (SelectionState.getSelectedJump().getReminders() == null) {
			SelectionState.getSelectedJump().setReminders(new ArrayList<Reminder>());
		}

		return SelectionState.getSelectedJump();
	}

	private static boolean isCrosshairPickBetter(JumpPickResult a, JumpPickResult b) {
		// Prefer smaller perpendicular distance to view ray,
		// then prefer closer along-ray distance (t).
		if (a == null || b == null) return false;

		if (a.perpDistSq < b.perpDistSq) return true;
		if (a.perpDistSq > b.perpDistSq) return false;

		return a.rayT < b.rayT;
	}

	private static JumpPickResult findBestJumpInCrosshairInStore(
			DataStore store,
			EntityPlayerSP player,
			double maxRangeBlocks,
			double maxPerpDistBlocks
	) {
		if (store == null || store.getServers() == null || player == null) {
			return null;
		}

		Vec3 eye = player.getPositionEyes(1.0F);
		Vec3 look = player.getLookVec();
		if (eye == null || look == null) {
			return null;
		}

		double lx = look.xCoord;
		double ly = look.yCoord;
		double lz = look.zCoord;

		// Normalize look vector (defensive)
		double len = Math.sqrt(lx * lx + ly * ly + lz * lz);
		if (len <= 0.000001) {
			return null;
		}
		lx /= len;
		ly /= len;
		lz /= len;

		double maxRange = Math.max(0.0, maxRangeBlocks);
		double maxPerp = Math.max(0.0, maxPerpDistBlocks);
		double maxPerpSq = maxPerp * maxPerp;

		JumpPickResult best = null;

		for (ServerProfile s : store.getServers()) {
			if (s == null || s.getMaps() == null) continue;

			for (ParkourMap m : s.getMaps()) {
				if (m == null || m.getJumps() == null) continue;

				for (Jump j : m.getJumps()) {
					if (j == null) continue;

					int x = j.getX();
					int y = j.getY();
					int z = j.getZ();

					if (x == 0 && y == 0 && z == 0) {
						continue;
					}

					// Only consider jumps that actually have strategies
					if (j.getReminders() == null || j.getReminders().isEmpty()) {
						continue;
					}

					// Use center of block for nicer aiming
					double jx = x + 0.5;
					double jy = y + 0.5;
					double jz = z + 0.5;

					double tx = jx - eye.xCoord;
					double ty = jy - eye.yCoord;
					double tz = jz - eye.zCoord;

					// Projection length along the view ray
					double t = (tx * lx) + (ty * ly) + (tz * lz);

					if (t <= 0.0 || t > maxRange) {
						continue;
					}

					// Closest point on ray to jump point
					double cx = eye.xCoord + (lx * t);
					double cy = eye.yCoord + (ly * t);
					double cz = eye.zCoord + (lz * t);

					double dx = jx - cx;
					double dy = jy - cy;
					double dz = jz - cz;

					double perpSq = (dx * dx) + (dy * dy) + (dz * dz);
					if (perpSq > maxPerpSq) {
						continue;
					}

					JumpPickResult cand = new JumpPickResult(s, m, j, perpSq, t);
					if (best == null || isCrosshairPickBetter(cand, best)) {
						best = cand;
					}
				}
			}
		}

		return best;
	}

	public static Jump selectNearestJumpToPlayer() {
		DataStoreRepository.ensureLoaded();

		Minecraft mc = Minecraft.getMinecraft();
		if (mc == null || mc.thePlayer == null) {
			return null;
		}

		EntityPlayerSP player = mc.thePlayer;

		Jump bestJump = null;
		ParkourMap bestMap = null;
		ServerProfile bestServer = null;

		double bestDistSq = Double.MAX_VALUE;

		// Search: Global + Restored + Shared (in that order)
		JumpPickResult globalPick = findNearestJumpInStore(DataStoreRepository.getGlobalStore(), player);
		if (globalPick != null && globalPick.jump != null) {
			bestJump = globalPick.jump;
			bestMap = globalPick.map;
			bestServer = globalPick.server;
			bestDistSq = globalPick.distSq;
		}

		JumpPickResult restoredPick = findNearestJumpInStore(DataStoreRepository.getRestoredStore(), player);
		if (restoredPick != null && restoredPick.jump != null && restoredPick.distSq < bestDistSq) {
			bestJump = restoredPick.jump;
			bestMap = restoredPick.map;
			bestServer = restoredPick.server;
			bestDistSq = restoredPick.distSq;
		}

		JumpPickResult sharedPick = findNearestJumpInStore(DataStoreRepository.getSharedStore(), player);
		if (sharedPick != null && sharedPick.jump != null && sharedPick.distSq < bestDistSq) {
			bestJump = sharedPick.jump;
			bestMap = sharedPick.map;
			bestServer = sharedPick.server;
			bestDistSq = sharedPick.distSq;
		}

		if (bestJump == null) {
			return null;
		}

		SelectionState.select(bestServer, bestMap, bestJump);

		if (SelectionState.getSelectedJump().getReminders() == null) {
			SelectionState.getSelectedJump().setReminders(new ArrayList<Reminder>());
		}

		return SelectionState.getSelectedJump();
	}

	private static class JumpPickResult {
		final ServerProfile server;
		final ParkourMap map;
		final Jump jump;

		// Used by nearest selection
		final double distSq;

		// Used by crosshair selection
		final double perpDistSq;
		final double rayT;

		// Nearest constructor
		JumpPickResult(ServerProfile server, ParkourMap map, Jump jump, double distSq) {
			this.server = server;
			this.map = map;
			this.jump = jump;
			this.distSq = distSq;
			this.perpDistSq = Double.MAX_VALUE;
			this.rayT = Double.MAX_VALUE;
		}

		// Crosshair constructor
		JumpPickResult(ServerProfile server, ParkourMap map, Jump jump, double perpDistSq, double rayT) {
			this.server = server;
			this.map = map;
			this.jump = jump;
			this.distSq = Double.MAX_VALUE;
			this.perpDistSq = perpDistSq;
			this.rayT = rayT;
		}
	}

	private static JumpPickResult findNearestJumpInStore(DataStore store, EntityPlayerSP player) {
		if (store == null || store.getServers() == null || player == null) {
			return null;
		}

		Jump bestJump = null;
		ParkourMap bestMap = null;
		ServerProfile bestServer = null;
		double bestDistSq = Double.MAX_VALUE;

		for (ServerProfile s : store.getServers()) {
			if (s == null || s.getMaps() == null) continue;

			for (ParkourMap m : s.getMaps()) {
				if (m == null || m.getJumps() == null) continue;

				for (Jump j : m.getJumps()) {
					if (j == null) continue;

					int x = j.getX();
					int y = j.getY();
					int z = j.getZ();

					if (x == 0 && y == 0 && z == 0) {
						continue;
					}

					double dx = player.posX - x;
					double dy = player.posY - y;
					double dz = player.posZ - z;
					double distSq = (dx * dx) + (dy * dy) + (dz * dz);

					if (distSq < bestDistSq) {
						bestDistSq = distSq;
						bestJump = j;
						bestMap = m;
						bestServer = s;
					}
				}
			}
		}

		if (bestJump == null) {
			return null;
		}

		return new JumpPickResult(bestServer, bestMap, bestJump, bestDistSq);
	}

}
