package me.texyle.startreminders.services;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;

import me.texyle.startreminders.data.Jump;
import me.texyle.startreminders.data.MapSection;
import me.texyle.startreminders.data.ParkourMap;
import me.texyle.startreminders.data.ServerProfile;
import me.texyle.startreminders.persistence.BuiltinContainers;
import me.texyle.startreminders.persistence.DataStoreRepository;
import me.texyle.startreminders.reminders.DefaultContextSelector;
import me.texyle.startreminders.reminders.Reminder;
import me.texyle.startreminders.reminders.SelectionState;
import me.texyle.startreminders.utils.IdRules;
import me.texyle.startreminders.utils.PlayerBlockPos;

/** Creates, finds, renames, removes and transfers jumps. Jump identity is name + coordinates. */
public final class JumpService {

	private JumpService() {}

	/**
	 * Finds a jump only if BOTH name and coords match.
	 * This is the new identity check for "reuse existing container".
	 */
	public static Jump findJumpByNameAndCoords(ParkourMap map, String id, int x, int y, int z) {
		DataStoreRepository.ensureLoaded();
		if (map == null || id == null) return null;
		if (map.getJumps() == null) map.setJumps(new ArrayList<Jump>());

		for (Jump j : map.getJumps()) {
			if (j == null) continue;

			if (!id.equals(j.getId())) continue;

			if (j.getX() == x && j.getY() == y && j.getZ() == z) {
				return j;
			}
		}

		return null;
	}

	/**
	 * Returns an existing jump if (name + coords) are identical, otherwise creates a new jump
	 * even if the name is the same (new container).
	 *
	 * This is the method your GUI should use instead of "find by name".
	 */
	public static Jump getOrCreateJumpByNameAndCoords(ParkourMap map, String id, int x, int y, int z) {
		DataStoreRepository.ensureLoaded();
		if (map == null) return null;
		if (!IdRules.isValidId(id)) return null;

		Jump existing = findJumpByNameAndCoords(map, id, x, y, z);
		if (existing != null) {
			return existing;
		}

		Jump j = new Jump();
		j.setShowJumpNameInWorld(true);
		j.setId(id);
		j.setX(x);
		j.setY(y);
		j.setZ(z);
		j.setReminders(new ArrayList<Reminder>());
		j.setActiveReminderIndex(-1);

		if (map.getJumps() == null) {
			map.setJumps(new ArrayList<Jump>());
		}
		map.getJumps().add(j);

		DataStoreRepository.save();
		return j;
	}

	/** Like {@link #getOrCreateJumpByNameAndCoords}, at the player's current block position. */
	public static Jump getOrCreateJumpAtPlayer(ParkourMap map, String id) {
		int[] pos = playerBlockPosOrZero();
		return getOrCreateJumpByNameAndCoords(map, id, pos[0], pos[1], pos[2]);
	}

	/** Like {@link #createJumpByNameAndCoordsAlwaysNew}, at the player's current block position. */
	public static Jump createJumpAtPlayer(ParkourMap map, String id) {
		int[] pos = playerBlockPosOrZero();
		return createJumpByNameAndCoordsAlwaysNew(map, id, pos[0], pos[1], pos[2]);
	}

	private static int[] playerBlockPosOrZero() {
		EntityPlayerSP p = Minecraft.getMinecraft().thePlayer;
		if (p == null) {
			return new int[] { 0, 0, 0 };
		}
		return new int[] { PlayerBlockPos.x(p), PlayerBlockPos.jumpY(p), PlayerBlockPos.z(p) };
	}

	/**
	 * Always creates a NEW jump entry, even if another jump with the same name and coords already exists.
	 * This is used to allow manual duplicates in the GUI (same Jump name allowed, same coords allowed).
	 */
	public static Jump createJumpByNameAndCoordsAlwaysNew(ParkourMap map, String id, int x, int y, int z) {
		DataStoreRepository.ensureLoaded();
		if (map == null) return null;
		if (!IdRules.isValidId(id)) return null;

		Jump j = new Jump();
		j.setShowJumpNameInWorld(true);
		j.setId(id);
		j.setX(x);
		j.setY(y);
		j.setZ(z);
		j.setReminders(new ArrayList<Reminder>());
		j.setActiveReminderIndex(-1);

		if (map.getJumps() == null) {
			map.setJumps(new ArrayList<Jump>());
		}
		map.getJumps().add(j);

		DataStoreRepository.save();
		return j;
	}

	public static boolean removeJumpFromRestoredStrats(Jump legacyJump) {
		DataStoreRepository.ensureLoaded();

		if (legacyJump == null) {
			return false;
		}

		ParkourMap restoredMap = BuiltinContainers.getRestoredMap();
		if (restoredMap == null || restoredMap.getJumps() == null) {
			return false;
		}

		boolean removed = restoredMap.getJumps().remove(legacyJump);

		// Fallback: if the object reference does not match (e.g., deserialized copy), remove by coords.
		if (!removed) {
			int lx = legacyJump.getX();
			int ly = legacyJump.getY();
			int lz = legacyJump.getZ();

			for (int i = 0; i < restoredMap.getJumps().size(); i++) {
				Jump j = restoredMap.getJumps().get(i);
				if (j == null) continue;

				if (j.getX() == lx && j.getY() == ly && j.getZ() == lz) {
					restoredMap.getJumps().remove(i);
					removed = true;
					break;
				}
			}
		}

		if (!removed) {
			return false;
		}

		// Clear selection if it pointed to the removed legacy jump.
		SelectionState.clearJumpIfSelected(legacyJump);

		DataStoreRepository.save();
		DefaultContextSelector.autoSelectDefaultContext();
		return true;
	}

