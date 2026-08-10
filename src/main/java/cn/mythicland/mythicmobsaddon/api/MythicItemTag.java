package cn.mythicland.mythicmobsaddon.api;

/**
 * Addon-owned multi-select item tag.
 */
public record MythicItemTag(String id, String displayName, String color) {
    public MythicItemTag {
        id = requireId(id);
        displayName = displayName == null || displayName.isBlank() ? id : displayName.trim();
        color = color == null || color.isBlank() ? "#929394" : color.trim();
    }

    private static String requireId(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("tag id is required");
        return value.trim();
    }
}
