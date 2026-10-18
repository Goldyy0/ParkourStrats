package me.texyle.startreminders.gui.hierarchy;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import me.texyle.startreminders.data.ParkourMap;
import me.texyle.startreminders.data.ServerProfile;
import me.texyle.startreminders.gui.FileDialogs;
import me.texyle.startreminders.importing.MapTemplateIO;
import me.texyle.startreminders.persistence.BuiltinContainers;
import me.texyle.startreminders.reminders.SelectionState;
import me.texyle.startreminders.services.MapService;
import me.texyle.startreminders.sheets.SheetTemplateSync;
import me.texyle.startreminders.utils.ChatUtils;

public class GuiMapList extends GuiScreen {

    public interface IMapPickHandler {
        void onPick(ParkourMap map);
    }

    private static final int BTN_CREATE = 1;
    private static final int BTN_EDIT = 2;
    private static final int BTN_REMOVE = 3;
    private static final int BTN_BACK = 4;

    private static final int BTN_SYNC_SHEET = 5;
    private static final int BTN_EXPORT_TEMPLATE = 6;
    private static final int BTN_IMPORT_TEMPLATE = 7;
    private static final int BTN_UPDATE_SYNC = 8;

    private final GuiScreen parent;
    private final ServerProfile server;
    private final IMapPickHandler pickHandler;

    private GuiListSlot<ParkourMap> list;
    private ArrayList<ParkourMap> maps;

    private GuiButton createButton;
    private GuiButton editButton;
    private GuiButton removeButton;
    private GuiButton backButton;

    private GuiButton syncSheetButton;
    private GuiButton exportTemplateButton;
    private GuiButton importTemplateButton;
    private GuiButton updateSyncButton;

    public GuiMapList(GuiScreen parent, ServerProfile server) {
        this(parent, server, null);
    }

    public GuiMapList(GuiScreen parent, ServerProfile server, IMapPickHandler pickHandler) {
        this.parent = parent;
        this.server = server;
        this.pickHandler = pickHandler;
    }

    @Override
    public void initGui() {
        super.initGui();

        refreshData();

        int top = 32;
        int bottom = this.height - 52;

        list = new GuiListSlot<ParkourMap>(
                Minecraft.getMinecraft(),
                this.width,
                this.height,
                top,
                bottom,
                22,
                maps,
                new GuiListSlot.ILabelProvider<ParkourMap>() {
                    @Override
                    public String getLabel(ParkourMap item) {
                        return item != null ? item.getId() : "<null>";
                    }
                },
                new GuiListSlot.ISelectionHandler() {
                    @Override
                    public void onSingleClick(int index) {
                        updateButtonStates();
                    }

                    @Override
                    public void onDoubleClick(int index) {
                        ParkourMap selected = list.getSelectedItem();
                        if (selected == null) {
                            return;
                        }

                        SelectionState.setSelectedMap(selected);

                        // Picker mode: return selection to caller
                        if (pickHandler != null) {
                            pickHandler.onPick(selected);
                            Minecraft.getMinecraft().displayGuiScreen(parent);
                            return;
                        }

                        // Default mode: open jumps
                        Minecraft.getMinecraft().displayGuiScreen(new GuiJumpList(GuiMapList.this, selected));
                    }
                }
        );

        boolean isProtected = BuiltinContainers.isProtectedServer(server);
        boolean isPickerMode = (pickHandler != null);

        backButton = new GuiButton(BTN_BACK, 8, 8, 60, 20, "Back");
        this.buttonList.add(backButton);

        // IMPORTANT:
        // In picker mode, this screen is only used to select a map (no management buttons).
        // Also, protected servers (Global / RestoredStrats) do not show management buttons.
        if (!isPickerMode && !isProtected) {
            createButton = new GuiButton(BTN_CREATE, this.width / 2 - 154, this.height - 44, 100, 20, "Create");
            editButton = new GuiButton(BTN_EDIT, this.width / 2 - 50, this.height - 44, 100, 20, "Edit map");
            removeButton = new GuiButton(BTN_REMOVE, this.width / 2 + 54, this.height - 44, 100, 20, "Remove");

            this.buttonList.add(createButton);
            this.buttonList.add(editButton);
            this.buttonList.add(removeButton);

            // Sync Sheet button (moved down, left of Create)
            syncSheetButton = new GuiButton(BTN_SYNC_SHEET, createButton.xPosition - 104, createButton.yPosition, 100, 20, "Sync Sheet");
            this.buttonList.add(syncSheetButton);

            // Update Sync button (right of Remove)
            updateSyncButton = new GuiButton(BTN_UPDATE_SYNC, removeButton.xPosition + removeButton.width + 8, removeButton.yPosition, 100, 20, "Update Sync");
            this.buttonList.add(updateSyncButton);

            // Layout:
            // - Import Template: top-left, right next to Back
            // - Export Template: top-right corner
            int topY2 = 8;
            int btnH = 20;
            int gap = 4;

            int importW = 120;
            int exportW = 120;

            int importX = backButton.xPosition + backButton.width + gap;
            int exportX = this.width - 8 - exportW;

            importTemplateButton = new GuiButton(BTN_IMPORT_TEMPLATE, importX, topY2, importW, btnH, "Import Template");
            exportTemplateButton = new GuiButton(BTN_EXPORT_TEMPLATE, exportX, topY2, exportW, btnH, "Export Template");

            this.buttonList.add(importTemplateButton);
            this.buttonList.add(exportTemplateButton);
        } else {
            // Ensure references stay null
            createButton = null;
            editButton = null;
            removeButton = null;

            syncSheetButton = null;
            updateSyncButton = null;
            exportTemplateButton = null;
            importTemplateButton = null;
        }

        updateButtonStates();
    }

