package me.texyle.startreminders.gui.hierarchy;

import net.minecraft.client.gui.GuiScreen;

import me.texyle.startreminders.data.ParkourMap;
import me.texyle.startreminders.data.ServerProfile;
import me.texyle.startreminders.services.MapService;

/** Form for renaming a map and changing its section configuration. */
public class GuiEditMap extends GuiMapForm {

    private static final String[] DEFAULT_SECTION_NAMES = { "Section", "Sub-section", "Area", "CP" };

    private final ServerProfile server;
    private final ParkourMap map;

    public GuiEditMap(GuiScreen parent, ServerProfile server, ParkourMap map) {
        super(parent);
        this.server = server;
        this.map = map;
    }

    @Override
    protected String getTitle() {
        return "Edit map";
    }

    @Override
    protected String getSubmitLabel() {
        return "Save";
    }

    @Override
    protected Prefill getPrefill() {
        if (map == null) {
            return new Prefill("", Boolean.FALSE, Integer.valueOf(1), DEFAULT_SECTION_NAMES);
        }

        String currentId = (map.getId() != null) ? map.getId() : "";

        int effective = map.getEffectiveSectionsCount();
        int count = (effective <= 0) ? 1 : effective;

        String[] stored = map.getSectionNamesSafe();
        String[] names = new String[4];
        for (int i = 0; i < 4; i++) {
            names[i] = (stored != null && i < stored.length && stored[i] != null) ? stored[i] : "";
        }

        return new Prefill(currentId, Boolean.valueOf(map.isSectionsEnabled()), Integer.valueOf(count), names);
    }

    @Override
    protected String getContextError() {
        return (server == null || map == null) ? "Cannot edit: missing context." : null;
    }

    @Override
    protected boolean submit(String mapName, boolean sectionsEnabled, int sectionsCount, String[] sectionNames) {
        return MapService.updateMapConfig(server, map, mapName, sectionsEnabled, sectionsCount, sectionNames);
    }

    @Override
    protected String getFailureMessage() {
        return "Save failed. Name may be invalid or already exists.";
    }
}
