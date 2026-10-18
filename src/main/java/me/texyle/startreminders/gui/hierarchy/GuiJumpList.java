package me.texyle.startreminders.gui.hierarchy;

import java.io.IOException;
import java.util.ArrayList;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

import me.texyle.startreminders.data.Jump;
import me.texyle.startreminders.data.ParkourMap;
import me.texyle.startreminders.gui.GuiConvertLegacy;
import me.texyle.startreminders.gui.GuiCreateReminder;
import me.texyle.startreminders.gui.GuiEditReminders;
import me.texyle.startreminders.gui.GuiScissor;
import me.texyle.startreminders.gui.GuiTransferJump;
import me.texyle.startreminders.importing.BetterLinkCraftImporter;
import me.texyle.startreminders.importing.LegacyModImporter;
import me.texyle.startreminders.persistence.BuiltinContainers;
import me.texyle.startreminders.reminders.SelectionState;
import me.texyle.startreminders.services.JumpService;
import me.texyle.startreminders.services.SectionLayoutService;

public class GuiJumpList extends GuiScreen {

    private final GuiScreen parent;
    private final ParkourMap map;

    private GuiJumpTableSlot table;
    private ArrayList<Jump> jumps;

    private GuiButton createButton;
    private GuiButton convertButton; // Legacy-only
    private GuiButton editNameButton;
    private GuiButton removeButton;
    private GuiButton backButton;
    private GuiButton transferButton;
    private GuiButton configureSectionsButton;

    private GuiButton moveUpButton;
    private GuiButton moveDownButton;

    private JumpTablePanel panel;

    // Colors (ARGB)
    private static final int COLOR_TITLE = 0xFFFFFF;

    // Button IDs
    private static final int BTN_CREATE = 1;
    private static final int BTN_CONVERT = 2; // Legacy-only
    private static final int BTN_REMOVE = 3;
    private static final int BTN_BACK = 4;

    private static final int BTN_MOVE_UP = 5;
    private static final int BTN_MOVE_DOWN = 6;

    private static final int BTN_EDIT_NAME = 7;
    private static final int BTN_TRANSFER = 8;
    private static final int BTN_CONFIGURE_SECTIONS = 9;

    // Persist scroll between initGui rebuilds (reset only when list is closed)
    private boolean hasSavedScroll = false;
    private int savedVScrollPx = 0;
    private double savedHScrollPx = 0.0;

    // Deferred GUI open (workaround for prompt that auto-returns to parent on confirm)
    private GuiScreen pendingScreen = null;

    // Debounce move actions (prevents multi-trigger causing jumps to fly to start/end)
    private long lastMoveMs = 0L;
    private Jump lastMoveJump = null;
    private int lastMoveDelta = 0;

    private static final long MOVE_DEBOUNCE_MS = 180L;

    private int pendingMoveDelta = 0; // -1, +1, or 0

    private final Jump initialSelection;

    public GuiJumpList(GuiScreen parent, ParkourMap map) {
        this(parent, map, null);
    }

    public GuiJumpList(GuiScreen parent, ParkourMap map, Jump initialSelection) {
        this.parent = parent;
        this.map = map;
        this.initialSelection = initialSelection;
    }

    @Override
    public void initGui() {
        super.initGui();

        refreshData();

        initTable();
        initButtons();

        updateButtonStates();

        // Prevent key repeat spam on this screen (UP/DOWN should be single-step per press)
        Keyboard.enableRepeatEvents(false);
    }

