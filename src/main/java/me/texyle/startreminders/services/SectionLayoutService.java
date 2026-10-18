package me.texyle.startreminders.services;

import java.util.ArrayList;

import me.texyle.startreminders.data.Jump;
import me.texyle.startreminders.data.MapSection;
import me.texyle.startreminders.data.ParkourMap;
import me.texyle.startreminders.persistence.DataStoreRepository;

/** Keeps section ranges consistent when jumps are inserted, removed or moved. */
public final class SectionLayoutService {

	private SectionLayoutService() {}

	// Treat a section as "inactive" if it does not have a valid range.
	// Inactive sections remain stored, but the GUI should not render them.
	private static boolean isSectionRangeActive(MapSection s, int jumpsSize) {
		if (s == null) return false;
		int a = s.getStartJumpIndex();
		int b = s.getEndJumpIndex();

		if (a < 0 || b < 0) return false;
		if (a > b) return false;
		if (jumpsSize <= 0) return false;
		if (a >= jumpsSize || b >= jumpsSize) return false;

		return true;
	}

	private static void deactivateSectionRange(MapSection s) {
		if (s == null) return;
		s.setStartJumpIndex(-1);
		s.setEndJumpIndex(-1);
	}

	public static void insertJumpAtEndOfSection(ParkourMap map, int levelOneBased, MapSection targetSection, Jump jump) {
		if (map == null || targetSection == null || jump == null) {
			return;
		}

		int max = map.getMaxSectionLevelsAllowed();
		if (max <= 0) {
			return;
		}
		if (levelOneBased < 1 || levelOneBased > max) {
			return;
		}

		ArrayList<Jump> jumps = map.getJumps();
		if (jumps == null) {
			return;
		}

		int oldIndex = JumpService.indexOfJumpByReference(jumps, jump);

		// Remove existing instance (if present) and shift section indices.
		if (oldIndex >= 0) {
			jumps.remove(oldIndex);
			shiftAllSectionRangesAfterRemove(map, oldIndex, max);
		}

		int sizeAfterRemove = jumps.size();
		boolean active = isSectionRangeActive(targetSection, sizeAfterRemove);

		// Capture the target end BEFORE insertion, after potential removal-shifts.
		// This is the boundary we want to "stick to" for any other sections that ended here.
		int oldTargetEnd = -1;
		if (active) {
			oldTargetEnd = targetSection.getEndJumpIndex();
		}

		int insertPos;
		if (!active) {
			// Inactive section -> append to end
			insertPos = jumps.size();
		} else {
			insertPos = targetSection.getEndJumpIndex() + 1;
			if (insertPos < 0) insertPos = 0;
			if (insertPos > jumps.size()) insertPos = jumps.size();
		}

		// True only for the "insert at end of the section" scenario (not just any insert).
		boolean isInsertAtEndOfActiveSection = active && (oldTargetEnd >= 0) && (insertPos == oldTargetEnd + 1);

		jumps.add(insertPos, jump);

		// Shift indices for ALL sections in ALL levels due to insertion.
		shiftAllSectionRangesAfterInsert(map, insertPos, max);

		if (!active) {
			// Activate target section.
			targetSection.setStartJumpIndex(insertPos);
			targetSection.setEndJumpIndex(insertPos);
		} else {
			if (isInsertAtEndOfActiveSection) {
				// NEW RULE:
				// If we inserted at the end of this section, extend ALL active sections (any level)
				// that ended exactly at the same old end boundary.
				extendAllSectionEndsThatWereAt(map, oldTargetEnd, max);

				// NOTE: targetSection is included in the extension above,
				// so we do NOT manually do targetSection.setEndJumpIndex(end+1) here.
			} else {
				// Insert happened inside the section (or clamped away from exact end) -> only target should extend.
				targetSection.setEndJumpIndex(targetSection.getEndJumpIndex() + 1);
			}
		}

		DataStoreRepository.save();
	}

	/**
	 * Extends endJumpIndex by +1 for all ACTIVE sections in ALL levels
	 * that ended exactly at oldEndIndex.
	 *
	 * This is used when inserting a jump at the end of a section, to keep
	 * other "aligned" sections (parents/children on other levels) ending at the same boundary.
	 */
	private static void extendAllSectionEndsThatWereAt(ParkourMap map, int oldEndIndex, int maxLevel) {
		if (map == null) return;
		if (oldEndIndex < 0) return;

		for (int lvl = 1; lvl <= maxLevel; lvl++) {
			ArrayList<MapSection> sections = map.getSectionsForLevel(lvl);
			if (sections == null || sections.isEmpty()) continue;

			for (int i = 0; i < sections.size(); i++) {
				MapSection s = sections.get(i);
				if (s == null) continue;

				int start = s.getStartJumpIndex();
				int end = s.getEndJumpIndex();

				// Inactive sections -> ignore
				if (start < 0 || end < 0) continue;

				// Normalize for safety
				if (end < start) {
					int t = start;
					start = end;
					end = t;
				}

				if (end == oldEndIndex) {
					s.setEndJumpIndex(end + 1);
				}
			}
		}
	}

