package me.texyle.startreminders.gui.hierarchy;

import java.io.IOException;
import java.util.ArrayList;

import me.texyle.startreminders.data.MapSection;
import me.texyle.startreminders.data.ParkourMap;
import me.texyle.startreminders.reminders.ReminderManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

public class GuiConfigureSections extends GuiScreen {

    private static final int BTN_BACK = 1;

    private static final int BTN_TAB_1 = 10;
    private static final int BTN_TAB_2 = 11;
    private static final int BTN_TAB_3 = 12;
    private static final int BTN_TAB_4 = 13;

    private static final int BTN_CREATE = 20;
    private static final int BTN_EDIT = 21;
    private static final int BTN_DELETE = 22;

    private static final int BTN_SYNC_SECTIONS = 30;

    private final GuiScreen parent;
    private final ParkourMap map;

    private int activeLevel = 1; // 1..4

    private GuiButton backButton;

    private GuiButton tab1;
    private GuiButton tab2;
    private GuiButton tab3;
    private GuiButton tab4;

    private GuiButton createButton;
    private GuiButton editButton;
    private GuiButton deleteButton;

    private GuiButton syncSectionsButton;

    private GuiSectionListSlot list;

    // Visual layout (match hierarchy style like GuiJumpList)
    private int panelLeft;
    private int panelRight;
    private int panelTop;
    private int panelBottom;

    private int headerTop;
    private int headerBottom;

    // Colors (ARGB) - same as GuiJumpList
    private static final int COLOR_PANEL_BG = 0xAA0B0B0B;
    private static final int COLOR_PANEL_BORDER = 0xCC2A2A2A;
    private static final int COLOR_HEADER_BG = 0xFF4A4A4A;
    private static final int COLOR_GRID = 0x662A2A2A;

    public GuiConfigureSections(GuiScreen parent, ParkourMap map) {
        this.parent = parent;
        this.map = map;
    }

    // Package-private getters for list rendering (nested prefix feature).
    ParkourMap getMapForSectionList() {
        return map;
    }

    int getActiveLevelForSectionList() {
        return activeLevel;
    }

    @Override
    public void initGui() {
        super.initGui();

        this.buttonList.clear();

        // IMPORTANT: clamp FIRST so activeLevel is always valid before building UI and refreshing list.
        clampActiveLevel();

        int centerX = this.width / 2;

        // Base panel geometry (we may push panelTop down if Sync button moves to a 2nd row).
        panelLeft = 10;
        panelRight = this.width - 10;
        panelTop = 34; // default
        panelBottom = this.height - 54;

        backButton = new GuiButton(BTN_BACK, 8, 8, 60, 20, "Back");
        this.buttonList.add(backButton);

        int tabsY = 8;
        int tabW = 90;
        int tabH = 20;
        int gap = 6;

        int startX = 8 + 60 + 6; // just to the right of "Back"

        tab1 = new GuiButton(BTN_TAB_1, startX + (tabW + gap) * 0, tabsY, tabW, tabH, "Section I");
        tab2 = new GuiButton(BTN_TAB_2, startX + (tabW + gap) * 1, tabsY, tabW, tabH, "Section II");
        tab3 = new GuiButton(BTN_TAB_3, startX + (tabW + gap) * 2, tabsY, tabW, tabH, "Section III");
        tab4 = new GuiButton(BTN_TAB_4, startX + (tabW + gap) * 3, tabsY, tabW, tabH, "Section IV");

        this.buttonList.add(tab1);
        this.buttonList.add(tab2);
        this.buttonList.add(tab3);
        this.buttonList.add(tab4);

        // ------------------------------------------------------------
        // Sync Sections button (top-right).
        // If the window is too narrow and would overlap tabs, move it one row down.
        // IMPORTANT: if we move it down, we MUST also push the panelTop down,
        // otherwise the button overlaps the list/panel (the screenshot issue).
        // ------------------------------------------------------------
        int syncW = 120;
        int syncH = 20;
        int syncX = this.width - 8 - syncW;
        int syncY = 8;

        int tabsRightEdge = tab4.xPosition + tabW; // last tab edge (even if hidden later)

        if (syncX < (tabsRightEdge + gap)) {
            // Not enough space -> move below tabs, still right-aligned.
            syncY = 8 + tabH + 4;
        }

        syncSectionsButton = new GuiButton(BTN_SYNC_SECTIONS, syncX, syncY, syncW, syncH, "Sync Sections");
        syncSectionsButton.enabled = (map != null && map.getEffectiveSectionsCount() > 0);
        this.buttonList.add(syncSectionsButton);

        // If Sync button is on the 2nd row, push the panel down so it never overlaps controls.
        int controlsBottomY = Math.max(8 + tabH, syncY + syncH);
        panelTop = Math.max(panelTop, controlsBottomY + 6);

        // Header row geometry (inside panel)
        headerTop = panelTop + 6;
        headerBottom = headerTop + 16;

        // Bottom buttons
        int bottomY = this.height - 44;
        int btnW = 120;
        int btnH = 20;

        int totalW = btnW * 3 + gap * 2;
        int startBottomX = centerX - (totalW / 2);

        createButton = new GuiButton(BTN_CREATE, startBottomX, bottomY, btnW, btnH, "Create section");
        editButton = new GuiButton(BTN_EDIT, startBottomX + (btnW + gap), bottomY, btnW, btnH, "Edit section");
        deleteButton = new GuiButton(BTN_DELETE, startBottomX + (btnW + gap) * 2, bottomY, btnW, btnH, "Delete section");

        this.buttonList.add(createButton);
        this.buttonList.add(editButton);
        this.buttonList.add(deleteButton);

        // List area INSIDE panel (below header bar)
        int listTop = headerBottom + 4;
        int listBottom = panelBottom - 12;

        list = new GuiSectionListSlot(
                Minecraft.getMinecraft(),
                this.width,
                this.height,
                listTop,
                listBottom,
                24,
                this,
                map
        );

        // IMPORTANT: ensure list knows current level BEFORE refresh, otherwise nesting prefix disappears after returning from sub-screens.
        if (list != null) {
            list.setActiveLevel(activeLevel);
            list.refresh(getCurrentList());
        }

        updateTabStates();
        updateButtons();
    }

