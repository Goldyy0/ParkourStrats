package me.texyle.startreminders.gui.hierarchy;

import java.io.IOException;
import java.util.ArrayList;

import me.texyle.startreminders.data.ParkourMap;
import me.texyle.startreminders.data.ServerProfile;
import me.texyle.startreminders.reminders.ReminderManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

public class GuiServerList extends GuiScreen {

    public interface IServerPickHandler {
        void onPick(ServerProfile server);
    }

    private final GuiScreen parent;
    private final IServerPickHandler pickHandler;

    private GuiListSlot<ServerProfile> list;
    private ArrayList<ServerProfile> servers;

    private GuiButton createButton;
    private GuiButton editButton;
    private GuiButton removeButton;
    private GuiButton backButton;
    private final boolean allowRestoredInPicker;

    public GuiServerList(GuiScreen parent) {
        this(parent, null, false);
    }

    public GuiServerList(GuiScreen parent, IServerPickHandler pickHandler, boolean allowRestoredInPicker) {
        this.parent = parent;
        this.pickHandler = pickHandler;
        this.allowRestoredInPicker = allowRestoredInPicker;
    }

    @Override
    public void initGui() {
        super.initGui();

        refreshData();

        int top = 32;
        int bottom = this.height - 52;

        list = new GuiListSlot<ServerProfile>(
                Minecraft.getMinecraft(),
                this.width,
                this.height,
                top,
                bottom,
                22,
                servers,
                new GuiListSlot.ILabelProvider<ServerProfile>() {
                    @Override
                    public String getLabel(ServerProfile item) {
                        if (item == null) {
                            return "<null>";
                        }

                        String id = item.getId() != null ? item.getId() : "<null>";

                        // Custom colors for protected servers
                        if (ReminderManager.isProtectedServer(item)) {
                            if (ReminderManager.isGlobalServer(item) || "Global".equals(id)) {
                                return "\u00A7a" + id + "\u00A7r"; // green
                            }
                            if (ReminderManager.isRestoredServer(item) || "RestoredStrats".equals(id)) {
                                return "\u00A7e" + id + "\u00A7r"; // yellow
                            }

                            return "\u00A7a" + id + "\u00A7r";
                        }

                        return id;
                    }
                },
                new GuiListSlot.ISelectionHandler() {
                    @Override
                    public void onSingleClick(int index) {
                        updateButtonStates();
                    }

                    @Override
                    public void onDoubleClick(int index) {
                        ServerProfile selected = list.getSelectedItem();
                        if (selected == null) {
                            return;
                        }

                        ReminderManager.setSelectedServer(selected);

                        // Picker mode: return selection to caller
                        if (pickHandler != null) {
                            pickHandler.onPick(selected);
                            Minecraft.getMinecraft().displayGuiScreen(parent);
                            return;
                        }

                        // Default mode:
                        // Protected servers (Global / RestoredStrats) -> open jumps directly (skip maps)
                        if (ReminderManager.isProtectedServer(selected)) {
                            ParkourMap targetMap = null;

                            if (ReminderManager.isGlobalServer(selected)) {
                                targetMap = ReminderManager.getGlobalMap();
                            } else if (ReminderManager.isRestoredServer(selected)) {
                                targetMap = ReminderManager.getRestoredMap();
                            }

                            if (targetMap != null) {
                                ReminderManager.setSelectedMap(targetMap);
                                Minecraft.getMinecraft().displayGuiScreen(new GuiJumpList(GuiServerList.this, targetMap));
                                return;
                            }
                        }

                        // Normal server -> open maps
                        Minecraft.getMinecraft().displayGuiScreen(new GuiMapList(GuiServerList.this, selected));
                    }
                }
        );

        boolean isPickerMode = (pickHandler != null);

        backButton = new GuiButton(4, 8, 8, 60, 20, "Back");
        this.buttonList.add(backButton);

        // IMPORTANT:
        // In picker mode (creating a new jump context), this screen is ONLY for picking a server.
        // Hide Create/Edit/Remove completely.
        if (!isPickerMode) {
            createButton = new GuiButton(1, this.width / 2 - 154, this.height - 44, 100, 20, "Create");
            editButton = new GuiButton(2, this.width / 2 - 50, this.height - 44, 100, 20, "Edit");
            removeButton = new GuiButton(3, this.width / 2 + 54, this.height - 44, 100, 20, "Remove");

            this.buttonList.add(createButton);
            this.buttonList.add(editButton);
            this.buttonList.add(removeButton);
        } else {
            createButton = null;
            editButton = null;
            removeButton = null;
        }

        updateButtonStates();
    }

    private void refreshData() {
        servers = new ArrayList<ServerProfile>(ReminderManager.getServers());

        // IMPORTANT:
        // When this screen is used as a "picker" (e.g., creating a new jump),
        // we must not allow selecting RestoredStrats.
        boolean isPicker = (pickHandler != null);

        if (isPicker && !allowRestoredInPicker && servers != null) {
            for (int i = servers.size() - 1; i >= 0; i--) {
                ServerProfile s = servers.get(i);
                if (s != null && ReminderManager.isRestoredServer(s)) {
                    servers.remove(i);
                }
            }
        }
    }

    private void updateButtonStates() {
        boolean isPickerMode = (pickHandler != null);

        // Picker mode: no management buttons exist.
        if (isPickerMode) {
            return;
        }

        ServerProfile sel = (list != null) ? list.getSelectedItem() : null;
        boolean hasSelection = sel != null;

        boolean isProtected = (sel != null && ReminderManager.isProtectedServer(sel));

        if (editButton != null) editButton.enabled = hasSelection && !isProtected;
        if (removeButton != null) removeButton.enabled = hasSelection && !isProtected;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();

        String title = "Servers";
        this.drawCenteredString(this.fontRendererObj, title, this.width / 2, 12, 0xFFFFFF);

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
        if (button == null) {
            return;
        }

        if (button.id == 4) {
            Minecraft.getMinecraft().displayGuiScreen(parent);
            return;
        }

        // Picker mode: no Create/Edit/Remove actions.
        if (pickHandler != null) {
            return;
        }

        if (button.id == 1) {
            Minecraft.getMinecraft().displayGuiScreen(new GuiTextPrompt(
                    this,
                    "Create server",
                    "",
                    new GuiTextPrompt.IResultHandler() {
                        @Override
                        public void onConfirm(String text) {
                            ReminderManager.createServer(text);
                            initGui();
                        }

                        @Override
                        public void onCancel() { }
                    }
            ));
            return;
        }

        ServerProfile selected = list.getSelectedItem();
        if (selected == null) {
            return;
        }

        // Protect Global and RestoredStrats from edit/remove
        if (ReminderManager.isProtectedServer(selected)) {
            return;
        }

        if (button.id == 2) {
            Minecraft.getMinecraft().displayGuiScreen(new GuiTextPrompt(
                    this,
                    "Rename server",
                    selected.getId(),
                    new GuiTextPrompt.IResultHandler() {
                        @Override
                        public void onConfirm(String text) {
                            ReminderManager.renameServer(selected, text);
                            initGui();
                        }

                        @Override
                        public void onCancel() { }
                    }
            ));
            return;
        }

        if (button.id == 3) {
            Minecraft.getMinecraft().displayGuiScreen(new GuiConfirm(
                    this,
                    "Remove server",
                    "Are you sure?",
                    new GuiConfirm.IConfirmHandler() {
                        @Override
                        public void onYes() {
                            ReminderManager.removeServer(selected);
                            initGui();
                        }

                        @Override
                        public void onNo() { }
                    }
            ));
        }
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