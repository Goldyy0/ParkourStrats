package me.texyle.startreminders.gui.hierarchy;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;

import me.texyle.startreminders.data.ParkourMap;
import me.texyle.startreminders.data.ServerProfile;
import me.texyle.startreminders.reminders.ReminderManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

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
    private static final int BTN_STOP_SYNC = 8;

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
    private GuiButton stopSyncButton;

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

                        ReminderManager.setSelectedMap(selected);

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

        boolean isProtected = ReminderManager.isProtectedServer(server);
        boolean isPickerMode = (pickHandler != null);

        backButton = new GuiButton(BTN_BACK, 8, 8, 60, 20, "Back");
        this.buttonList.add(backButton);

        // IMPORTANT:
        // In picker mode, this screen is only used to select a map (no management buttons).
        // Also, protected servers (Global / RestoredStrats) do not show management buttons.
        if (!isPickerMode && !isProtected) {
            createButton = new GuiButton(BTN_CREATE, this.width / 2 - 154, this.height - 44, 100, 20, "Create");
            editButton = new GuiButton(BTN_EDIT, this.width / 2 - 50, this.height - 44, 100, 20, "Edit");
            removeButton = new GuiButton(BTN_REMOVE, this.width / 2 + 54, this.height - 44, 100, 20, "Remove");

            this.buttonList.add(createButton);
            this.buttonList.add(editButton);
            this.buttonList.add(removeButton);

            // Sync Sheet button (moved down, left of Create)
            syncSheetButton = new GuiButton(BTN_SYNC_SHEET, createButton.xPosition - 104, createButton.yPosition, 100, 20, "Sync Sheet");
            this.buttonList.add(syncSheetButton);

            // Stop Sync button (right of Remove)
            stopSyncButton = new GuiButton(BTN_STOP_SYNC, removeButton.xPosition + removeButton.width + 8, removeButton.yPosition, 100, 20, "Stop Sync");
            this.buttonList.add(stopSyncButton);

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
            stopSyncButton = null;
            exportTemplateButton = null;
            importTemplateButton = null;
        }

        updateButtonStates();
    }

    private void refreshData() {
        maps = new ArrayList<ParkourMap>(ReminderManager.getMaps(server));
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

        if (stopSyncButton != null) {
            stopSyncButton.enabled = (selected != null && selected.hasSheetConfigured());
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

        boolean isProtected = ReminderManager.isProtectedServer(server);
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
                            ReminderManager.setSheetUrlForMap(selected, text);
                            ReminderManager.requestSheetSyncForMap(server, selected, true);
                            initGui();
                        }

                        @Override
                        public void onCancel() { }
                    }
            ));
            return;
        }

        if (button.id == BTN_STOP_SYNC) {
            if (selected == null) {
                return;
            }

            ReminderManager.stopSheetSyncForMap(server, selected);

            sendClientChat(EnumChatFormatting.DARK_AQUA + "[ParkourStrats] " + EnumChatFormatting.AQUA
                    + "Sync stopped for map: " + selected.getId());

            initGui();
            return;
        }

        if (button.id == BTN_CREATE) {
            Minecraft.getMinecraft().displayGuiScreen(new GuiTextPrompt(
                    this,
                    "Create map",
                    "",
                    new GuiTextPrompt.IResultHandler() {
                        @Override
                        public void onConfirm(String text) {
                            ReminderManager.createMap(server, text);
                            initGui();
                        }

                        @Override
                        public void onCancel() { }
                    }
            ));
            return;
        }

        if (selected == null) {
            return;
        }

        if (button.id == BTN_EDIT) {
            Minecraft.getMinecraft().displayGuiScreen(new GuiTextPrompt(
                    this,
                    "Rename map",
                    selected.getId(),
                    new GuiTextPrompt.IResultHandler() {
                        @Override
                        public void onConfirm(String text) {
                            ReminderManager.renameMap(server, selected, text);
                            initGui();
                        }

                        @Override
                        public void onCancel() { }
                    }
            ));
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
                            ReminderManager.removeMap(server, selected);
                            initGui();
                        }

                        @Override
                        public void onNo() { }
                    }
            ));
        }
    }

    private void openExportDialog(final ParkourMap selected) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final File[] chosen = new File[1];

                    SwingUtilities.invokeAndWait(new Runnable() {
                        @Override
                        public void run() {
                            JFileChooser chooser = new JFileChooser();
                            chooser.setDialogTitle("Export Template (.json)");
                            chooser.setSelectedFile(new File((selected.getId() != null ? selected.getId() : "map") + ".json"));
                            int result = chooser.showSaveDialog(null);
                            if (result == JFileChooser.APPROVE_OPTION) {
                                chosen[0] = chooser.getSelectedFile();
                            }
                        }
                    });

                    if (chosen[0] == null) return;

                    Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                ReminderManager.exportMapTemplateToFile(server, selected, chosen[0]);
                                sendClientChat(EnumChatFormatting.DARK_AQUA + "[ParkourStrats] " + EnumChatFormatting.AQUA
                                        + "Exported template: " + chosen[0].getName());
                            } catch (Exception ex) {
                                ex.printStackTrace();
                                sendClientChat(EnumChatFormatting.DARK_AQUA + "[ParkourStrats] " + EnumChatFormatting.RED
                                        + "Export failed. See logs.");
                            }
                        }
                    });

                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }).start();
    }

    private void openImportDialog() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final File[] chosen = new File[1];

                    SwingUtilities.invokeAndWait(new Runnable() {
                        @Override
                        public void run() {
                            JFileChooser chooser = new JFileChooser();
                            chooser.setDialogTitle("Import Template (.json)");
                            int result = chooser.showOpenDialog(null);
                            if (result == JFileChooser.APPROVE_OPTION) {
                                chosen[0] = chooser.getSelectedFile();
                            }
                        }
                    });

                    if (chosen[0] == null) return;

                    Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                ReminderManager.importMapTemplateFromFile(server, chosen[0]);
                                refreshData();
                                initGui();
                                sendClientChat(EnumChatFormatting.DARK_AQUA + "[ParkourStrats] " + EnumChatFormatting.AQUA
                                        + "Imported template: " + chosen[0].getName());
                            } catch (Exception ex) {
                                ex.printStackTrace();
                                sendClientChat(EnumChatFormatting.DARK_AQUA + "[ParkourStrats] " + EnumChatFormatting.RED
                                        + "Import failed. See logs.");
                            }
                        }
                    });

                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }).start();
    }

    private void sendClientChat(String msg) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null || msg == null) return;
        mc.thePlayer.addChatMessage(new ChatComponentText(msg));
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