package me.texyle.startreminders.gui;

import java.io.IOException;
import java.util.ArrayList;

import me.texyle.startreminders.data.Jump;
import me.texyle.startreminders.data.MapSection;
import me.texyle.startreminders.data.ParkourMap;
import me.texyle.startreminders.data.ServerProfile;
import me.texyle.startreminders.gui.hierarchy.GuiMapList;
import me.texyle.startreminders.gui.hierarchy.GuiPickSectionPreview;
import me.texyle.startreminders.gui.hierarchy.GuiServerList;
import me.texyle.startreminders.reminders.Reminder;
import me.texyle.startreminders.reminders.ReminderManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.EnumChatFormatting;

public class GuiCreateJumpContext extends GuiScreen {

    private static final int FIELD_WIDTH = 220;

    private static final int COLOR_TEXT = 0xFFFFFF;
    private static final int COLOR_INVALID_OVERLAY = 0x55FF0000;

    private static final String PLACEHOLDER_TEXT = "PLACEHOLDER";

    // ---------------------------------------
    // Sticky (cached) section selection
    // ---------------------------------------
    // Cached per (serverId + mapId) so it only "sticks" for the same context.
    private static String cachedServerId = null;
    private static String cachedMapId = null;
    private static int cachedSectionLevelOneBased = 0; // 0 = none
    private static String cachedSectionId = null; // MapSection.getId()

    private final GuiScreen parent;

    private GuiButton backButton;
    private GuiButton nextButton;
    private GuiButton createPlaceholderButton;
    private GuiButton configurePlaceholderButton;

    private GuiButton pickServerButton;
    private GuiButton pickMapButton;

    private GuiButton pickSectionButton;

    private GuiButton prevJumpButton;
    private GuiButton nextJumpButton;

    // Section navigation (Level 1)
    private GuiButton prevSectionButton;
    private GuiButton nextSectionButton;

    private GuiTextField jumpNameField;

    // Cache for jump name so it survives re-initGui when returning from sub GUIs
    private String cachedJumpNameText = "";

    private ServerProfile selectedServer;
    private ParkourMap selectedMap;

    // Optional section selection
    private int selectedSectionLevelOneBased = 0; // 0 = none
    private MapSection selectedSection = null;

    private String errorText = "";

    public GuiCreateJumpContext() {
        this(null);
    }

    public GuiCreateJumpContext(GuiScreen parent) {
        this.parent = parent;

        this.selectedServer = ReminderManager.getSelectedServer();
        this.selectedMap = ReminderManager.getSelectedMap();

        if (this.selectedServer == null) {
            this.selectedMap = null;
        }

        // Disallow RestoredStrats as context for creating new jumps
        if (this.selectedServer != null && ReminderManager.isRestoredServer(this.selectedServer)) {
            this.selectedServer = null;
            this.selectedMap = null;
        }

        // If server is Global, force global map
        if (this.selectedServer != null && ReminderManager.isGlobalServer(this.selectedServer)) {
            this.selectedMap = ReminderManager.getGlobalMap();
        }

        // Try restore cached section selection (only if server+map match)
        restoreStickySectionIfPossible();
    }

    private void restoreStickySectionIfPossible() {
        clearSectionSelection();

        if (selectedServer == null || selectedMap == null) {
            clearStickySectionCache();
            return;
        }

        String sid = safeId(selectedServer.getId());
        String mid = safeId(selectedMap.getId());

        if (sid.isEmpty() || mid.isEmpty()) {
            clearStickySectionCache();
            return;
        }

        if (!sid.equals(cachedServerId) || !mid.equals(cachedMapId)) {
            // Different context => do not restore
            return;
        }

        if (cachedSectionLevelOneBased < 1 || cachedSectionLevelOneBased > 4) {
            return;
        }

        if (cachedSectionId == null || cachedSectionId.trim().isEmpty()) {
            return;
        }

        MapSection resolved = findSectionById(selectedMap, cachedSectionLevelOneBased, cachedSectionId);
        if (resolved != null) {
            selectedSectionLevelOneBased = cachedSectionLevelOneBased;
            selectedSection = resolved;
        }
    }

