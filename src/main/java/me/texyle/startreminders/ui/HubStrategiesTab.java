package me.texyle.startreminders.ui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

import me.texyle.startreminders.data.Jump;
import me.texyle.startreminders.data.ParkourMap;
import me.texyle.startreminders.data.ServerProfile;
import me.texyle.startreminders.gui.hierarchy.GuiConfirm;
import me.texyle.startreminders.gui.hierarchy.GuiTextPrompt;
import me.texyle.startreminders.importing.BetterLinkCraftImporter;
import me.texyle.startreminders.importing.LegacyModImporter;
import me.texyle.startreminders.persistence.BuiltinContainers;
import me.texyle.startreminders.reminders.SelectionState;
import me.texyle.startreminders.services.JumpService;
import me.texyle.startreminders.services.MapService;
import me.texyle.startreminders.services.SectionLayoutService;
import me.texyle.startreminders.services.ServerService;
import me.texyle.startreminders.ui.GuiTransferJump;
import me.texyle.startreminders.ui.UiPopup.Entry;

/**
 * Browse the jumps of a map. Server and map are picked with two dropdowns instead of separate screens,
 * the table can be searched, and the actions on the selected jump live in one "More" menu.
 */
final class HubStrategiesTab implements HubTab {

    private static final int TOOLBAR_H = UiTheme.CONTROL_HEIGHT;
    private static final int FOOTER_H = 24;
    private static final int GAP = 8;

    private final GuiHub hub;
    private final UiPopup popup;

    private ServerProfile server;
    private ParkourMap map;

    private final UiDropdown serverDropdown;
    private final UiDropdown mapDropdown;
    private final UiButton newButton;
    private final UiButton editButton;
    private final UiButton moreButton;
    private final List<UiWidget> widgets = new ArrayList<UiWidget>();

    private int x;
    private int y;
    private int w;
    private int h;

    private final UiJumpTable table = new UiJumpTable();
    private final UiTextField searchField = new UiTextField("", 60).placeholder("Search jumps...");

    HubStrategiesTab(GuiHub hub, UiPopup popup) {
        this.hub = hub;
        this.popup = popup;

        serverDropdown = new UiDropdown(popup, "Server", this::serverLabel, this::serverEntries);
        mapDropdown = new UiDropdown(popup, "Map", this::mapLabel, this::mapEntries);

        newButton = new UiButton("+ New strategy", UiButton.Style.PRIMARY, this::onPrimaryAction);

        editButton = new UiButton("Edit", UiButton.Style.SECONDARY, this::editOrConvertSelected);
        moreButton = new UiButton("More", UiButton.Style.GHOST, this::openJumpMenu);

        table.setOnActivate(jump -> editOrConvertSelected());
        searchField.onChange(table::setFilter);

        widgets.add(serverDropdown);
        widgets.add(mapDropdown);
        widgets.add(searchField);
        widgets.add(newButton);
        widgets.add(editButton);
        widgets.add(moreButton);
    }

    // -------------------------
    // Context (server + map)
    // -------------------------

    /** Makes sure a valid server and map are selected, preferring what the rest of the mod selected. */
    private void syncFromSelection() {
        server = SelectionState.getSelectedServer();
        map = SelectionState.getSelectedMap();

        if (server == null) {
            List<ServerProfile> servers = ServerService.getServers();
            server = servers.isEmpty() ? null : servers.get(0);
            map = null;
        }

        if (server != null) {
            ParkourMap forced = BuiltinContainers.getForcedMap(server);
            if (forced != null) {
                map = forced;
            } else {
                List<ParkourMap> maps = MapService.getMaps(server);
                if (map == null || !maps.contains(map)) {
                    map = maps.isEmpty() ? null : maps.get(0);
                }
            }
        }

        SelectionState.setSelectedServer(server);
        SelectionState.setSelectedMap(map);
    }

