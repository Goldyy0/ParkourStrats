package me.texyle.startreminders.data;

import java.util.ArrayList;

/**
 * What to take from a linked sheet: a first and last jump (the sheet may have clutter before and after the strategies)
 * and jumps to leave out. Jumps are identified by their sheet key, which stays the same from sync to sync.
 */
public class SheetFilter {

    private String startKey = "";
    private String endKey = "";
    private ArrayList<String> excludedKeys = new ArrayList<String>();

    /** Key of the first jump to import, or empty to start at the top of the sheet. */
    public String getStartKey() {
        return startKey != null ? startKey : "";
    }

    public void setStartKey(String startKey) {
        this.startKey = startKey != null ? startKey : "";
    }

    /** Key of the last jump to import, or empty to read to the bottom of the sheet. */
    public String getEndKey() {
        return endKey != null ? endKey : "";
    }

    public void setEndKey(String endKey) {
        this.endKey = endKey != null ? endKey : "";
    }

    public ArrayList<String> getExcludedKeys() {
        if (excludedKeys == null) excludedKeys = new ArrayList<String>();
        return excludedKeys;
    }

    public boolean isEmpty() {
        return getStartKey().isEmpty() && getEndKey().isEmpty() && getExcludedKeys().isEmpty();
    }
}
