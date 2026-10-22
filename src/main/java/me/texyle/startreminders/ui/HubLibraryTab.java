package me.texyle.startreminders.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.texyle.startreminders.data.ParkourMap;
import me.texyle.startreminders.data.ServerProfile;
import me.texyle.startreminders.gui.hierarchy.GuiConfirm;
import me.texyle.startreminders.gui.hierarchy.GuiCreateMap;
import me.texyle.startreminders.gui.hierarchy.GuiEditMap;
import me.texyle.startreminders.gui.hierarchy.GuiTextPrompt;
import me.texyle.startreminders.persistence.BuiltinContainers;
import me.texyle.startreminders.reminders.SelectionState;
import me.texyle.startreminders.services.MapService;
import me.texyle.startreminders.services.ServerService;
import me.texyle.startreminders.sheets.SheetImportService;
import me.texyle.startreminders.template.TemplateMode;
import me.texyle.startreminders.ui.UiPopup.Entry;

/**
 * Where servers and their maps are managed: two lists, with everything that changes them
 * (rename, remove, sheet sync, templates) in menus instead of rows of buttons.
 */
final class HubLibraryTab implements HubTab {

    private static final int GAP = 12;
    private static final int HEADER_H = 22;
    private static final int INFO_H = 22;


    private final GuiHub hub;
    private final UiPopup popup;

    private final UiList<ServerProfile> serverList;
    private final UiList<ParkourMap> mapList;

    private final UiButton newServerButton;
    private final UiButton serverMenuButton;
    private final UiButton newMapButton;
    private final UiButton mapMenuButton;
    private final List<UiWidget> widgets = new ArrayList<UiWidget>();

    private int x;
    private int y;
    private int w;
    private int h;
    private int leftW;

    HubLibraryTab(GuiHub hub, UiPopup popup) {
        this.hub = hub;
        this.popup = popup;

        serverList = new UiList<ServerProfile>(ServerService::getServers, s -> String.valueOf(s.getId()))
                .emptyText("No servers")
                .renderer(this::drawServerRow)
                .onSelect(this::onServerSelected);

        mapList = new UiList<ParkourMap>(this::currentMaps, m -> String.valueOf(m.getId()))
                .emptyText("No maps")
                .renderer(this::drawMapRow)
                .onActivate(this::openMap);

        newServerButton = new UiButton("+ New", UiButton.Style.SECONDARY, this::createServer);
        serverMenuButton = new UiButton("...", UiButton.Style.SECONDARY, this::openServerMenu);
        newMapButton = new UiButton("+ New map", UiButton.Style.PRIMARY, this::createMap);
        mapMenuButton = new UiButton("...", UiButton.Style.SECONDARY, this::openMapMenu);

        widgets.add(serverList);
        widgets.add(mapList);
        widgets.add(newServerButton);
        widgets.add(serverMenuButton);
        widgets.add(newMapButton);
        widgets.add(mapMenuButton);
    }

    // -------------------------
    // Data
    // -------------------------

    private ServerProfile currentServer() {
        return serverList.getSelected();
    }

    private ParkourMap currentMap() {
        return mapList.getSelected();
    }

    private List<ParkourMap> currentMaps() {
        ServerProfile server = currentServer();
        if (server == null) return Collections.emptyList();

        ParkourMap forced = BuiltinContainers.getForcedMap(server);
        if (forced != null) return Collections.singletonList(forced);

        return MapService.getMaps(server);
    }

    private boolean serverIsProtected() {
        return BuiltinContainers.isProtectedServer(currentServer());
    }

    private void onServerSelected(ServerProfile server) {
        mapList.clearSelection();
        SelectionState.setSelectedServer(server);
    }

    private void openMap(ParkourMap map) {
        ServerProfile server = currentServer();
        if (server == null || map == null) return;

        SelectionState.setSelectedServer(server);
        SelectionState.setSelectedMap(map);
        SelectionState.setSelectedJump(null);
        hub.showTab(GuiHub.TAB_STRATEGIES);
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

        leftW = Math.max(180, Math.min(280, (int) (w * 0.34)));
        int rightX = x + leftW + GAP;
        int rightW = w - leftW - GAP;

        int listTop = y + HEADER_H + 4;
        int listH = h - HEADER_H - 4 - INFO_H;

        serverList.setBounds(x, listTop, leftW, listH);
        mapList.setBounds(rightX, listTop, rightW, listH);

        serverMenuButton.setBounds(x + leftW - 28, y, 28, UiTheme.CONTROL_HEIGHT);
        newServerButton.setBounds(serverMenuButton.x - 6 - 56, y, 56, UiTheme.CONTROL_HEIGHT);

        mapMenuButton.setBounds(rightX + rightW - 28, y, 28, UiTheme.CONTROL_HEIGHT);
        int newMapW = newMapButton.preferredWidth();
        newMapButton.setBounds(mapMenuButton.x - 6 - newMapW, y, newMapW, UiTheme.CONTROL_HEIGHT);
    }