    /** Builds the panel and the jump table, restoring scroll and the preselected jump. */
    private void initTable() {
        panel = new JumpTablePanel(this.width, this.height);

        int top = panel.tableTop();
        int bottom = panel.tableBottom();

        table = new GuiJumpTableSlot(
                Minecraft.getMinecraft(),
                this.width,
                this.height,
                top,
                bottom,
                144,
                jumps,
                new GuiJumpTableSlot.ISelectionHandler() {
                    @Override
                    public void onSingleClick(int index) {
                        updateButtonStates();
                    }

                    @Override
                    public void onDoubleClick(int index) {
                        Jump selected = table.getSelectedItem();
                        if (selected == null) {
                            return;
                        }

                        SelectionState.setSelectedMap(map);
                        SelectionState.setSelectedJump(selected);

                        boolean hasStrategies = selected.getReminders() != null && !selected.getReminders().isEmpty();
                        if (hasStrategies) {
                            Minecraft.getMinecraft().displayGuiScreen(new GuiEditReminders(GuiJumpList.this));
                        } else {
                            Minecraft.getMinecraft().displayGuiScreen(new GuiCreateReminder(GuiJumpList.this));
                        }
                    }

                    @Override
                    public void onJumpNameClick(int index) {
                        // Selection only; do not open on single click.
                    }
                }
        );

        // IMPORTANT: provide map reference to table (needed for section overlays / names)
        table.setMap(map);

        if (map != null) {
            table.setMap(map);
            table.setSectionColumns(map.getEffectiveSectionsCount());
        } else {
            table.setMap(null);
            table.setSectionColumns(0);
        }

        // Make GuiSlot draw within panel bounds (prevents full-width top/bottom overlays).
        table.setPanelBounds(panel.left + 1, panel.right - 1);

        // Restore scroll after table was fully configured (bounds + columns)
        restoreScrollState();

        // If this list was opened with a preselected jump (e.g., after Transfer),
        // force-select it now. Do not depend on persisted scroll for this case.
        if (initialSelection != null && table != null) {
            hasSavedScroll = false; // ignore previously saved scroll for "jump focus" opens
            table.selectJump(initialSelection);

            // Ensure the moved jump is actually visible after opening the list.
            table.ensureSelectedVisible(12);
            // Alternatively, if you prefer center focus:
            // table.centerSelectedRow();
        }
    }

    /** Creates and lays out the buttons; which ones exist depends on the kind of map. */
    private void initButtons() {
        boolean isLegacy = BuiltinContainers.isRestoredMap(map);

        // Bottom buttons
        // Only RestoredStrats uses the "Insert" button; all other maps must NOT show Create.
        if (isLegacy) {
            createButton = new GuiButton(BTN_CREATE, 0, 0, 100, 20, "Insert");
        } else {
            createButton = null;
        }

        if (isLegacy) {
            convertButton = new GuiButton(BTN_CONVERT, 0, 0, 100, 20, "Convert");
        } else {
            convertButton = null;
        }

        editNameButton = new GuiButton(BTN_EDIT_NAME, 0, 0, 100, 20, "Edit name");

        removeButton = new GuiButton(BTN_REMOVE, 0, 0, 100, 20, "Remove");
        backButton = new GuiButton(BTN_BACK, 8, 8, 60, 20, "Back");

        // ------------------------------------------------------------
        // Configure sections button: HIDE for Global and RestoredStrats
        // ------------------------------------------------------------
        boolean hideConfigureSections = BuiltinContainers.isGlobalMap(map) || BuiltinContainers.isRestoredMap(map);

        if (!hideConfigureSections) {
            configureSectionsButton = new GuiButton(BTN_CONFIGURE_SECTIONS, 8 + 60 + 6, 8, 140, 20, "Configure sections");
            configureSectionsButton.enabled = (map != null && map.getEffectiveSectionsCount() > 0);
            this.buttonList.add(configureSectionsButton);
        } else {
            configureSectionsButton = null;
        }

        if (!isLegacy) {
            int w = 120;
            int h = 20;
            int x = this.width - 8 - w;
            int y = 8;

            transferButton = new GuiButton(BTN_TRANSFER, x, y, w, h, "Transfer to...");
            this.buttonList.add(transferButton);
        } else {
            transferButton = null;
        }

        // Center buttons with a small gap
        int gap = 8;
        int yButtons = this.height - 44;

        if (isLegacy) {
            // Legacy: Insert + Convert + Remove (no Edit name)
            int totalW = 0;
            int btnCount = 0;

            if (createButton != null) { totalW += createButton.width; btnCount++; }
            if (convertButton != null) { totalW += (btnCount > 0 ? gap : 0) + convertButton.width; btnCount++; }
            totalW += (btnCount > 0 ? gap : 0) + removeButton.width;

            int startX = (this.width - totalW) / 2;
            int x = startX;

            if (createButton != null) {
                createButton.xPosition = x;
                createButton.yPosition = yButtons;
                x += createButton.width + gap;
            }

            if (convertButton != null) {
                convertButton.xPosition = x;
                convertButton.yPosition = yButtons;
                x += convertButton.width + gap;
            }

            removeButton.xPosition = x;
            removeButton.yPosition = yButtons;

            // Do not show edit name in legacy list
            editNameButton.visible = false;
            editNameButton.enabled = false;
        } else {
            // Normal: Edit name + Remove (NO Create)
            int totalW = editNameButton.width + gap + removeButton.width;
            int startX = (this.width - totalW) / 2;

            editNameButton.xPosition = startX;
            editNameButton.yPosition = yButtons;

            removeButton.xPosition = editNameButton.xPosition + editNameButton.width + gap;
            removeButton.yPosition = yButtons;
        }

        if (createButton != null) {
            this.buttonList.add(createButton);
        }
        if (convertButton != null) {
            this.buttonList.add(convertButton);
        }
        this.buttonList.add(editNameButton);
        this.buttonList.add(removeButton);
        this.buttonList.add(backButton);

        // Reorder arrows next to Remove (side by side)
        // Requirement: hide arrows in RestoredStrats (legacy list).
        if (!isLegacy) {
            int arrowsW = 20;
            int arrowsH = 20;
            int arrowsGap = 4;

            int desiredX = removeButton.xPosition + removeButton.width + 8;
            int maxX = this.width - 10 - (arrowsW * 2 + arrowsGap);

            int arrowsX = Math.min(desiredX, maxX);
            if (arrowsX < 10) {
                arrowsX = 10;
            }

            moveUpButton = new GuiButton(BTN_MOVE_UP, arrowsX, yButtons, arrowsW, arrowsH, "^");
            moveDownButton = new GuiButton(BTN_MOVE_DOWN, arrowsX + arrowsW + arrowsGap, yButtons, arrowsW, arrowsH, "v");

            this.buttonList.add(moveUpButton);
            this.buttonList.add(moveDownButton);
        } else {
            moveUpButton = null;
            moveDownButton = null;
        }
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        // Restore default behavior for other screens (e.g., typing in prompts)
        Keyboard.enableRepeatEvents(true);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();

        // Open deferred GUI after prompt closes and returns to this screen
        if (pendingScreen != null) {
            GuiScreen toOpen = pendingScreen;
            pendingScreen = null;
            Minecraft.getMinecraft().displayGuiScreen(toOpen);
            return;
        }

        if (pendingMoveDelta != 0) {
            int d = pendingMoveDelta;
            pendingMoveDelta = 0;
            moveSelectedJump(d);
        }
    }