	public static void moveExistingJumpToEndOfSection(ParkourMap map, int levelOneBased, MapSection targetSection, Jump jump) {
		DataStoreRepository.ensureLoaded();
		if (map == null || targetSection == null || jump == null) return;

		insertJumpAtEndOfSection(map, levelOneBased, targetSection, jump);
	}

	/**
	 * Shifts section start/end indices by +1 for all sections in all levels
	 * when a new jump is inserted at insertPos in the jumps list.
	 */
	private static void shiftAllSectionRangesAfterInsert(ParkourMap map, int insertPos, int maxLevel) {
		if (map == null) return;

		for (int lvl = 1; lvl <= maxLevel; lvl++) {
			ArrayList<MapSection> sections = map.getSectionsForLevel(lvl);
			if (sections == null) continue;

			for (int i = 0; i < sections.size(); i++) {
				MapSection s = sections.get(i);
				if (s == null) continue;

				int start = s.getStartJumpIndex();
				int end = s.getEndJumpIndex();

				// Inactive section -> keep as inactive
				if (start < 0 || end < 0) {
					continue;
				}

				// If insertion is before or at start, both start and end shift.
				if (start >= insertPos) {
					start += 1;
					end += 1;
				}
				// If insertion is inside the section range (before or at end), end shifts.
				else if (end >= insertPos) {
					end += 1;
				}

				s.setStartJumpIndex(start);
				s.setEndJumpIndex(end);
			}
		}
	}

	/**
	 * Shifts section start/end indices after a jump is removed.
	 * If a section becomes invalid/out-of-range, it is deactivated (-1...-1) so it exists but is not rendered.
	 */
	public static void shiftAllSectionRangesAfterRemove(ParkourMap map, int removedIndex, int maxLevels) {
		if (map == null) return;

		if (maxLevels < 1) return;
		if (maxLevels > 4) maxLevels = 4;

		ArrayList<Jump> jumps = map.getJumps();
		int jumpCount = (jumps != null) ? jumps.size() : 0;

		for (int lvl = 1; lvl <= maxLevels; lvl++) {
			ArrayList<MapSection> secs = map.getSectionsForLevel(lvl);
			if (secs == null || secs.isEmpty()) continue;

			for (int i = 0; i < secs.size(); i++) {
				MapSection s = secs.get(i);
				if (s == null) continue;

				int start = s.getStartJumpIndex();
				int end = s.getEndJumpIndex();

				// Inactive section -> keep as inactive
				if (start < 0 || end < 0) {
					continue;
				}

				// If the removed index is before the section, shift the whole range left.
				if (removedIndex < start) {
					start -= 1;
					end -= 1;
				}
				// If removed index was inside the section, the section loses one element.
				else if (removedIndex >= start && removedIndex <= end) {
					end -= 1;
				}
				// else: removedIndex > end => unaffected

				// Validate against current jump count AFTER removal.
				// If out-of-range or empty/invalid, deactivate it instead of clamping.
				if (jumpCount <= 0) {
					deactivateSectionRange(s);
					continue;
				}

				if (end < start || end < 0 || start < 0) {
					deactivateSectionRange(s);
					continue;
				}

				// Out-of-range after shift (e.g., removed last jump, or section was at the end)
				if (start >= jumpCount || end >= jumpCount) {
					deactivateSectionRange(s);
					continue;
				}

				s.setStartJumpIndex(start);
				s.setEndJumpIndex(end);
			}
		}
	}

	public static boolean moveJumpByOneRespectingSections(ParkourMap map, Jump jump, int delta) {
		return moveByOne(map, jump, delta, true);
	}

	/**
	 * Moves a jump to the given index, exactly as if "move by one" was applied that many times
	 * (so section boundaries are respected the same way). Saves once.
	 */
	public static boolean moveJumpToIndex(ParkourMap map, Jump jump, int targetIndex) {
		DataStoreRepository.ensureLoaded();
		if (map == null || jump == null || map.getJumps() == null) return false;

		int from = JumpService.indexOfJumpByReference(map.getJumps(), jump);
		if (from < 0) return false;

		int steps = Math.abs(targetIndex - from);
		int delta = targetIndex > from ? 1 : -1;
		boolean movedAny = false;
		for (int i = 0; i < steps; i++) {
			if (!moveByOne(map, jump, delta, false)) break;
			movedAny = true;
		}

		if (movedAny) DataStoreRepository.save();
		return movedAny;
	}

