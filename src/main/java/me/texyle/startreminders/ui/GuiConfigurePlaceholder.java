package me.texyle.startreminders.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import me.texyle.startreminders.data.ParkourMap;
import me.texyle.startreminders.reminders.SelectionState;
import me.texyle.startreminders.sheets.PlaceholderSyncService;

/**
 * Links a public Google Sheet whose jump names (and sections) feed the "new strategy" dialog,
 * so jumps can be created in the order of the sheet.
 */
public class GuiConfigurePlaceholder extends UiScreen {

    private UiTextField urlField;
    private UiButton syncButton;
    private UiButton stopButton;
    private UiLabel status;

    private String statusText = "";
    private int statusColor = UiTheme.TEXT_MUTED;

    public GuiConfigurePlaceholder(GuiScreen parent) {
        super(parent);
    }

    @Override
    protected String getTitle() {
        return "Placeholder sheet";
    }

    @Override
    protected int getPanelWidth() {
        return 420;
    }

    @Override
    protected int getPanelHeight() {
        return 170;
    }

    @Override
    protected void build(int x, int y, int w, int h) {
        ParkourMap map = SelectionState.getSelectedMap();

        String current = urlField != null ? urlField.getText()
                : (map != null && map.getPlaceholderSheetUrl() != null ? map.getPlaceholderSheetUrl() : "");

        urlField = add(new UiTextField(current, 512).placeholder("Link to a public Google Sheet"), x, y + 4, w, 20);
        urlField.setFocused(true);

        status = add(new UiLabel(() -> statusText, UiTheme.TEXT_MUTED), x, y + 34, w, 10);

        stopButton = new UiButton("Stop sync", UiButton.Style.SECONDARY, this::stopSync);
        syncButton = new UiButton("Sync jumps", UiButton.Style.PRIMARY, this::sync);
        addFooter(stopButton, syncButton);
    }

    @Override
    protected void drawContent(int mouseX, int mouseY, float partialTicks) {
        ParkourMap map = SelectionState.getSelectedMap();
        boolean hasUrl = urlField.getText().trim().length() > 0;

        syncButton.enabled = map != null && hasUrl && !PlaceholderSyncService.isAnySheetSyncInProgress();
        stopButton.enabled = map != null && map.isPlaceholderSyncEnabled();
        status.setColor(statusColor);
    }

    private void sync() {
        ParkourMap map = SelectionState.getSelectedMap();
        if (map == null) {
            setStatus("Select a map first.", UiTheme.DANGER_HOVER);
            return;
        }

        String url = urlField.getText().trim();
        if (url.isEmpty()) {
            setStatus("URL is required.", UiTheme.DANGER_HOVER);
            return;
        }

        setStatus("Downloading...", UiTheme.TEXT_MUTED);

        // Persist URL + enable mode immediately, the list arrives asynchronously
        PlaceholderSyncService.enablePlaceholderSyncForMap(map, url);

        PlaceholderSyncService.requestPlaceholderJumpListSync(map, new PlaceholderSyncService.IPlaceholderJumpSyncCallback() {
            @Override
            public void onSuccess(int count) {
                // Return to the parent so the list can be used right away
                Minecraft.getMinecraft().displayGuiScreen(parent);
            }

            @Override
            public void onError(String errorMessage) {
                setStatus(errorMessage, UiTheme.DANGER_HOVER);
            }
        });
    }

    private void stopSync() {
        ParkourMap map = SelectionState.getSelectedMap();
        if (map != null) {
            PlaceholderSyncService.disablePlaceholderSyncForMap(map);
        }
        Minecraft.getMinecraft().displayGuiScreen(parent);
    }

    private void setStatus(String text, int color) {
        statusText = text != null ? text : "";
        statusColor = color;
    }
}