    private void refreshData() {
        maps = new ArrayList<ParkourMap>(MapService.getMaps(server));
    }

    private void updateButtonStates() {
        boolean hasSelection = list != null && list.getSelectedItem() != null;
        ParkourMap selected = list != null ? list.getSelectedItem() : null;
        boolean isPickerMode = (pickHandler != null);

        // In picker mode, there are no management buttons.
        if (isPickerMode) {
            return;
        }

        if (editButton != null) editButton.enabled = hasSelection;
        if (removeButton != null) removeButton.enabled = hasSelection;

        if (createButton != null) createButton.enabled = true;

        if (syncSheetButton != null) {
            syncSheetButton.enabled = hasSelection;
        }

        if (updateSyncButton != null) {
            updateSyncButton.enabled = (selected != null && selected.hasSheetConfigured());
        }

        if (exportTemplateButton != null) {
            exportTemplateButton.enabled = hasSelection;
        }

        if (importTemplateButton != null) {
            importTemplateButton.enabled = true;
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();

        String titleBase = "Maps for: " + (server != null ? server.getId() : "<null>");
        this.drawCenteredString(this.fontRendererObj, titleBase, this.width / 2, 12, 0xFFFFFF);

        list.drawScreen(mouseX, mouseY, partialTicks);

        updateButtonStates();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        list.handleMouseInput();
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button == null) return;

        if (button.id == BTN_BACK) {
            Minecraft.getMinecraft().displayGuiScreen(parent);
            return;
        }

        boolean isProtected = BuiltinContainers.isProtectedServer(server);
        boolean isPickerMode = (pickHandler != null);

        // Picker mode: no management actions allowed.
        if (isPickerMode) {
            return;
        }

        ParkourMap selected = list != null ? list.getSelectedItem() : null;

        // Protected servers do not support any management actions here.
        if (isProtected) {
            return;
        }

        if (button.id == BTN_IMPORT_TEMPLATE) {
            openImportDialog();
            return;
        }

        if (button.id == BTN_EXPORT_TEMPLATE) {
            if (selected == null) return;
            openExportDialog(selected);
            return;
        }

        if (button.id == BTN_SYNC_SHEET) {
            if (selected == null) {
                return;
            }

            String current = selected.getSheetUrl();

            Minecraft.getMinecraft().displayGuiScreen(new GuiTextPrompt(
                    this,
                    "Sheet URL (public) for map: " + selected.getId(),
                    current != null ? current : "",
                    new GuiTextPrompt.IResultHandler() {
                        @Override
                        public void onConfirm(String text) {
                            SheetTemplateSync.setSheetUrlForMap(selected, text);
                            SheetTemplateSync.requestSheetSyncForMap(server, selected, true);
                            initGui();
                        }

                        @Override
                        public void onCancel() { }
                    }
            ));
            return;
        }

        if (button.id == BTN_UPDATE_SYNC) {
            if (selected == null) {
                return;
            }

            // Manual sync trigger (no auto-sync anymore)
            SheetTemplateSync.requestSheetSyncForMap(server, selected, true);
            initGui();
            return;
        }

        if (button.id == BTN_CREATE) {
            Minecraft.getMinecraft().displayGuiScreen(new GuiCreateMap(this, server));
            return;
        }

        if (selected == null) {
            return;
        }

        if (button.id == BTN_EDIT) {
            if (selected == null) return;
            Minecraft.getMinecraft().displayGuiScreen(new GuiEditMap(this, server, selected));
            return;
        }

        if (button.id == BTN_REMOVE) {
            Minecraft.getMinecraft().displayGuiScreen(new GuiConfirm(
                    this,
                    "Remove map",
                    "Are you sure?",
                    new GuiConfirm.IConfirmHandler() {
                        @Override
                        public void onYes() {
                            MapService.removeMap(server, selected);
                            initGui();
                        }

                        @Override
                        public void onNo() { }
                    }
            ));
        }
    }

    private void openExportDialog(final ParkourMap selected) {
        String suggested = (selected.getId() != null ? selected.getId() : "map") + ".json";

        FileDialogs.chooseSaveFile("Export Template (.json)", suggested, new FileDialogs.FileHandler() {
            @Override
            public void onFile(File file) {
                try {
                    MapTemplateIO.exportMapTemplateToFile(server, selected, file);
                    ChatUtils.info("Exported template: " + file.getName());
                } catch (Exception ex) {
                    ex.printStackTrace();
                    ChatUtils.error("Export failed. See logs.");
                }
            }
        });
    }

    private void openImportDialog() {
        FileDialogs.chooseOpenFile("Import Template (.json)", new FileDialogs.FileHandler() {
            @Override
            public void onFile(File file) {
                try {
                    MapTemplateIO.importMapTemplateFromFile(server, file);
                    refreshData();
                    initGui();
                    ChatUtils.info("Imported template: " + file.getName());
                } catch (Exception ex) {
                    ex.printStackTrace();
                    ChatUtils.error("Import failed. See logs.");
                }
            }
        });
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        // Esc
        if (keyCode == 1) {
            Minecraft.getMinecraft().displayGuiScreen(parent);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }
}