    @Override
    public void onShow() {
        ServerProfile selected = SelectionState.getSelectedServer();
        if (selected != null) {
            serverList.setSelected(selected);
        } else if (!ServerService.getServers().isEmpty()) {
            serverList.setSelected(ServerService.getServers().get(0));
        }

        ParkourMap map = SelectionState.getSelectedMap();
        if (map != null) {
            mapList.setSelected(map);
        }
    }

    // -------------------------
    // Drawing
    // -------------------------

    @Override
    public void draw(int mouseX, int mouseY, float partialTicks) {
        boolean protectedServer = serverIsProtected();
        ServerProfile server = currentServer();
        ParkourMap map = currentMap();

        newMapButton.enabled = server != null && !protectedServer;
        mapMenuButton.enabled = server != null && !protectedServer;
        serverMenuButton.enabled = server != null && !protectedServer;

        UiDraw.text("SERVERS", x, y + 7, UiTheme.TEXT_MUTED);
        if (server != null) {
            // "MAPS OF" muted, the server's name in its own color when it is Global / RestoredStrats.
            String prefix = "MAPS OF ";
            String name = UiDraw.fit(String.valueOf(server.getId()).toUpperCase(), mapList.w - 150 - UiDraw.width(prefix));
            int special = SpecialColors.of(server);
            UiDraw.text(prefix, mapList.x, y + 7, UiTheme.TEXT_MUTED);
            UiDraw.text(name, mapList.x + UiDraw.width(prefix), y + 7, special != 0 ? special : UiTheme.TEXT_MUTED);
        } else {
            UiDraw.text("MAPS", mapList.x, y + 7, UiTheme.TEXT_MUTED);
        }

        for (UiWidget widget : widgets) {
            widget.draw(mouseX, mouseY);
        }

        drawInfo(map, protectedServer);
    }

    private void drawInfo(ParkourMap map, boolean protectedServer) {
        int infoY = y + h - INFO_H + 8;

        if (protectedServer) {
            UiDraw.text("Global and RestoredStrats are built in and cannot be changed.", x, infoY, UiTheme.TEXT_MUTED);
            return;
        }
        if (map == null) {
            UiDraw.text("Double-click a map to open its jumps.", x, infoY, UiTheme.TEXT_MUTED);
            return;
        }

        String sheet;
        if (!map.hasSheetConfigured()) {
            sheet = "No sheet linked";
        } else if (map.getLastSheetSyncMs() <= 0L) {
            sheet = "Sheet linked, not synced yet";
        } else {
            sheet = "Sheet linked, synced " + ago(System.currentTimeMillis() - map.getLastSheetSyncMs());
        }

        int jumps = map.getJumps() != null ? map.getJumps().size() : 0;
        UiDraw.text(map.getId() + "  -  " + jumps + (jumps == 1 ? " jump" : " jumps") + "  -  " + sheet,
                x, infoY, UiTheme.TEXT);
    }

    private static String ago(long ms) {
        long minutes = Math.max(0L, ms / 60000L);
        if (minutes < 1) return "just now";
        if (minutes < 60) return minutes + " min ago";
        long hours = minutes / 60;
        if (hours < 48) return hours + " h ago";
        return (hours / 24) + " days ago";
    }

    private void drawServerRow(ServerProfile s, int rx, int ry, int rw, int rh, boolean selected) {
        int special = SpecialColors.of(s);
        int color = special != 0 ? special : UiTheme.TEXT;
        UiDraw.text(UiDraw.fit(String.valueOf(s.getId()), rw - 14), rx + 8, ry + (rh - 8) / 2, color);
    }

