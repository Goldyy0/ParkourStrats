package me.texyle.startreminders.gui.hierarchy;

import net.minecraft.client.gui.GuiScreen;

import me.texyle.startreminders.data.ServerProfile;
import me.texyle.startreminders.services.MapService;

/** Form for creating a new map in a server. */
public class GuiCreateMap extends GuiMapForm {

    private static final String[] DEFAULT_SECTION_NAMES = { "Section", "Sub-section", "Area", "CP" };

    private final ServerProfile server;

    public GuiCreateMap(GuiScreen parent, ServerProfile server) {
        super(parent);
        this.server = server;
    }

    @Override
    protected String getTitle() {
        return "Create map";
    }

    @Override
    protected String getSubmitLabel() {
        return "Create";
    }

    @Override
    protected Prefill getPrefill() {
        // Keep whatever sections toggle / count the user already chose when the screen is rebuilt.
        return new Prefill("", null, null, DEFAULT_SECTION_NAMES);
    }

    @Override
    protected boolean submit(String mapName, boolean sectionsEnabled, int sectionsCount, String[] sectionNames) {
        return MapService.createMap(server, mapName, sectionsEnabled, sectionsCount, sectionNames);
    }

    @Override
    protected String getFailureMessage() {
        return "Create failed. Name may be invalid or already exists.";
    }
}