	private static boolean moveByOne(ParkourMap map, Jump jump, int delta, boolean save) {
		DataStoreRepository.ensureLoaded();
		if (map == null || jump == null) return false;
		if (delta != -1 && delta != 1) return false;

		ArrayList<Jump> jumps = map.getJumps();
		if (jumps == null || jumps.size() <= 1) return false;

		int idx = JumpService.indexOfJumpByReference(jumps, jump);
		if (idx < 0) return false;

		int otherIdx = idx + delta;
		if (otherIdx < 0 || otherIdx >= jumps.size()) return false;

		int maxLevels = 0;
		try {
			maxLevels = map.getMaxSectionLevelsAllowed();
		} catch (Throwable ignored) { }

		if (maxLevels < 1) {
			// No sections configured -> plain swap by one.
			java.util.Collections.swap(jumps, idx, otherIdx);
			if (save) DataStoreRepository.save();
			return true;
		}
		if (maxLevels > 4) maxLevels = 4;

		// Try to shift section boundaries on ALL levels where a boundary exists at this move position.
		boolean shiftedAny = shiftBoundariesAllLevels(map, idx, delta, maxLevels);

		if (!shiftedAny) {
			// No boundary at any level -> normal swap by one.
			java.util.Collections.swap(jumps, idx, otherIdx);
		} else {
			// Minimal non-invasive cleanup:
			// If shifting boundaries emptied any section, deactivate it (-1/-1),
			// so it disappears from the table but stays in the section list.
			deactivateEmptySectionsAfterBoundaryShift(map, maxLevels);
		}

		// IMPORTANT: keep empty sections (do NOT remove them automatically).
		// No cleanupEmptySections(map) here.

		if (save) DataStoreRepository.save();
		return true;
	}

	private static void deactivateEmptySectionsAfterBoundaryShift(ParkourMap map, int maxLevels) {
		if (map == null) return;
		if (maxLevels < 1) return;
		if (maxLevels > 4) maxLevels = 4;

		for (int lvl = 1; lvl <= maxLevels; lvl++) {
			ArrayList<MapSection> secs = map.getSectionsForLevel(lvl);
			if (secs == null || secs.isEmpty()) continue;

			for (int i = 0; i < secs.size(); i++) {
				MapSection s = secs.get(i);
				if (s == null) continue;

				int start = s.getStartJumpIndex();
				int end = s.getEndJumpIndex();

				// Already inactive -> keep inactive
				if (start < 0 || end < 0) continue;

				// If boundary shifting made the section empty, deactivate it.
				// This matches the "all jumps removed from the section" behavior.
				if (end < start) {
					deactivateSectionRange(s);
				}
			}
		}
	}

