package cn.mythicland.mythicmobsaddon.api;

/**
 * Result of reloading the MythicMobs item manager.
 */
public record MythicItemsReloadResult(
        boolean success,
        int itemCount,
        long generation,
        String message
) {
    public MythicItemsReloadResult {
        if (itemCount < 0) throw new IllegalArgumentException("itemCount must not be negative");
        message = message == null ? "" : message;
    }
}