    private void refreshData() {
        if (map != null && map.getJumps() != null) {
            jumps = map.getJumps();
        } else {
            jumps = new ArrayList<Jump>();
        }
    }

    private void updateButtonStates() {
        boolean hasSelection = table != null && table.getSelectedItem() != null;
        boolean isLegacy = BuiltinContainers.isRestoredMap(map);

        // Transfer is available only when a jump is selected and only outside RestoredStrats.
        if (transferButton != null) {
            transferButton.enabled = hasSelection;
        }

        if (convertButton != null) convertButton.enabled = hasSelection;
        if (removeButton != null) removeButton.enabled = hasSelection;

        if (editNameButton != null) {
            // Edit name is not available for legacy list
            editNameButton.enabled = hasSelection && !isLegacy;
            editNameButton.visible = !isLegacy;
        }

        if (configureSectionsButton != null) {
            configureSectionsButton.enabled = (map != null && map.getEffectiveSectionsCount() > 0);
        }

        int idx = (table != null) ? table.getSelectedJumpIndexInItems() : -1;
        int size = (jumps != null) ? jumps.size() : 0;

        boolean canMoveUp = hasSelection && idx > 0;
        boolean canMoveDown = hasSelection && idx >= 0 && idx < (size - 1);

        if (moveUpButton != null) moveUpButton.enabled = canMoveUp;
        if (moveDownButton != null) moveDownButton.enabled = canMoveDown;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();

        String title = "Jumps for: " + (map != null ? map.getId() : "<null>");
        this.drawCenteredString(this.fontRendererObj, title, this.width / 2, 12, COLOR_TITLE);

        panel.drawBackground();
        panel.drawHeader(this.fontRendererObj, map, table);

        table.drawScreen(mouseX, mouseY, partialTicks);

        // Make sure no scissor from table/header leaks into the scrollbar draw.
        GuiScissor.disable();

        // IMPORTANT: draw first -> updates sb* cache, then handle drag
        table.drawHorizontalScrollbar(panel.bottom - 10, panel.bottom - 6);
        table.updateHorizontalScrollbarDrag(mouseX, mouseY);

        updateButtonStates();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        table.handleHorizontalScrollWheel();
        table.handleMouseInput();
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button == null) {
            return;
        }