	/**
	 * Shifts section boundaries by one step across ALL levels (1...maxLevels) where a boundary exists.
	 *
	 * Supports 3 cases per level:
	 *  A) left != null && right != null && left != right   (between two sibling sections)
	 *  B) left != null && right == null                    (between a section and "no subsection" area)
	 *  C) left == null && right != null                    (between "no subsection" area and a section)
	 *
	 * BUT: For cases B/C we only allow the shift if BOTH indices are inside the SAME ancestor chain
	 * on all higher levels (1...lvl-1). This prevents a deeper-level section from expanding across a parent boundary.
	 *
	 * Jumps' list order is NOT changed here.
	 *
	 * Returns true if at least one level boundary was shifted.
	 */
	private static boolean shiftBoundariesAllLevels(ParkourMap map, int idx, int delta, int maxLevels) {
		if (map == null) return false;
		if (delta != -1 && delta != 1) return false;

		boolean changed = false;

		for (int lvl = 1; lvl <= maxLevels; lvl++) {
			ArrayList<MapSection> secs = map.getSectionsForLevel(lvl);
			if (secs == null || secs.isEmpty()) continue;

			if (delta == -1) {
				// Move up: boundary between (idx-1) and idx
				int leftIdx = idx - 1;
				if (leftIdx < 0) continue;

				MapSection left = findSectionContainingIndex(secs, leftIdx);
				MapSection right = findSectionContainingIndex(secs, idx);

				// ---- Case A: between two different sections ----
				if (left != null && right != null && left != right) {
					int rStart = normStart(right);
					if (idx == rStart) {
						left.setEndJumpIndex(normEnd(left) + 1);
						right.setStartJumpIndex(rStart + 1);
						changed = true;
					}
					continue;
				}

				// ---- Case B: left is a section, right is "no section" (no subsection) ----
				// Example: parent S2 includes both, but only leftIdx is inside child R2; idx is outside any child.
				if (left != null && right == null) {
					// Only if idx is immediately AFTER left's end, and both indices share the same ancestors on higher levels.
					int lEnd = normEnd(left);
					if (idx == lEnd + 1 && sameAncestorsOnHigherLevels(map, leftIdx, idx, lvl, maxLevels)) {
						left.setEndJumpIndex(lEnd + 1); // expand child to include idx
						changed = true;
					}
					continue;
				}

				// ---- Case C: left is "no section", right is a section ----
				// This would mean idx is inside a section, leftIdx is not; moving up can pull idx out by shifting right.start.
				if (left == null && right != null) {
					int rStart = normStart(right);
					if (idx == rStart && sameAncestorsOnHigherLevels(map, leftIdx, idx, lvl, maxLevels)) {
						right.setStartJumpIndex(rStart + 1); // shrink from the start (idx leaves the section)
						changed = true;
					}
				}
			} else {
				// delta == +1
				// Move down: boundary between idx and (idx+1)
				int rightIdx = idx + 1;

				MapSection left = findSectionContainingIndex(secs, idx);
				MapSection right = findSectionContainingIndex(secs, rightIdx);

				// ---- Case A: between two different sections ----
				if (left != null && right != null && left != right) {
					int lEnd = normEnd(left);
					if (idx == lEnd) {
						left.setEndJumpIndex(lEnd - 1);
						right.setStartJumpIndex(normStart(right) - 1);
						changed = true;
					}
					continue;
				}

				// ---- Case B: left is a section, right is "no section" (no subsection) ----
				// Example: idx is last inside child R2, rightIdx is outside any child but still within same parent S2.
				if (left != null && right == null) {
					int lEnd = normEnd(left);
					if (idx == lEnd && sameAncestorsOnHigherLevels(map, idx, rightIdx, lvl, maxLevels)) {
						left.setEndJumpIndex(lEnd - 1); // shrink child, idx leaves the child
						changed = true;
					}
					continue;
				}

				// ---- Case C: left is "no section", right is a section ----
				// Moving down can pull idx into the right section by expanding right.start to the left.
				if (left == null && right != null) {
					int rStart = normStart(right);
					if (rightIdx == rStart && sameAncestorsOnHigherLevels(map, idx, rightIdx, lvl, maxLevels)) {
						right.setStartJumpIndex(rStart - 1); // expand section to include idx
						changed = true;
					}
				}
			}
		}

		return changed;
	}

	private static int normStart(MapSection s) {
		int a = s.getStartJumpIndex();
		int b = s.getEndJumpIndex();
		return (a <= b) ? a : b;
	}

	private static int normEnd(MapSection s) {
		int a = s.getStartJumpIndex();
		int b = s.getEndJumpIndex();
		return (a <= b) ? b : a;
	}

	/**
	 * Finds the first section whose (normalized) range contains index.
	 * Empty sections (start > end) never match and are simply ignored (kept in list).
	 */
	public static MapSection findSectionContainingIndex(ArrayList<MapSection> secs, int index) {
		if (secs == null || index < 0) return null;

		for (int i = 0; i < secs.size(); i++) {
			MapSection s = secs.get(i);
			if (s == null) continue;

			int a = s.getStartJumpIndex();
			int b = s.getEndJumpIndex();
			if (a > b) {
				// empty section, keep it but it cannot contain anything
				continue;
			}

			if (index >= a && index <= b) {
				return s;
			}
		}

		return null;
	}

	/**
	 * Returns true if indexA and indexB belong to the SAME section chain on all higher levels (1...lvl-1).
	 * If on a higher level there are no sections, we treat it as "same".
	 *
	 * This is the key rule that allows cross-nesting behavior safely:
	 * - A child section may expand into "no subsection" area only if both indices are inside the same parent(s).
	 */
	private static boolean sameAncestorsOnHigherLevels(ParkourMap map, int indexA, int indexB, int currentLevelOneBased, int maxLevels) {
		if (map == null) return false;
		if (currentLevelOneBased <= 1) return true; // no higher levels

		int top = Math.min(maxLevels, currentLevelOneBased - 1);
		for (int lvl = 1; lvl <= top; lvl++) {
			ArrayList<MapSection> secs = map.getSectionsForLevel(lvl);
			if (secs == null || secs.isEmpty()) {
				// no constraints at this level
				continue;
			}

			MapSection a = findSectionContainingIndex(secs, indexA);
			MapSection b = findSectionContainingIndex(secs, indexB);

			if (a != b) {
				return false;
			}
		}

		return true;
	}

}