    private void drawMapRow(ParkourMap m, int rx, int ry, int rw, int rh, boolean selected) {
        int textY = ry + (rh - 8) / 2;
        String tag = m.hasSheetConfigured() ? "SHEET" : "";
        int tagW = tag.isEmpty() ? 0 : UiDraw.width(tag) + 12;

        int special = SpecialColors.of(m);
        UiDraw.text(UiDraw.fit(String.valueOf(m.getId()), rw - 14 - tagW), rx + 8, textY, special != 0 ? special : UiTheme.TEXT);
        if (!tag.isEmpty()) {
            UiDraw.text(tag, rx + rw - tagW, textY, selected ? UiTheme.TEXT : UiTheme.TEXT_MUTED);
        }
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        for (int i = widgets.size() - 1; i >= 0; i--) {
            if (widgets.get(i).mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseWheel(int mouseX, int mouseY, int delta) {
        return serverList.mouseWheel(mouseX, mouseY, delta) || mapList.mouseWheel(mouseX, mouseY, delta);
    }

    // -------------------------
    // Server actions
    // -------------------------

    private void createServer() {
        hub.open(new GuiTextPrompt(hub, "Create server", "", new GuiTextPrompt.IResultHandler() {
            @Override
            public void onConfirm(String text) {
                ServerService.createServer(text);
            }

            @Override
            public void onCancel() { }
        }));
    }

    private void openServerMenu() {
        final ServerProfile server = currentServer();
        if (server == null) return;

        boolean editable = !BuiltinContainers.isProtectedServer(server);
        List<Entry> entries = new ArrayList<Entry>();
        entries.add(Entry.item("Rename...", editable, () -> renameServer(server)));
        entries.add(Entry.danger("Remove...", editable, () -> removeServer(server)));

        popup.open(serverMenuButton.x + serverMenuButton.w - 170, serverMenuButton.y + serverMenuButton.h + 2, 170, entries);
    }

    private void renameServer(final ServerProfile server) {
        hub.open(new GuiTextPrompt(hub, "Rename server", server.getId(), new GuiTextPrompt.IResultHandler() {
            @Override
            public void onConfirm(String text) {
                ServerService.renameServer(server, text);
            }

            @Override
            public void onCancel() { }
        }));
    }

    private void removeServer(final ServerProfile server) {
        hub.open(new GuiConfirm(hub, "Remove server", "Remove \"" + server.getId() + "\" and all its maps?",
                new GuiConfirm.IConfirmHandler() {
                    @Override
                    public void onYes() {
                        ServerService.removeServer(server);
                        serverList.clearSelection();
                        mapList.clearSelection();
                    }

                    @Override
                    public void onNo() { }
                }));
    }

    // -------------------------
    // Map actions
    // -------------------------

    private void createMap() {
        ServerProfile server = currentServer();
        if (server != null) {
            hub.open(new GuiCreateMap(hub, server));
        }
    }

    private void openMapMenu() {
        final ServerProfile server = currentServer();
        final ParkourMap map = currentMap();
        if (server == null) return;

        boolean editable = !BuiltinContainers.isProtectedServer(server);
        List<Entry> entries = new ArrayList<Entry>();
        entries.add(Entry.item("Import sheet as new map...", editable, () -> importSheet(server)));
        if (map != null) {
            entries.add(new Entry("Template mode", editable, map.isTemplateMode(), true,
                    () -> TemplateMode.setEnabled(map, !map.isTemplateMode())));
            entries.add(Entry.item("Edit map...", editable, () -> hub.open(new GuiEditMap(hub, server, map))));
            entries.add(Entry.afterSeparator("Link sheet...", editable, () -> linkSheet(server, map)));
            entries.add(Entry.item("Sync now", editable && map.hasSheetConfigured(),
                    () -> SheetImportService.sync(server, map)));
            entries.add(Entry.item("Edit import filter...", editable && map.hasSheetConfigured(),
                    () -> SheetImportActions.editFilter(map)));
            entries.add(Entry.afterSeparator("Export template...", editable, () -> MapTemplateActions.exportMap(server, map)));
            entries.add(Entry.item("Import template...", editable, () -> MapTemplateActions.importInto(server, null)));
            entries.add(Entry.danger("Remove map...", editable, () -> removeMap(server, map)));
        }

        popup.open(mapMenuButton.x + mapMenuButton.w - 220, mapMenuButton.y + mapMenuButton.h + 2, 220, entries);
    }

    /** Asks for the sheet link and a name, then builds a new map from the sheet. */
    private void importSheet(final ServerProfile server) {
        hub.open(new GuiTextPrompt(hub, "Sheet URL", "",
                new GuiTextPrompt.IResultHandler() {
                    @Override
                    public void onConfirm(final String url) {
                        if (url == null || url.trim().isEmpty()) return;
                        hub.open(new GuiTextPrompt(hub, "Name of the new map", "", new GuiTextPrompt.IResultHandler() {
                            @Override
                            public void onConfirm(String name) {
                                SheetImportActions.importAsNewMap(server, name, url);
                            }

                            @Override
                            public void onCancel() { }
                        }));
                    }

                    @Override
                    public void onCancel() { }
                }));
    }

    private void linkSheet(final ServerProfile server, final ParkourMap map) {
        String current = map.getSheetUrl();
        hub.open(new GuiTextPrompt(hub, "Sheet URL for map: " + map.getId(), current != null ? current : "",
                new GuiTextPrompt.IResultHandler() {
                    @Override
                    public void onConfirm(String text) {
                        if (text == null || text.trim().isEmpty()) {
                            SheetImportService.stopSync(map);
                        } else {
                            SheetImportActions.linkSheet(map, text.trim());
                        }
                    }

                    @Override
                    public void onCancel() { }
                }));
    }

    private void removeMap(final ServerProfile server, final ParkourMap map) {
        hub.open(new GuiConfirm(hub, "Remove map", "Remove \"" + map.getId() + "\" and all its jumps?",
                new GuiConfirm.IConfirmHandler() {
                    @Override
                    public void onYes() {
                        MapService.removeMap(server, map);
                        mapList.clearSelection();
                    }

                    @Override
                    public void onNo() { }
                }));
    }
}
