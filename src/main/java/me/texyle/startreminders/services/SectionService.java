package me.texyle.startreminders.services;

import java.util.ArrayList;
import java.util.UUID;

import me.texyle.startreminders.data.Jump;
import me.texyle.startreminders.data.MapSection;
import me.texyle.startreminders.data.ParkourMap;
import me.texyle.startreminders.persistence.DataStoreRepository;

/** Creates and updates the sections of a map level, enforcing that ranges are valid and do not overlap. */
public final class SectionService {

	private SectionService() {}

	/**
	 * Saves a new section, or updates {@code editing}.
	 * The range is optional: either both indices are set or both are null (section exists but is not shown).
	 *
	 * @return null on success, otherwise a message explaining why nothing was saved
	 */
	public static String saveSection(ParkourMap map, int levelOneBased, MapSection editing,
	                                 String name, int colorArgb, int textColorArgb,
	                                 Integer startIdx, Integer endIdx) {
		if (map == null) {
			return "Map is null.";
		}

		String trimmedName = (name != null) ? name.trim() : "";
		if (trimmedName.length() == 0) {
			return "Section name cannot be empty.";
		}

		ArrayList<Jump> jumps = map.getJumps();
		int jumpCount = (jumps != null) ? jumps.size() : 0;

		boolean hasStart = (startIdx != null);
		boolean hasEnd = (endIdx != null);

		if (hasStart != hasEnd) {
			return "Please set both Start and End, or leave both Not set.";
		}

		boolean hasRange = hasStart;

		int s = -1;
		int e = -1;

		if (hasRange) {
			s = startIdx.intValue();
			e = endIdx.intValue();

			if (jumpCount <= 0) {
				return "This map has no jumps. Clear the range to save this section.";
			}

			if (s < 0 || e < 0 || s >= jumpCount || e >= jumpCount) {
				return "Invalid section range.";
			}

			if (e < s) {
				int t = s;
				s = e;
				e = t;
			}

			if (overlapsExistingSection(map, levelOneBased, editing, s, e)) {
				return "Section range overlaps another section on this level.";
			}
		}

		MapSection target = (editing != null) ? editing : new MapSection();
		if (editing == null) {
			target.setId(UUID.randomUUID().toString());
		}

		target.setName(trimmedName);
		target.setColorArgb(colorArgb);
		target.setTextColorArgb(textColorArgb);

		if (hasRange) {
			target.setStartJumpIndex(s);
			target.setEndJumpIndex(e);
			target.normalizeRange();
		} else {
			// No range => section exists but is not rendered anywhere.
			target.setStartJumpIndex(-1);
			target.setEndJumpIndex(-1);
		}

		if (editing == null) {
			map.getSectionsForLevel(levelOneBased).add(target);
		}

		DataStoreRepository.save();
		return null;
	}

	/** Removes the section from its level and saves. */
	public static void deleteSection(ParkourMap map, int levelOneBased, MapSection section) {
		if (map == null || section == null) return;

		ArrayList<MapSection> list = map.getSectionsForLevel(levelOneBased);
		if (list == null) return;

		list.remove(section);
		DataStoreRepository.save();
	}

	/** True if [s, e] (inclusive) intersects another active section on the level; the edited section is ignored. */
	public static boolean overlapsExistingSection(ParkourMap map, int levelOneBased, MapSection editing, int s, int e) {
		if (map == null) return false;

		ArrayList<MapSection> list = map.getSectionsForLevel(levelOneBased);
		if (list == null || list.isEmpty()) return false;

		for (int i = 0; i < list.size(); i++) {
			MapSection other = list.get(i);
			if (other == null) continue;

			// Skip the section being edited.
			if (editing != null && other == editing) continue;

			int a = other.getStartJumpIndex();
			int b = other.getEndJumpIndex();

			// Inactive sections do not participate in overlap checks.
			if (a < 0 || b < 0) continue;

			if (b < a) {
				int t = a;
				a = b;
				b = t;
			}

			if (Math.max(a, s) <= Math.min(b, e)) {
				return true;
			}
		}

		return false;
	}
}