        switch (button.id) {
            case BTN_BACK:
                hasSavedScroll = false; // reset only when leaving this list
                Minecraft.getMinecraft().displayGuiScreen(parent);
                return;
            case BTN_CONFIGURE_SECTIONS:
                onConfigureSections();
                return;
            case BTN_MOVE_UP:
                pendingMoveDelta = -1;
                return;
            case BTN_MOVE_DOWN:
                pendingMoveDelta = 1;
                return;
            case BTN_CREATE:
                onCreate();
                return;
            case BTN_TRANSFER:
                onTransfer();
                return;
            default:
                break;
        }

        // The remaining buttons act on the selected jump.
        Jump selected = table.getSelectedItem();
        if (selected == null) {
            return;
        }

        switch (button.id) {
            case BTN_EDIT_NAME:
                onEditName(selected);
                return;
            case BTN_CONVERT:
                onConvert(selected);
                return;
            case BTN_REMOVE:
                onRemove(selected);
                return;
            default:
                break;
        }
    }

    private void onConfigureSections() {
        // Button is hidden for Global/RestoredStrats, but keep this null-safe anyway.
        if (map == null) return;
        if (BuiltinContainers.isGlobalMap(map) || BuiltinContainers.isRestoredMap(map)) return;
        if (map.getEffectiveSectionsCount() <= 0) return;

        Minecraft.getMinecraft().displayGuiScreen(new GuiConfigureSections(this, map));
    }

    /** RestoredStrats: "Insert" runs the imports. Other maps: create a new jump via a name prompt. */
    private void onCreate() {
        // RestoredStrats: run BOTH imports (legacy + BetterLinkCraft) each time
        if (BuiltinContainers.isRestoredMap(map)) {
            boolean importedLegacy = LegacyModImporter.importIntoRestoredStrats();
            boolean importedBlc = BetterLinkCraftImporter.importIntoRestoredStrats();

            if (importedLegacy || importedBlc) {
                saveScrollState();
                initGui();
            }
            return;
        }

        Minecraft.getMinecraft().displayGuiScreen(new GuiTextPrompt(
                this,
                "Create jump",
                "",
                new GuiTextPrompt.IResultHandler() {
                    @Override
                    public void onConfirm(String text) {
                        Jump j = JumpService.createJumpAtPlayer(map, text);
                        if (j == null) {
                            initGui();
                            return;
                        }

                        SelectionState.setSelectedMap(map);
                        SelectionState.setSelectedJump(j);

                        Minecraft.getMinecraft().displayGuiScreen(new GuiCreateReminder(GuiJumpList.this));
                    }

                    @Override
                    public void onCancel() { }
                }
        ));
    }

    private void onTransfer() {
        if (BuiltinContainers.isRestoredMap(map)) {
            return;
        }

        Jump sel = (table != null) ? table.getSelectedItem() : null;
        if (sel == null) return;

        Minecraft.getMinecraft().displayGuiScreen(new GuiTransferJump(this, map, sel));
    }

    private void onEditName(final Jump selected) {
        if (BuiltinContainers.isRestoredMap(map)) {
            return;
        }

        Minecraft.getMinecraft().displayGuiScreen(new GuiTextPrompt(
                this,
                "Edit jump name",
                selected.getId() != null ? selected.getId() : "",
                new GuiTextPrompt.IResultHandler() {
                    @Override
                    public void onConfirm(String text) {
                        saveScrollState();
                        JumpService.renameJump(map, selected, text);
                        initGui();
                    }

                    @Override
                    public void onCancel() { }
                }
        ));
    }

    /** Legacy (RestoredStrats) only: ask for a new name, then open the convert editor. */
    private void onConvert(final Jump selected) {
        if (!BuiltinContainers.isRestoredMap(map)) {
            return;
        }

        // Step 1: ask for a new jump name, then open the convert editor (deferred).
        Minecraft.getMinecraft().displayGuiScreen(new GuiTextPrompt(
                this,
                "Convert to Global: choose a jump name",
                "",
                new GuiTextPrompt.IResultHandler() {
                    @Override
                    public void onConfirm(String text) {
                        String t = (text != null) ? text.trim() : "";
                        if (t.isEmpty()) {
                            Minecraft.getMinecraft().displayGuiScreen(GuiJumpList.this);
                            return;
                        }

                        // IMPORTANT: defer opening, because the prompt likely re-opens parent after confirm.
                        pendingScreen = new GuiConvertLegacy(GuiJumpList.this, selected, t);

                        // Ensure we return to this list; updateScreen() will open pendingScreen.
                        Minecraft.getMinecraft().displayGuiScreen(GuiJumpList.this);
                    }

                    @Override
                    public void onCancel() {
                        Minecraft.getMinecraft().displayGuiScreen(GuiJumpList.this);
                    }
                }
        ));
    }

    private void onRemove(final Jump selected) {
        Minecraft.getMinecraft().displayGuiScreen(new GuiConfirm(
                this,
                "Remove jump",
                "Are you sure?",
                new GuiConfirm.IConfirmHandler() {
                    @Override
                    public void onYes() {
                        saveScrollState();
                        JumpService.removeJump(map, selected);
                        initGui();
                    }

                    @Override
                    public void onNo() { }
                }
        ));
    }

    private void saveScrollState() {
        if (table == null) return;

        savedVScrollPx = table.getVerticalScrollPx();
        savedHScrollPx = table.getHorizontalScrollPx();
        hasSavedScroll = true;
    }

    private void restoreScrollState() {
        if (!hasSavedScroll) return;
        if (table == null) return;

        table.setVerticalScrollPx(savedVScrollPx);
        table.setHorizontalScrollPx(savedHScrollPx);
    }

    private void requestMoveSelectedJump(int delta) {
        if (table == null || map == null) return;

        Jump selected = table.getSelectedItem();
        if (selected == null) return;

        long now = Minecraft.getSystemTime();

        // Ignore accidental duplicates (same jump + same direction in a short window)
        if (selected == lastMoveJump && delta == lastMoveDelta) {
            long dt = now - lastMoveMs;
            if (dt >= 0 && dt <= MOVE_DEBOUNCE_MS) {
                System.out.println("[ParkourStrats] [DBG] requestMoveSelectedJump ignored duplicate dt=" + dt);
                return;
            }
        }

        lastMoveMs = now;
        lastMoveJump = selected;
        lastMoveDelta = delta;

        moveSelectedJump(delta);
    }

    private void moveSelectedJump(int delta) {
        if (table == null || map == null) {
            return;
        }

        Jump selected = table.getSelectedItem();
        if (selected == null) {
            return;
        }

        ArrayList<Jump> list = map.getJumps();
        if (list == null || list.size() <= 1) {
            return;
        }

        // Reference-based lookup (do NOT use equals()).
        int idx = -1;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) == selected) {
                idx = i;
                break;
            }
        }

        // If not found, do best-effort fallback (should be rare).
        if (idx < 0) {
            idx = table.getSelectedJumpIndexInItems();
        }
        if (idx < 0) {
            return;
        }

        int newIdx = idx + delta;
        if (newIdx < 0 || newIdx >= list.size()) {
            return;
        }

        // Do the actual move (1 step).
        if (!SectionLayoutService.moveJumpByOneRespectingSections(map, selected, delta)) {
            return;
        }

        // Restore scroll BEFORE rebuild
        saveScrollState();

        // Rebuild GUI so table rebinds and section overlays use fresh indices.
        initGui();

        // Restore selection on the same Jump instance and refresh row caches.
        if (table != null) {
            table.selectJump(selected);
        }

        updateButtonStates();
    }

    @Override
    public void handleKeyboardInput() throws java.io.IOException {
        super.handleKeyboardInput();

        // Only handle on key-down, and ignore key repeat events.
        if (!Keyboard.getEventKeyState()) {
            return;
        }
        if (Keyboard.isRepeatEvent()) {
            return;
        }

        int key = Keyboard.getEventKey();

        if (key == Keyboard.KEY_UP) {
            requestMoveSelectedJump(-1);
            return;
        }

        if (key == Keyboard.KEY_DOWN) {
            requestMoveSelectedJump(1);
            return;
        }
    }
}