	public static ArrayList<Jump> getJumps(ParkourMap map) {
		DataStoreRepository.ensureLoaded();
		if (map == null) return new ArrayList<Jump>();
		if (map.getJumps() == null) map.setJumps(new ArrayList<Jump>());
		return map.getJumps();
	}

	public static void renameJump(ParkourMap map, Jump jump, String newName) {
		DataStoreRepository.ensureLoaded();
		if (map == null || jump == null) return;

		String t = (newName != null) ? newName.trim() : "";
		if (t.isEmpty()) return;

		jump.setId(t);

		DataStoreRepository.save();
	}

	public static boolean removeJump(ParkourMap map, Jump jump) {
		DataStoreRepository.ensureLoaded();
		if (map == null || jump == null) return false;

		ArrayList<Jump> jumps = map.getJumps();
		if (jumps == null || jumps.isEmpty()) return false;

		int idx = indexOfJumpByReference(jumps, jump);
		if (idx < 0) return false;

		jumps.remove(idx);

		int max = map.getMaxSectionLevelsAllowed();
		if (max > ParkourMap.MAX_SECTION_LEVELS) max = ParkourMap.MAX_SECTION_LEVELS;

		if (max > 0) {
			SectionLayoutService.shiftAllSectionRangesAfterRemove(map, idx, max);
		}

		SelectionState.clearJumpIfSelected(jump);

		DataStoreRepository.save();
		return true;
	}

	public static boolean transferJump(
			ParkourMap fromMap,
			Jump jump,
			ServerProfile toServer,
			ParkourMap toMap,
			int targetLevelOneBased,
			MapSection targetSection
	) {
		DataStoreRepository.ensureLoaded();

		if (fromMap == null || toMap == null || jump == null) return false;
		if (toMap == fromMap) return false;

		// NOTE: Global IS allowed as a transfer target.
		// (Do not block it here.)

		if (fromMap.getJumps() == null) fromMap.setJumps(new ArrayList<Jump>());
		if (toMap.getJumps() == null) toMap.setJumps(new ArrayList<Jump>());

		// ------------------------------------------------------------
		// 1) Remove from source (like removeJump) + shift section ranges
		// ------------------------------------------------------------
		ArrayList<Jump> fromJumps = fromMap.getJumps();
		int removedIndex = indexOfJumpByReference(fromJumps, jump);
		boolean removed = false;

		if (removedIndex >= 0) {
			fromJumps.remove(removedIndex);
			removed = true;
		} else {
			// Fallback: remove by coords+id if reference does not match
			for (int i = 0; i < fromJumps.size(); i++) {
				Jump j = fromJumps.get(i);
				if (j == null) continue;

				boolean sameId = safeEq(j.getId(), jump.getId());
				boolean sameCoords = (j.getX() == jump.getX() && j.getY() == jump.getY() && j.getZ() == jump.getZ());

				if (sameId && sameCoords) {
					fromJumps.remove(i);
					removedIndex = i;
					removed = true;
					break;
				}
			}
		}

		if (!removed) return false;

		// Apply section shift on source map (exactly like removeJump does)
		int fromMax = 0;
		try { fromMax = fromMap.getMaxSectionLevelsAllowed(); } catch (Throwable ignored) {}
		if (fromMax > ParkourMap.MAX_SECTION_LEVELS) fromMax = ParkourMap.MAX_SECTION_LEVELS;

		if (fromMax > 0 && removedIndex >= 0) {
			SectionLayoutService.shiftAllSectionRangesAfterRemove(fromMap, removedIndex, fromMax);
		}

		// If selection pointed to this jump, clear it.
		SelectionState.clearJumpIfSelected(jump);

		// ------------------------------------------------------------
		// 2) Add to target
		//    If section selected -> behave like InsertJump at end of section
		// ------------------------------------------------------------
		boolean insertedViaSection = false;

		if (targetSection != null && targetLevelOneBased > 0) {
			int toMax = 0;
			try { toMax = toMap.getMaxSectionLevelsAllowed(); } catch (Throwable ignored) {}
			if (toMax > ParkourMap.MAX_SECTION_LEVELS) toMax = ParkourMap.MAX_SECTION_LEVELS;

			if (toMax > 0 && targetLevelOneBased >= 1 && targetLevelOneBased <= toMax) {
				SectionLayoutService.insertJumpAtEndOfSection(toMap, targetLevelOneBased, targetSection, jump);
				insertedViaSection = true;
			}
		}

		if (!insertedViaSection) {
			toMap.getJumps().add(jump);
		}

		// Update selection to moved item
		ServerProfile owner = ServerService.findOwningServerForMap(toMap);
		SelectionState.select((owner != null) ? owner : toServer, toMap, jump);

		DataStoreRepository.save();
		return true;
	}

	private static boolean safeEq(String a, String b) {
		if (a == null && b == null) return true;
		if (a == null || b == null) return false;
		return a.equals(b);
	}

	public static int indexOfJumpByReference(java.util.List<Jump> list, Jump target) {
		if (list == null || target == null) return -1;
		for (int i = 0; i < list.size(); i++) {
			if (list.get(i) == target) {
				return i;
			}
		}
		return -1;
	}

}
