package me.texyle.startreminders.gui.hierarchy;

import java.io.IOException;
import java.util.ArrayList;

import me.texyle.startreminders.data.MapSection;
import me.texyle.startreminders.data.ParkourMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

public class GuiPickSectionPreview extends GuiScreen {

    public interface ISectionPickHandler {
        void onPick(int levelOneBased, MapSection section);
    }

    private static final int BTN_BACK = 1;

    private static final int BTN_TAB_1 = 10;
    private static final int BTN_TAB_2 = 11;
    private static final int BTN_TAB_3 = 12;
    private static final int BTN_TAB_4 = 13;

    private static final int BTN_SELECT = 20;
    private static final int BTN_UNSELECT = 21;

    private final GuiScreen parent;
    private final ParkourMap map;
    private final ISectionPickHandler onPick;

    private int activeLevel = 1; // 1..4

    private GuiButton backButton;

    private GuiButton tab1;
    private GuiButton tab2;
    private GuiButton tab3;
    private GuiButton tab4;

    private GuiButton selectButton;
    private GuiButton unselectButton;

    private GuiPickSectionListSlot list;

    // Visual layout (match hierarchy style like GuiConfigureSections)
    private int panelLeft;
    private int panelRight;
    private int panelTop;
    private int panelBottom;

    private int headerTop;
    private int headerBottom;

    // Colors (ARGB)
    private static final int COLOR_PANEL_BG = 0xAA0B0B0B;
    private static final int COLOR_PANEL_BORDER = 0xCC2A2A2A;
    private static final int COLOR_HEADER_BG = 0xFF4A4A4A;
    private static final int COLOR_GRID = 0x662A2A2A;

    public GuiPickSectionPreview(GuiScreen parent, ParkourMap map, ISectionPickHandler onPick) {
        this.parent = parent;
        this.map = map;
        this.onPick = onPick;
    }

    @Override
    public void initGui() {
        super.initGui();

        this.buttonList.clear();

        int centerX = this.width / 2;

        // Panel geometry
        panelLeft = 10;
        panelRight = this.width - 10;
        panelTop = 34;
        panelBottom = this.height - 54;

        headerTop = panelTop + 6;
        headerBottom = headerTop + 16;

        backButton = new GuiButton(BTN_BACK, 8, 8, 60, 20, "Back");
        this.buttonList.add(backButton);

        int tabsY = 8;
        int tabW = 90;
        int tabH = 20;
        int gap = 6;

        int startX = 8 + 60 + 6; // to the right of "Back"

        tab1 = new GuiButton(BTN_TAB_1, startX + (tabW + gap) * 0, tabsY, tabW, tabH, "Section I");
        tab2 = new GuiButton(BTN_TAB_2, startX + (tabW + gap) * 1, tabsY, tabW, tabH, "Section II");
        tab3 = new GuiButton(BTN_TAB_3, startX + (tabW + gap) * 2, tabsY, tabW, tabH, "Section III");
        tab4 = new GuiButton(BTN_TAB_4, startX + (tabW + gap) * 3, tabsY, tabW, tabH, "Section IV");

        this.buttonList.add(tab1);
        this.buttonList.add(tab2);
        this.buttonList.add(tab3);
        this.buttonList.add(tab4);

        // Bottom buttons: Select + Unselect
        int bottomY = this.height - 44;
        int btnW = 140;
        int btnH = 20;
        int btnGap = 6;

        int totalW = btnW * 2 + btnGap;
        int startBottomX = centerX - (totalW / 2);

        selectButton = new GuiButton(BTN_SELECT, startBottomX, bottomY, btnW, btnH, "Select");
        unselectButton = new GuiButton(BTN_UNSELECT, startBottomX + btnW + btnGap, bottomY, btnW, btnH, "Unselect");

        this.buttonList.add(selectButton);
        this.buttonList.add(unselectButton);

        // List area inside panel
        int listTop = headerBottom + 4;
        int listBottom = panelBottom - 12;

        list = new GuiPickSectionListSlot(
                Minecraft.getMinecraft(),
                this.width,
                this.height,
                listTop,
                listBottom,
                24,
                this
        );

        clampActiveLevel();
        list.refresh(getCurrentList());

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

        tab1.enabled = (activeLevel != 1);
        tab2.enabled = (max >= 2) && (activeLevel != 2);
        tab3.enabled = (max >= 3) && (activeLevel != 3);
        tab4.enabled = (max >= 4) && (activeLevel != 4);

        tab2.visible = (max >= 2);
        tab3.visible = (max >= 3);
        tab4.visible = (max >= 4);
    }

    private void updateButtons() {
        boolean hasSelection = (list != null && list.getSelected() != null);

        if (selectButton != null) {
            selectButton.enabled = hasSelection;
        }

        // Unselect is always available (field is optional).
        if (unselectButton != null) {
            unselectButton.enabled = true;
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // Do NOT call drawDefaultBackground()
        drawRect(0, 0, this.width, this.height, 0xFF101010);

        drawPanel();

        if (list != null) {
            list.drawScreen(mouseX, mouseY, partialTicks);
        }

        updateButtons();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawPanel() {
        // Panel background
        drawRect(panelLeft, panelTop, panelRight, panelBottom, COLOR_PANEL_BG);

        // Left/right borders only
        drawRect(panelLeft, panelTop, panelLeft + 1, panelBottom, COLOR_PANEL_BORDER);
        drawRect(panelRight - 1, panelTop, panelRight, panelBottom, COLOR_PANEL_BORDER);

        // Header background
        drawRect(panelLeft + 1, headerTop - 2, panelRight - 1, headerBottom + 2, COLOR_HEADER_BG);

        // Separator under header
        drawRect(panelLeft + 1, headerBottom + 2, panelRight - 1, headerBottom + 3, COLOR_GRID);

        String hdr = "Pick: Section " + toRoman(activeLevel);
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

        if (button.id == BTN_TAB_1) { activeLevel = 1; onLevelChanged(); return; }
        if (button.id == BTN_TAB_2) { activeLevel = 2; onLevelChanged(); return; }
        if (button.id == BTN_TAB_3) { activeLevel = 3; onLevelChanged(); return; }
        if (button.id == BTN_TAB_4) { activeLevel = 4; onLevelChanged(); return; }

        if (button.id == BTN_SELECT) {
            MapSection sel = (list != null) ? list.getSelected() : null;
            if (sel == null) return;

            if (onPick != null) {
                onPick.onPick(activeLevel, sel);
            }

            Minecraft.getMinecraft().displayGuiScreen(parent);
            return;
        }

        if (button.id == BTN_UNSELECT) {
            if (onPick != null) {
                onPick.onPick(0, null);
            }
            Minecraft.getMinecraft().displayGuiScreen(parent);
            return;
        }
    }

    private void onLevelChanged() {
        clampActiveLevel();
        updateTabStates();

        if (list != null) {
            list.refresh(getCurrentList());
            list.setSelectedIndex(-1);
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