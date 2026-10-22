package me.texyle.startreminders.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import me.texyle.startreminders.data.ParkourMap;
import me.texyle.startreminders.data.ServerProfile;
import me.texyle.startreminders.data.SheetFilter;
import me.texyle.startreminders.sheets.SheetData;
import me.texyle.startreminders.sheets.SheetImportService;
import me.texyle.startreminders.sheets.SheetSyncManager;
import me.texyle.startreminders.utils.ChatUtils;

/** Importing a sheet: download it, let the player review it, then import what they chose. */
final class SheetImportActions {

    private SheetImportActions() {}

    private interface Opened {
        void open(SheetData data, GuiScreen parent);
    }

    /** Downloads the sheet; on success {@code next} runs on the client thread with the screen that was open. */
    private static void download(String url, final Opened next) {
        ChatUtils.info("Downloading the sheet...");
        SheetSyncManager.fetchAsync(url, -1, new SheetSyncManager.Callback() {
            @Override
            public void onSuccess(final SheetData data) {
                Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                    @Override
                    public void run() {
                        next.open(data, Minecraft.getMinecraft().currentScreen);
                    }
                });
            }

            @Override
            public void onError(final String message) {
                Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                    @Override
                    public void run() {
                        ChatUtils.error(message);
                    }
                });
            }
        });
    }

    /** A new map from a sheet. */
    static void importAsNewMap(final ServerProfile server, final String mapId, final String url) {
        String problem = SheetImportService.checkNewMapName(server, mapId);
        if (problem != null) {
            ChatUtils.error(problem);
            return;
        }

        download(url, new Opened() {
            @Override
            public void open(final SheetData data, GuiScreen parent) {
                Minecraft.getMinecraft().displayGuiScreen(new GuiSheetReview(parent, data, null, "Import sheet", "Import",
                        new GuiSheetReview.Confirm() {
                            @Override
                            public void onConfirm(SheetFilter filter) {
                                SheetImportService.createMapFromSheet(server, mapId, url, data, filter);
                            }
                        }));
            }
        });
    }

    /** Links a (possibly different) sheet to an existing map. */
    static void linkSheet(final ParkourMap map, final String url) {
        // The old choices only mean something for the same sheet.
        final SheetFilter previous = url.trim().equals(map.getSheetUrl().trim()) ? map.getSheetFilter() : null;

        download(url, new Opened() {
            @Override
            public void open(final SheetData data, GuiScreen parent) {
                Minecraft.getMinecraft().displayGuiScreen(new GuiSheetReview(parent, data, previous, "Link sheet", "Import",
                        new GuiSheetReview.Confirm() {
                            @Override
                            public void onConfirm(SheetFilter filter) {
                                SheetImportService.applySheet(map, url, data, filter);
                            }
                        }));
            }
        });
    }

    /** Changes which part of the already linked sheet is imported. */
    static void editFilter(final ParkourMap map) {
        download(map.getSheetUrl(), new Opened() {
            @Override
            public void open(final SheetData data, GuiScreen parent) {
                Minecraft.getMinecraft().displayGuiScreen(new GuiSheetReview(parent, data, map.getSheetFilter(),
                        "Import filter", "Apply", new GuiSheetReview.Confirm() {
                            @Override
                            public void onConfirm(SheetFilter filter) {
                                SheetImportService.applySheet(map, null, data, filter);
                            }
                        }));
            }
        });
    }
}