    private void chooseServer(ServerProfile chosen) {
        SelectionState.setSelectedServer(chosen);
        SelectionState.setSelectedMap(null);
        SelectionState.setSelectedJump(null);
        syncFromSelection();
        rebuildTable();
        table.resetScroll();
    }

    private void chooseMap(ParkourMap chosen) {
        SelectionState.setSelectedMap(chosen);
        SelectionState.setSelectedJump(null);
        syncFromSelection();
        rebuildTable();
        table.resetScroll();
    }

    private boolean isProtectedServer() {
        return BuiltinContainers.isProtectedServer(server);
    }

    private boolean isRestoredMap() {
        return BuiltinContainers.isRestoredMap(map);
    }

    private String serverLabel() {
        return server != null ? server.getId() : null;
    }

    private String mapLabel() {
        return map != null ? map.getId() : null;
    }

    private List<Entry> serverEntries() {
        List<Entry> entries = new ArrayList<Entry>();
        for (final ServerProfile s : ServerService.getServers()) {
            entries.add(Entry.choice(String.valueOf(s.getId()), s == server, () -> chooseServer(s)));
        }
        entries.add(Entry.afterSeparator("Manage servers...", true, () -> hub.showTab(GuiHub.TAB_LIBRARY)));
        return entries;
    }

    private List<Entry> mapEntries() {
        List<Entry> entries = new ArrayList<Entry>();
        if (server != null) {
            for (final ParkourMap m : MapService.getMaps(server)) {
                entries.add(Entry.choice(String.valueOf(m.getId()), m == map, () -> chooseMap(m)));
            }
            entries.add(Entry.afterSeparator("Manage maps...", true, () -> hub.showTab(GuiHub.TAB_LIBRARY)));
        }
        return entries;
    }

    @Override
    public void onShow() {
        table.clearZoom();
        syncFromSelection();
        rebuildTable();
    }

    // -------------------------
    // Layout
    // -------------------------

    @Override
    public void layout(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;

        syncFromSelection();

        serverDropdown.setBounds(x, y, 170, TOOLBAR_H);
        mapDropdown.setBounds(x + 170 + GAP, y, 190, TOOLBAR_H);

        placePrimaryButton();

        int searchX = mapDropdown.x + mapDropdown.w + GAP;
        int searchW = Math.min(220, newButton.x - GAP - searchX);
        searchField.visible = searchW >= 90;
        searchField.setBounds(searchX, y, Math.max(searchW, 0), TOOLBAR_H);

        int footerY = y + h - FOOTER_H + 2;
        moreButton.setBounds(x + w - 60, footerY, 60, TOOLBAR_H);
        editButton.setBounds(moreButton.x - GAP - 70, footerY, 70, TOOLBAR_H);

        int tableTop = y + TOOLBAR_H + GAP;
        table.setBounds(x, tableTop, w, h - TOOLBAR_H - GAP - FOOTER_H - GAP);

        rebuildTable();
    }

    /** Points the table at the current map, keeping its scroll position and the selected jump. */
    private void rebuildTable() {
        table.setMap(map);

        Jump selected = SelectionState.getSelectedJump();
        if (selected != null && table.isShowing(selected)) {
            table.selectJump(selected);
            table.ensureSelectedVisible();
        } else if (table.getSelectedJump() != null && !table.isShowing(table.getSelectedJump())) {
            table.selectJump(null);
        }
    }

    // -------------------------
    // Actions
    // -------------------------

    private Jump selectedJump() {
        return table.getSelectedJump();
    }

    /** In RestoredStrats the old format is converted instead of edited. */
    private void editOrConvertSelected() {
        Jump jump = selectedJump();
        if (jump == null) return;
        if (isRestoredMap()) {
            convertToGlobal(jump);
        } else {
            editSelected();
        }
    }

    private void editSelected() {
        Jump jump = selectedJump();
        if (jump == null || map == null) return;

        SelectionState.setSelectedServer(server);
        SelectionState.setSelectedMap(map);
        SelectionState.setSelectedJump(jump);

        hub.open(GuiStrategyEditor.forEdit(hub, hub));
    }