    private static MapSection findSectionById(ParkourMap map, int levelOneBased, String sectionId) {
        if (map == null) return null;
        if (levelOneBased < 1 || levelOneBased > 4) return null;
        if (sectionId == null) return null;

        ArrayList<MapSection> list = map.getSectionsForLevel(levelOneBased);
        if (list == null || list.isEmpty()) return null;

        for (int i = 0; i < list.size(); i++) {
            MapSection s = list.get(i);
            if (s == null) continue;
            String id = s.getId();
            if (id != null && id.equals(sectionId)) {
                return s;
            }
        }

        return null;
    }

    private static void setStickySectionCache(ServerProfile server, ParkourMap map, int levelOneBased, MapSection section) {
        String sid = (server != null) ? safeId(server.getId()) : "";
        String mid = (map != null) ? safeId(map.getId()) : "";

        if (sid.isEmpty() || mid.isEmpty()) {
            clearStickySectionCache();
            return;
        }

        cachedServerId = sid;
        cachedMapId = mid;

        if (section == null || levelOneBased < 1 || levelOneBased > 4) {
            cachedSectionLevelOneBased = 0;
            cachedSectionId = null;
            return;
        }

        cachedSectionLevelOneBased = levelOneBased;
        cachedSectionId = section.getId();
    }

    private static void clearStickySectionCache() {
        cachedServerId = null;
        cachedMapId = null;
        cachedSectionLevelOneBased = 0;
        cachedSectionId = null;
    }

    private int getBaseY() {
        // Move the whole form up (stronger offset), and clamp to keep it on-screen.
        int y = (this.height / 3) - 52;
        if (y < 24) y = 24;
        return y;
    }

    @Override
    public void initGui() {
        super.initGui();

        int yBase = getBaseY();

        backButton = new GuiButton(20, 8, 8, 60, 20, "Back");
        this.buttonList.add(backButton);

        nextButton = new GuiButton(5, 0, 0, "Next");
        nextButton.xPosition = (this.width - nextButton.width) / 2;
        nextButton.yPosition = this.height - 28;
        this.buttonList.add(nextButton);

        // Row 1: Jump name field at yBase
        jumpNameField = new GuiTextField(0, this.fontRendererObj, getFieldX(), yBase, FIELD_WIDTH, 20);
        jumpNameField.setFocused(true);
        jumpNameField.setMaxStringLength(48);

        // Restore cached text (survives returning from sub GUIs)
        if (cachedJumpNameText != null && cachedJumpNameText.length() > 0) {
            jumpNameField.setText(cachedJumpNameText);
        }

        // If still empty, try placeholder autofill
        tryAutofillJumpName();

        // Row 2: Server/Map buttons
        pickServerButton = new GuiButton(6, 0, 0, "Select server");
        pickServerButton.width = 110;
        pickServerButton.xPosition = (this.width - FIELD_WIDTH) / 2;
        pickServerButton.yPosition = yBase + 26;
        this.buttonList.add(pickServerButton);

        pickMapButton = new GuiButton(7, 0, 0, "Select map");
        pickMapButton.width = 110;
        pickMapButton.xPosition = (this.width - FIELD_WIDTH) / 2 + 110;
        pickMapButton.yPosition = yBase + 26;
        this.buttonList.add(pickMapButton);

        // Row 3: Section button (optional)
        pickSectionButton = new GuiButton(12, 0, 0, "Select section (optional)");
        pickSectionButton.width = FIELD_WIDTH;
        pickSectionButton.xPosition = (this.width - FIELD_WIDTH) / 2;
        pickSectionButton.yPosition = yBase + 50;
        this.buttonList.add(pickSectionButton);

        // Placeholder buttons (below the info rows)
        createPlaceholderButton = new GuiButton(8, 0, 0, "Create placeholder");
        createPlaceholderButton.width = 160;
        createPlaceholderButton.xPosition = (this.width - createPlaceholderButton.width) / 2;
        createPlaceholderButton.yPosition = yBase + 156;
        this.buttonList.add(createPlaceholderButton);

        configurePlaceholderButton = new GuiButton(9, 0, 0, "Configure placeholder");
        configurePlaceholderButton.width = 160;
        configurePlaceholderButton.xPosition = (this.width - configurePlaceholderButton.width) / 2;
        configurePlaceholderButton.yPosition = yBase + 180;
        this.buttonList.add(configurePlaceholderButton);

        // Navigation buttons for placeholder SECTION paging (Level 1)
        prevSectionButton = new GuiButton(13, 0, 0, "<");
        prevSectionButton.width = 20;
        prevSectionButton.height = 20;
        prevSectionButton.xPosition = getFieldX();
        prevSectionButton.yPosition = yBase - 44; // above jump nav
        prevSectionButton.visible = false;
        this.buttonList.add(prevSectionButton);

        nextSectionButton = new GuiButton(14, 0, 0, ">");
        nextSectionButton.width = 20;
        nextSectionButton.height = 20;
        nextSectionButton.xPosition = getFieldX() + FIELD_WIDTH - 20;
        nextSectionButton.yPosition = yBase - 44; // above jump nav
        nextSectionButton.visible = false;
        this.buttonList.add(nextSectionButton);

        // Navigation buttons for placeholder mode (shown only when enabled)
        prevJumpButton = new GuiButton(10, 0, 0, "<");
        prevJumpButton.width = 20;
        prevJumpButton.height = 20;
        prevJumpButton.xPosition = getFieldX();
        prevJumpButton.yPosition = yBase - 22;
        prevJumpButton.visible = false;
        this.buttonList.add(prevJumpButton);

        nextJumpButton = new GuiButton(11, 0, 0, ">");
        nextJumpButton.width = 20;
        nextJumpButton.height = 20;
        nextJumpButton.xPosition = getFieldX() + FIELD_WIDTH - 20;
        nextJumpButton.yPosition = yBase - 22;
        nextJumpButton.visible = false;
        this.buttonList.add(nextJumpButton);

        updateButtonStates();
    }

