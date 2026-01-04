// Jump.java

package me.texyle.startreminders.data;

import java.util.ArrayList;

import me.texyle.startreminders.reminders.Reminder;

public class Jump {
    private String id;

    private int x;
    private int y;
    private int z;

    // Step C (table columns)
    private String position = "";
    private String facing = "";

    private String setup = "";
    private String strategy = "";
    private String strafe = "";
    private String turn = "";
    private String author = "";
    private String tips = "";

    private ArrayList<Reminder> reminders = new ArrayList<Reminder>();

    private int activeReminderIndex = -1;

    private Boolean showJumpNameInWorld;

    // NEW: Persistent binding to a specific spreadsheet row identity (context + occurrence)
    // This prevents wrong mapping when multiple jumps share the same name.
    // Example: "firstjump|S=start|SS=...|CP=...#2"
    private String sheetRowKey;

    public String getSheetRowKey() {
        return sheetRowKey != null ? sheetRowKey : "";
    }

    public void setSheetRowKey(String sheetRowKey) {
        this.sheetRowKey = (sheetRowKey != null) ? sheetRowKey : "";
    }

    public boolean hasSheetRowKey() {
        return sheetRowKey != null && sheetRowKey.trim().length() > 0;
    }

    // ... (zbytek Vaší třídy beze změn)

    /**
     * Ensures the persisted field exists with an explicit value.
     * Returns true if a missing value was initialized.
     */
    public boolean ensureShowJumpNameInWorldInitialized(boolean isRestoredStratsContext) {
        if (showJumpNameInWorld == null) {
            showJumpNameInWorld = Boolean.valueOf(!isRestoredStratsContext);
            return true;
        }
        return false;
    }

    public boolean isShowJumpNameInWorld() {
        return showJumpNameInWorld == null ? true : showJumpNameInWorld.booleanValue();
    }

    public void setShowJumpNameInWorld(boolean showJumpNameInWorld) {
        this.showJumpNameInWorld = Boolean.valueOf(showJumpNameInWorld);
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public int getX() { return x; }
    public void setX(int x) { this.x = x; }

    public int getY() { return y; }
    public void setY(int y) { this.y = y; }

    public int getZ() { return z; }
    public void setZ(int z) { this.z = z; }

    public ArrayList<Reminder> getReminders() { return reminders; }
    public void setReminders(ArrayList<Reminder> reminders) { this.reminders = reminders; }

    public int getActiveReminderIndex() { return activeReminderIndex; }
    public void setActiveReminderIndex(int activeReminderIndex) { this.activeReminderIndex = activeReminderIndex; }

    public String getPosition() { return position != null ? position : ""; }
    public void setPosition(String position) { this.position = position != null ? position : ""; }

    public String getFacing() { return facing != null ? facing : ""; }
    public void setFacing(String facing) { this.facing = facing != null ? facing : ""; }

    public String getSetup() { return setup; }
    public void setSetup(String setup) { this.setup = setup != null ? setup : ""; }

    public String getStrategy() { return strategy; }
    public void setStrategy(String strategy) { this.strategy = strategy != null ? strategy : ""; }

    public String getStrafe() { return strafe; }
    public void setStrafe(String strafe) { this.strafe = strafe != null ? strafe : ""; }

    public String getTurn() { return turn; }
    public void setTurn(String turn) { this.turn = turn != null ? turn : ""; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author != null ? author : ""; }

    public String getTips() { return tips; }
    public void setTips(String tips) { this.tips = tips != null ? tips : ""; }
}