    /** "+ New strategy" normally; in RestoredStrats the one useful action is importing the old files. */
    private void onPrimaryAction() {
        if (isRestoredMap()) {
            // RestoredStrats: run BOTH imports (legacy + BetterLinkCraft) each time
            boolean importedLegacy = LegacyModImporter.importIntoRestoredStrats();
            boolean importedBlc = BetterLinkCraftImporter.importIntoRestoredStrats();
            if (importedLegacy || importedBlc) {
                rebuildTable();
            }
            return;
        }
        hub.open(new GuiNewStrategy(hub, hub));
    }

    private void placePrimaryButton() {
        newButton.setLabel(isRestoredMap() ? "Import" : "+ New strategy");
        editButton.setLabel(isRestoredMap() ? "Convert" : "Edit");
        int newW = newButton.preferredWidth();
        newButton.setBounds(x + w - newW, y, newW, TOOLBAR_H);
    }

    /** Legacy (RestoredStrats) only: ask for a new name, then open the convert dialog. */
    private void convertToGlobal(final Jump jump) {
        hub.open(new GuiTextPrompt(hub, "Convert to Global: choose a jump name", "", new GuiTextPrompt.IResultHandler() {
            @Override
            public void onConfirm(String text) {
                String name = (text != null) ? text.trim() : "";
                if (!name.isEmpty()) {
                    Minecraft.getMinecraft().displayGuiScreen(new GuiConvertLegacy(hub, jump, name));
                }
            }

            @Override
            public void onCancel() { }
        }));
    }

    private void openJumpMenu() {
        final Jump jump = selectedJump();
        if (jump == null || map == null) return;

        boolean restored = isRestoredMap();
        int index = map.getJumps() != null ? indexOf(map.getJumps(), jump) : -1;
        int size = map.getJumps() != null ? map.getJumps().size() : 0;

        List<Entry> entries = new ArrayList<Entry>();
        entries.add(Entry.item("Edit strategy...", this::editSelected));
        entries.add(Entry.item("Rename...", !restored, () -> renameJump(jump)));
        entries.add(Entry.item("Move up", !restored && index > 0, () -> moveJump(jump, -1)));
        entries.add(Entry.item("Move down", !restored && index >= 0 && index < size - 1, () -> moveJump(jump, 1)));
        if (restored) {
            entries.add(Entry.item("Convert to Global...", () -> convertToGlobal(jump)));
        } else {
            entries.add(Entry.item("Transfer to...", () -> hub.open(new GuiTransferJump(hub, map, jump))));
        }
        entries.add(Entry.danger("Remove...", true, () -> removeJump(jump)));

        popup.open(moreButton.x + moreButton.w - 180, moreButton.y - entries.size() * 18 - 12, 180, entries);
    }