    private void clearSectionSelection() {
        selectedSectionLevelOneBased = 0;
        selectedSection = null;
    }

    private boolean isSectionPickAllowed() {
        if (selectedServer == null) return false;
        if (ReminderManager.isGlobalServer(selectedServer)) return false;
        if (ReminderManager.isRestoredServer(selectedServer)) return false;
        return selectedMap != null;
    }

    private void tryAutofillJumpName() {
        if (jumpNameField == null) return;

        String current = jumpNameField.getText();
        if (current != null && current.trim().length() > 0) {
            return; // do not override user text
        }

        // Do not override cached text either (extra safety)
        if (cachedJumpNameText != null && cachedJumpNameText.trim().length() > 0) {
            return;
        }

        if (ReminderManager.isPlaceholderSyncEnabledForSelectedMap()) {
            String name = ReminderManager.getCurrentPlaceholderJumpName();
            if (name != null && name.trim().length() > 0) {
                jumpNameField.setText(name.trim());
                cachedJumpNameText = name.trim();
            }
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);

        String title = "Create strategy: choose Jump / Server / Map / Section";
        this.drawCenteredString(this.fontRendererObj, title, this.width / 2, 14, COLOR_TEXT);

        int yBase = getBaseY();

        // Placeholder navigation UI (only when enabled)
        boolean showNav = ReminderManager.isPlaceholderSyncEnabledForSelectedMap();
        if (showNav) {
            // Section pager (Level 1) - sheet-driven
            int secCount = ReminderManager.getPlaceholderSectionCount();
            if (secCount > 0) {
                int secIdx = ReminderManager.getCurrentPlaceholderSectionIndex();
                if (secIdx < 0) secIdx = 0;
                if (secIdx >= secCount) secIdx = secCount - 1;

                String nm = ReminderManager.getCurrentPlaceholderSectionName();
                if (nm == null || nm.trim().isEmpty()) nm = "(unnamed)";

                String secPage = (secIdx + 1) + "/" + secCount + "  " + nm;
                this.drawCenteredString(this.fontRendererObj,
                        EnumChatFormatting.GOLD + "Section: " + secPage,
                        this.width / 2, yBase - 38, COLOR_TEXT);
            }

            // Jump pager
            int idx = ReminderManager.getPlaceholderJumpIndex() + 1;
            int count = ReminderManager.getPlaceholderJumpCount();
            String page = idx + "/" + count;
            this.drawCenteredString(this.fontRendererObj, EnumChatFormatting.YELLOW + page, this.width / 2, yBase - 16, COLOR_TEXT);
        }

        // Labels
        this.fontRendererObj.drawString("Jump Name:", getLabelX(), yBase + 6, COLOR_TEXT, true);

        // Info rows are below the buttons to avoid overlap
        int infoY1 = yBase + 78;
        int infoY2 = yBase + 102;
        int infoY3 = yBase + 126;

        this.fontRendererObj.drawString("Server:", getLabelX(), infoY1, COLOR_TEXT, true);
        this.fontRendererObj.drawString("Map:", getLabelX(), infoY2, COLOR_TEXT, true);
        this.fontRendererObj.drawString("Section:", getLabelX(), infoY3, COLOR_TEXT, true);

        String serverText = (selectedServer != null && selectedServer.getId() != null)
                ? selectedServer.getId()
                : "<not selected>";
        String mapText = (selectedMap != null && selectedMap.getId() != null)
                ? selectedMap.getId()
                : "<not selected>";

        String sectionText = "<optional>";
        if (selectedSection != null && selectedSectionLevelOneBased >= 1 && selectedSectionLevelOneBased <= 4) {
            String lvl = toRoman(selectedSectionLevelOneBased);
            String nm = selectedSection.getName();
            if (nm == null || nm.trim().isEmpty()) nm = "(unnamed)";
            sectionText = "Section " + lvl + ": " + nm + " (" + (selectedSection.getStartJumpIndex() + 1) + "-" + (selectedSection.getEndJumpIndex() + 1) + ")";
        }

        this.fontRendererObj.drawString(EnumChatFormatting.YELLOW + serverText, getFieldX(), infoY1, COLOR_TEXT, true);
        this.fontRendererObj.drawString(EnumChatFormatting.YELLOW + mapText, getFieldX(), infoY2, COLOR_TEXT, true);
        this.fontRendererObj.drawString(EnumChatFormatting.YELLOW + sectionText, getFieldX(), infoY3, COLOR_TEXT, true);

        if (errorText != null && !errorText.trim().isEmpty()) {
            this.drawCenteredString(this.fontRendererObj,
                    EnumChatFormatting.RED + errorText,
                    this.width / 2,
                    yBase + 206,
                    COLOR_TEXT);
        }

        drawValidationOverlays();
        if (jumpNameField != null) {
            jumpNameField.drawTextBox();
        }
    }