    private void clampActiveLevel() {
        int max = (map != null) ? map.getMaxSectionLevelsAllowed() : 0;
        if (max < 1) max = 1;
        if (activeLevel < 1) activeLevel = 1;
        if (activeLevel > max) activeLevel = max;
    }

    private ArrayList<MapSection> getCurrentList() {
        if (map == null) return new ArrayList<MapSection>();
        return map.getSectionsForLevel(activeLevel);
    }

    private void updateTabStates() {
        int max = (map != null) ? map.getMaxSectionLevelsAllowed() : 0;

        if (tab1 != null) tab1.enabled = (activeLevel != 1);
        if (tab2 != null) tab2.enabled = (max >= 2) && (activeLevel != 2);
        if (tab3 != null) tab3.enabled = (max >= 3) && (activeLevel != 3);
        if (tab4 != null) tab4.enabled = (max >= 4) && (activeLevel != 4);

        if (tab2 != null) tab2.visible = (max >= 2);
        if (tab3 != null) tab3.visible = (max >= 3);
        if (tab4 != null) tab4.visible = (max >= 4);
    }

    private void updateButtons() {
        boolean hasSelection = list != null && list.getSelected() != null;

        if (editButton != null) editButton.enabled = hasSelection;
        if (deleteButton != null) deleteButton.enabled = hasSelection;

        if (syncSectionsButton != null) {
            syncSectionsButton.enabled = (map != null && map.getEffectiveSectionsCount() > 0);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // IMPORTANT: do NOT call drawDefaultBackground() (it draws the dirt)
        drawRect(0, 0, this.width, this.height, 0xFF101010);

        drawPanel();

        if (list != null) {
            list.drawScreen(mouseX, mouseY, partialTicks);
        }

        updateButtons();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawPanel() {
        // Panel background fill
        drawRect(panelLeft, panelTop, panelRight, panelBottom, COLOR_PANEL_BG);

        // Keep left/right borders only (same style as GuiJumpList)
        drawRect(panelLeft, panelTop, panelLeft + 1, panelBottom, COLOR_PANEL_BORDER);
        drawRect(panelRight - 1, panelTop, panelRight, panelBottom, COLOR_PANEL_BORDER);

        // Header background
        drawRect(panelLeft + 1, headerTop - 2, panelRight - 1, headerBottom + 2, COLOR_HEADER_BG);

        // Subtle separator under header
        drawRect(panelLeft + 1, headerBottom + 2, panelRight - 1, headerBottom + 3, COLOR_GRID);

        // Header label (active tab)
        String hdr = "Editing: Section " + toRoman(activeLevel);
        this.fontRendererObj.drawString(hdr, panelLeft + 8, headerTop + 3, 0xFFFFFF, true);
    }

    private static String toRoman(int lvl) {
        if (lvl == 1) return "I";
        if (lvl == 2) return "II";
        if (lvl == 3) return "III";
        if (lvl == 4) return "IV";
        return "";
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        if (list != null) {
            list.handleMouseInput();
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button == null) return;

        if (button.id == BTN_BACK) {
            Minecraft.getMinecraft().displayGuiScreen(parent);
            return;
        }

        if (button.id == BTN_SYNC_SECTIONS) {
            if (map == null) return;

            Minecraft.getMinecraft().displayGuiScreen(new GuiTextPrompt(
                    this,
                    "Sync Sections: paste Spreadsheet URL",
                    map != null ? map.getSheetUrl() : "",
                    new GuiTextPrompt.IResultHandler() {
                        @Override
                        public void onConfirm(String text) {
                            String url = (text != null) ? text.trim() : "";

                            // Persist the URL (current design uses map.sheetUrl for both sheet sync and section sync)
                            if (map != null) {
                                map.setSheetUrl(url);
                            }

                            boolean ok = ReminderManager.syncSectionsFromAppsScript(map, url);

                            if (ok) {
                                sendClientChat_(EnumChatFormatting.DARK_AQUA + "[ParkourStrats] " + EnumChatFormatting.AQUA
                                        + "Sections synced from spreadsheet.");
                            } else {
                                // ReminderManager now posts a detailed error message to chat on failure.
                                // To avoid duplicate messages, we do not print the same error again here.
                                // As a safety fallback, show a generic line only if no error was provided.
                                String err = ReminderManager.getLastSectionSyncError();
                                if (err == null || err.trim().isEmpty()) {
                                    sendClientChat_(EnumChatFormatting.DARK_AQUA + "[ParkourStrats] " + EnumChatFormatting.RED
                                            + "Section sync failed.");
                                }
                            }

                            // Rebuild UI to reflect changes (and refresh list)
                            initGui();
                        }

                        @Override
                        public void onCancel() { }
                    }
            ));
            return;
        }

        if (button.id == BTN_TAB_1) { activeLevel = 1; onLevelChanged(); return; }
        if (button.id == BTN_TAB_2) { activeLevel = 2; onLevelChanged(); return; }
        if (button.id == BTN_TAB_3) { activeLevel = 3; onLevelChanged(); return; }
        if (button.id == BTN_TAB_4) { activeLevel = 4; onLevelChanged(); return; }

        if (button.id == BTN_CREATE) {
            Minecraft.getMinecraft().displayGuiScreen(new GuiEditSection(this, map, activeLevel, null));
            return;
        }

        if (button.id == BTN_EDIT) {
            MapSection sel = (list != null) ? list.getSelected() : null;
            if (sel == null) return;
            Minecraft.getMinecraft().displayGuiScreen(new GuiEditSection(this, map, activeLevel, sel));
            return;
        }

        if (button.id == BTN_DELETE) {
            final MapSection sel = (list != null) ? list.getSelected() : null;
            if (sel == null) return;

            String name = (sel.getName() != null) ? sel.getName().trim() : "";
            if (name.isEmpty()) name = "<unnamed>";

            final String msg = "Do you really want to delete section \"" + name + "\"?";

            // Show a confirmation dialog before deleting.
            Minecraft.getMinecraft().displayGuiScreen(new GuiConfirm(
                    this,
                    "Delete section",
                    msg,
                    new GuiConfirm.IConfirmHandler() {
                        @Override
                        public void onYes() {
                            deleteSection(sel);
                        }

                        @Override
                        public void onNo() {
                            // Do nothing; return to this screen.
                        }
                    }
            ));
            return;
        }
    }

    private void sendClientChat_(String msg) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null || msg == null) return;
        mc.thePlayer.addChatMessage(new ChatComponentText(msg));
    }

    private void deleteSection(MapSection sel) {
        if (sel == null) return;

        ArrayList<MapSection> lst = getCurrentList();
        if (lst == null) return;

        lst.remove(sel);

        ReminderManager.saveToFile();

        if (list != null) {
            list.setActiveLevel(activeLevel);
            list.refresh(getCurrentList());
        }
        updateButtons();
    }

    private void onLevelChanged() {
        clampActiveLevel();
        updateTabStates();

        if (list != null) {
            list.setActiveLevel(activeLevel);
            list.refresh(getCurrentList());
        }

        updateButtons();
    }

    public void onListSelectionChanged() {
        updateButtons();
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