    private static int indexOf(List<Jump> list, Jump jump) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) == jump) return i;
        }
        return -1;
    }

    private void renameJump(final Jump jump) {
        hub.open(new GuiTextPrompt(hub, "Edit jump name", jump.getId() != null ? jump.getId() : "",
                new GuiTextPrompt.IResultHandler() {
                    @Override
                    public void onConfirm(String text) {
                        JumpService.renameJump(map, jump, text);
                        SelectionState.setSelectedJump(jump);
                        Minecraft.getMinecraft().displayGuiScreen(hub);
                    }

                    @Override
                    public void onCancel() { }
                }));
    }

    private void moveJump(Jump jump, int delta) {
        if (SectionLayoutService.moveJumpByOneRespectingSections(map, jump, delta)) {
            rebuildTable();
            table.selectJump(jump);
            table.ensureSelectedVisible();
        }
    }

    private void removeJump(final Jump jump) {
        hub.open(new GuiConfirm(hub, "Remove jump", "Are you sure?", new GuiConfirm.IConfirmHandler() {
            @Override
            public void onYes() {
                JumpService.removeJump(map, jump);
            }

            @Override
            public void onNo() { }
        }));
    }

    // -------------------------
    // Drawing / input
    // -------------------------

    @Override
    public void draw(int mouseX, int mouseY, float partialTicks) {
        boolean protectedServer = isProtectedServer();
        mapDropdown.enabled = server != null && !protectedServer;
        placePrimaryButton();

        Jump selected = selectedJump();
        editButton.enabled = selected != null;
        moreButton.enabled = selected != null;

        for (UiWidget widget : widgets) {
            widget.draw(mouseX, mouseY);
        }

        drawTable(mouseX, mouseY);
        drawFooter(selected);
    }

    private void drawTable(int mouseX, int mouseY) {
        String empty = "";
        String hint = "";
        boolean hasJumps = map != null && map.getJumps() != null && !map.getJumps().isEmpty();

        if (map == null) {
            empty = "No map selected";
        } else if (!hasJumps) {
            empty = "No jumps in this map yet";
            hint = isRestoredMap() ? "Use Import to bring in your old strategies" : "Use + New strategy to add the first one";
        } else {
            empty = "No jumps match your search";
        }
        table.setEmptyText(empty);
        table.draw(mouseX, mouseY);

        if (!hint.isEmpty()) {
            int cx = table.x + table.w / 2;
            int cy = table.y + 21 + (table.h - 22) / 2;
            UiDraw.centered(hint, cx, cy + 8, UiTheme.TEXT_DISABLED);
        }
    }

    private void drawFooter(Jump selected) {
        int textY = y + h - FOOTER_H + 2 + (UiTheme.CONTROL_HEIGHT - UiDraw.font().FONT_HEIGHT) / 2 + 1;

        if (selected == null) {
            UiDraw.text("Select a jump. Double-click or Enter opens its strategy.", x + 2, textY, UiTheme.TEXT_MUTED);
            return;
        }

        int strategies = selected.getReminders() != null ? selected.getReminders().size() : 0;
        String name = selected.getId() != null ? selected.getId() : "<unnamed>";
        String info = name + "  -  " + strategies + (strategies == 1 ? " strategy" : " strategies");
        UiDraw.text(UiDraw.fit(info, editButton.x - x - 16), x + 2, textY, UiTheme.TEXT);
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (!searchField.contains(mouseX, mouseY)) {
            searchField.setFocused(false);
        }

        for (UiWidget widget : widgets) {
            if (widget.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return table.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseWheel(int mouseX, int mouseY, int delta) {
        return table.mouseWheel(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        boolean ctrl = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
        if (ctrl && keyCode == Keyboard.KEY_F && searchField.visible) {
            searchField.setFocused(true);
            return true;
        }

        // Zoom shortcuts for the table: Ctrl + / Ctrl - and Ctrl 0 to reset (Ctrl + wheel zooms at the cursor).
        if (ctrl && !searchField.isFocused()) {
            if (keyCode == Keyboard.KEY_EQUALS || keyCode == Keyboard.KEY_ADD) {
                table.zoomCentered(1);
                return true;
            }
            if (keyCode == Keyboard.KEY_MINUS || keyCode == Keyboard.KEY_SUBTRACT) {
                table.zoomCentered(-1);
                return true;
            }
            if (keyCode == Keyboard.KEY_0 || keyCode == Keyboard.KEY_NUMPAD0) {
                table.resetZoom();
                return true;
            }
        }

        if (searchField.isFocused()) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                // First Escape clears the search, a second one closes the window.
                boolean hadText = !searchField.getText().isEmpty();
                searchField.setFocused(false);
                if (hadText) {
                    searchField.setText("");
                    table.setFilter("");
                }
                return hadText;
            }
            if (keyCode == Keyboard.KEY_DOWN || keyCode == Keyboard.KEY_RETURN) {
                searchField.setFocused(false);
                table.selectRelative(1);
                return true;
            }
            return searchField.keyTyped(typedChar, keyCode);
        }

        return table.keyTyped(keyCode);
    }

    @Override
    public void update() {
        searchField.update();
    }
}