    private static String toRoman(int lvl) {
        if (lvl == 1) return "I";
        if (lvl == 2) return "II";
        if (lvl == 3) return "III";
        if (lvl == 4) return "IV";
        return "";
    }

    private void drawValidationOverlays() {
        if (!hasValidId(jumpNameField.getText())) {
            drawFieldOverlay(jumpNameField);
        }
    }

    private void drawFieldOverlay(GuiTextField field) {
        if (field == null) {
            return;
        }
        int left = field.xPosition - 1;
        int top = field.yPosition - 1;
        int right = field.xPosition + field.width + 1;
        int bottom = field.yPosition + field.height + 1;
        drawRect(left, top, right, bottom, COLOR_INVALID_OVERLAY);
    }

    private int getFieldX() {
        return (this.width - FIELD_WIDTH) / 2;
    }

    private int getLabelX() {
        return getFieldX() - 70;
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (jumpNameField != null) {
            jumpNameField.textboxKeyTyped(typedChar, keyCode);
            cachedJumpNameText = jumpNameField.getText();
        }

        if (keyCode == 28) { // Enter
            if (nextButton != null && nextButton.enabled) {
                actionPerformed(nextButton);
                return;
            }
        }

        if (keyCode == 1) { // Esc
            goBack();
            return;
        }

        super.keyTyped(typedChar, keyCode);
        updateButtonStates();
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (jumpNameField != null) {
            jumpNameField.updateCursorCounter();
        }
        updateButtonStates();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (jumpNameField != null) {
            jumpNameField.mouseClicked(mouseX, mouseY, mouseButton);
            cachedJumpNameText = jumpNameField.getText();
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private static int getPlayerBlockX(EntityPlayerSP p) {
        return (int) Math.floor(p.posX);
    }

    private static int getPlayerBlockYPlusOne(EntityPlayerSP p) {
        return (int) Math.floor(p.posY) + 2;
    }

    private static int getPlayerBlockZ(EntityPlayerSP p) {
        return (int) Math.floor(p.posZ);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button == null) {
            return;
        }

        // Cache the current text before opening any sub GUI
        if (jumpNameField != null) {
            cachedJumpNameText = jumpNameField.getText();
        }

        if (button.id == 20) {
            goBack();
            return;
        }

        if (button.id == 9) {
            Minecraft.getMinecraft().displayGuiScreen(new GuiConfigurePlaceholder(this));
            return;
        }

        if (button.id == 13) { // prev section (sheet-driven)
            int cur = ReminderManager.getCurrentPlaceholderSectionIndex();
            ReminderManager.jumpToPlaceholderSection(cur - 1);

            if (jumpNameField != null) {
                jumpNameField.setText(ReminderManager.getCurrentPlaceholderJumpName());
                cachedJumpNameText = jumpNameField.getText();
            }
            updateButtonStates();
            return;
        }

        if (button.id == 14) { // next section (sheet-driven)
            int cur = ReminderManager.getCurrentPlaceholderSectionIndex();
            ReminderManager.jumpToPlaceholderSection(cur + 1);

            if (jumpNameField != null) {
                jumpNameField.setText(ReminderManager.getCurrentPlaceholderJumpName());
                cachedJumpNameText = jumpNameField.getText();
            }
            updateButtonStates();
            return;
        }

        if (button.id == 10) { // prev
            ReminderManager.prevPlaceholderJump();
            if (jumpNameField != null) {
                jumpNameField.setText(ReminderManager.getCurrentPlaceholderJumpName());
                cachedJumpNameText = jumpNameField.getText();
            }
            updateButtonStates();
            return;
        }

        if (button.id == 11) { // next
            ReminderManager.nextPlaceholderJump();
            if (jumpNameField != null) {
                jumpNameField.setText(ReminderManager.getCurrentPlaceholderJumpName());
                cachedJumpNameText = jumpNameField.getText();
            }
            updateButtonStates();
            return;
        }

        if (button.id == 8) {
            createPlaceholderJump();
            return;
        }

        if (button.id == 12) {
            if (!isSectionPickAllowed()) {
                errorText = "Select a non-global server + map first.";
                return;
            }

            Minecraft.getMinecraft().displayGuiScreen(new GuiPickSectionPreview(
                    this,
                    selectedMap,
                    (levelOneBased, section) -> {
                        // level 0 + null => unselect (optional)
                        if (section == null || levelOneBased < 1 || levelOneBased > 4) {
                            clearSectionSelection();
                            setStickySectionCache(selectedServer, selectedMap, 0, null);
                        } else {
                            selectedSectionLevelOneBased = levelOneBased;
                            selectedSection = section;
                            setStickySectionCache(selectedServer, selectedMap, levelOneBased, section);
                        }

                        errorText = "";
                        updateButtonStates();
                    }
            ));
            return;
        }

        if (button.id == 6) {
            Minecraft.getMinecraft().displayGuiScreen(
                    new GuiServerList(this, server -> {
                        if (server != null && ReminderManager.isRestoredServer(server)) {
                            selectedServer = null;
                            selectedMap = null;
                            clearSectionSelection();
                            clearStickySectionCache();

                            ReminderManager.setSelectedServer(null);
                            ReminderManager.setSelectedMap(null);
                            ReminderManager.setSelectedJump(null);

                            errorText = "RestoredStrats cannot be used for creating new jumps.";
                            updateButtonStates();
                            return;
                        }

                        selectedServer = server;
                        clearSectionSelection();
                        clearStickySectionCache();

                        if (selectedServer != null && ReminderManager.isGlobalServer(selectedServer)) {
                            selectedMap = ReminderManager.getGlobalMap();
                        } else {
                            selectedMap = null;
                        }

                        ReminderManager.setSelectedServer(server);
                        ReminderManager.setSelectedMap(selectedMap);
                        ReminderManager.setSelectedJump(null);

                        errorText = "";
                        updateButtonStates();
                    }, false)
            );
            return;
        }

        if (button.id == 7) {
            if (selectedServer == null) {
                errorText = "Select a server first.";
                return;
            }

            if (ReminderManager.isGlobalServer(selectedServer)) {
                selectedMap = ReminderManager.getGlobalMap();
                ReminderManager.setSelectedMap(selectedMap);
                clearSectionSelection();
                clearStickySectionCache();
                errorText = "";
                updateButtonStates();
                return;
            }

            Minecraft.getMinecraft().displayGuiScreen(
                    new GuiMapList(this, selectedServer, map -> {
                        selectedMap = map;
                        clearSectionSelection();
                        clearStickySectionCache();

                        ReminderManager.setSelectedServer(selectedServer);
                        ReminderManager.setSelectedMap(map);
                        ReminderManager.setSelectedJump(null);

                        errorText = "";
                        updateButtonStates();
                    })
            );
            return;
        }

        if (button.id == 5) {
            if (!nextButton.enabled) {
                return;
            }

            String jumpId = safeId(jumpNameField.getText());
            if (!hasValidId(jumpId)) {
                errorText = "Jump Name is required.";
                updateButtonStates();
                return;
            }

            if (selectedServer == null) {
                errorText = "Server is required.";
                updateButtonStates();
                return;
            }

            if (ReminderManager.isRestoredServer(selectedServer)) {
                errorText = "RestoredStrats cannot be used for creating new jumps.";
                selectedServer = null;
                selectedMap = null;
                clearSectionSelection();
                clearStickySectionCache();
                updateButtonStates();
                return;
            }

            if (ReminderManager.isGlobalServer(selectedServer)) {
                selectedMap = ReminderManager.getGlobalMap();
                clearSectionSelection();
                clearStickySectionCache();
            }

            if (selectedMap == null) {
                errorText = "Map is required.";
                updateButtonStates();
                return;
            }

            ReminderManager.setSelectedServer(selectedServer);
            ReminderManager.setSelectedMap(selectedMap);

            EntityPlayerSP p = Minecraft.getMinecraft().thePlayer;

            int x = 0;
            int y = 0;
            int z = 0;

            if (p != null) {
                x = getPlayerBlockX(p);
                y = getPlayerBlockYPlusOne(p);
                z = getPlayerBlockZ(p);
            }

            Jump j = ReminderManager.getOrCreateJumpByNameAndCoords(selectedMap, jumpId, x, y, z);

            if (j == null) {
                errorText = "Failed to resolve jump.";
                updateButtonStates();
                return;
            }

            // If a section is selected, insert/move jump into it.
            if (selectedSection != null && selectedSectionLevelOneBased >= 1 && selectedSectionLevelOneBased <= 4 && isSectionPickAllowed()) {
                ReminderManager.insertJumpAtEndOfSection(selectedMap, selectedSectionLevelOneBased, selectedSection, j);

                // Keep section sticky for repeated jump creation
                setStickySectionCache(selectedServer, selectedMap, selectedSectionLevelOneBased, selectedSection);
            }

            ReminderManager.setSelectedJump(j);
            Minecraft.getMinecraft().displayGuiScreen(new GuiCreateReminder(this));
            return;
        }

        super.actionPerformed(button);
    }

    private void createPlaceholderJump() {
        String jumpId = safeId(jumpNameField.getText());
        if (!hasValidId(jumpId) || selectedServer == null || selectedMap == null) {
            return;
        }

        EntityPlayerSP p = Minecraft.getMinecraft().thePlayer;

        int x = 0;
        int y = 0;
        int z = 0;

        if (p != null) {
            x = getPlayerBlockX(p);
            y = getPlayerBlockYPlusOne(p);
            z = getPlayerBlockZ(p);
        }

        Jump j = ReminderManager.getOrCreateJumpByNameAndCoords(selectedMap, jumpId, x, y, z);
        if (j == null) {
            errorText = "Failed to create placeholder jump.";
            return;
        }

        // IMPORTANT: if a section is selected, move the (already created) jump to the end of that section.
        if (selectedSection != null
                && selectedSectionLevelOneBased >= 1
                && selectedSectionLevelOneBased <= 4
                && isSectionPickAllowed()) {

            ReminderManager.moveExistingJumpToEndOfSection(selectedMap, selectedSectionLevelOneBased, selectedSection, j);

            // Keep section sticky for repeated placeholder creation too
            setStickySectionCache(selectedServer, selectedMap, selectedSectionLevelOneBased, selectedSection);
        }

        ArrayList<String> lines = new ArrayList<String>(8);
        for (int i = 0; i < 8; i++) {
            lines.add("");
        }

        lines.set(2, PLACEHOLDER_TEXT);

        Reminder r = new Reminder(lines);
        j.getReminders().add(r);

        ReminderManager.saveToFile();
        ReminderManager.advancePlaceholderAfterCreate();

        goBack();
    }

    private void goBack() {
        if (parent != null) {
            Minecraft.getMinecraft().displayGuiScreen(parent);
        } else {
            Minecraft.getMinecraft().displayGuiScreen(null);
        }
    }

    private void updateButtonStates() {
        boolean serverSelected = (selectedServer != null);
        boolean isGlobal = (selectedServer != null && ReminderManager.isGlobalServer(selectedServer));

        if (pickMapButton != null) {
            pickMapButton.enabled = serverSelected && !isGlobal;
        }

        if (pickSectionButton != null) {
            pickSectionButton.enabled = isSectionPickAllowed();
        }

        boolean okName = hasValidId(safeId(jumpNameField != null ? jumpNameField.getText() : ""));

        boolean ok;
        if (isGlobal) {
            ok = okName && serverSelected;
        } else {
            ok = okName && serverSelected && selectedMap != null;
        }

        if (nextButton != null) {
            nextButton.enabled = ok;
        }

        if (createPlaceholderButton != null) {
            createPlaceholderButton.enabled = ok;
        }

        if (configurePlaceholderButton != null) {
            boolean canConfig = serverSelected && (isGlobal || selectedMap != null);
            configurePlaceholderButton.enabled = canConfig;
        }

        boolean showNav = ReminderManager.isPlaceholderSyncEnabledForSelectedMap();

        boolean hasSectionsL1 = showNav && (ReminderManager.getPlaceholderSectionCount() > 0);

        if (prevSectionButton != null) {
            prevSectionButton.visible = showNav && hasSectionsL1;
            prevSectionButton.enabled = showNav && hasSectionsL1;
        }
        if (nextSectionButton != null) {
            nextSectionButton.visible = showNav && hasSectionsL1;
            nextSectionButton.enabled = showNav && hasSectionsL1;
        }

        if (prevJumpButton != null) {
            prevJumpButton.visible = showNav;
            prevJumpButton.enabled = showNav;
        }
        if (nextJumpButton != null) {
            nextJumpButton.visible = showNav;
            nextJumpButton.enabled = showNav;
        }
    }

    private static boolean hasValidId(String id) {
        if (id == null) return false;
        String trimmed = id.trim();
        if (trimmed.isEmpty()) return false;
        return trimmed.length() <= 48;
    }

    private static String safeId(String s) {
        return s != null ? s.trim() : "";
    }
}