// MapSection.java
package me.texyle.startreminders.data;

public class MapSection {

    // Unique id per section, persisted in JSON
    private String id = "";
    private String name = "";

    private int colorArgb = 0xFFFFFFFF;      // default: solid white (background)
    private int textColorArgb = 0xFFFFFFFF;  // default: solid white (section name text)

    private int startJumpIndex = 0; // inclusive (0-based)
    private int endJumpIndex = 0;   // inclusive (0-based)

    public MapSection() { }

    public String getId() {
        return id != null ? id : "";
    }

    public void setId(String id) {
        this.id = id != null ? id : "";
    }

    public String getName() {
        return name != null ? name : "";
    }

    public void setName(String name) {
        this.name = name != null ? name : "";
    }

    public int getColorArgb() {
        return colorArgb;
    }

    public void setColorArgb(int colorArgb) {
        this.colorArgb = 0xFF000000 | (colorArgb & 0x00FFFFFF);
    }

    public int getTextColorArgb() {
        return textColorArgb;
    }

    public void setTextColorArgb(int textColorArgb) {
        this.textColorArgb = textColorArgb;
    }

    public int getStartJumpIndex() {
        return startJumpIndex;
    }

    public void setStartJumpIndex(int startJumpIndex) {
        // Allow -1 to mark an inactive/out-of-range section.
        this.startJumpIndex = startJumpIndex;
    }

    public int getEndJumpIndex() {
        return endJumpIndex;
    }

    public void setEndJumpIndex(int endJumpIndex) {
        // Allow -1 to mark an inactive/out-of-range section.
        this.endJumpIndex = endJumpIndex;
    }

    public void normalizeRange() {
        if (endJumpIndex < startJumpIndex) {
            int t = startJumpIndex;
            startJumpIndex = endJumpIndex;
            endJumpIndex = t;
        }
    }
}