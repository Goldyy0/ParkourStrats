package me.texyle.startreminders.ui;

import java.io.File;

import me.texyle.startreminders.data.ParkourMap;
import me.texyle.startreminders.data.ServerProfile;
import me.texyle.startreminders.gui.FileDialogs;
import me.texyle.startreminders.importing.MapTemplateIO;
import me.texyle.startreminders.utils.ChatUtils;

/** Export / import of a map as a template file, using the native file dialogs. */
final class MapTemplateActions {

    private MapTemplateActions() {}

    static void exportMap(final ServerProfile server, final ParkourMap map) {
        String suggested = (map.getId() != null ? map.getId() : "map") + ".json";

        FileDialogs.chooseSaveFile("Export Template (.json)", suggested, new FileDialogs.FileHandler() {
            @Override
            public void onFile(File file) {
                try {
                    MapTemplateIO.exportMapTemplateToFile(server, map, file);
                    ChatUtils.info("Exported template: " + file.getName());
                } catch (Exception ex) {
                    ex.printStackTrace();
                    ChatUtils.error("Export failed. See logs.");
                }
            }
        });
    }

    /** @param onDone runs after a successful import (to refresh lists) */
    static void importInto(final ServerProfile server, final Runnable onDone) {
        FileDialogs.chooseOpenFile("Import Template (.json)", new FileDialogs.FileHandler() {
            @Override
            public void onFile(File file) {
                try {
                    MapTemplateIO.importMapTemplateFromFile(server, file);
                    if (onDone != null) onDone.run();
                    ChatUtils.info("Imported template: " + file.getName());
                } catch (Exception ex) {
                    ex.printStackTrace();
                    ChatUtils.error("Import failed. See logs.");
                }
            }
        });
    }
}
