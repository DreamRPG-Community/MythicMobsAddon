package cn.mythicland.mythicmobsaddon.api;

/**
 * Result category for one item refresh attempt.
 */
public enum MythicItemRefreshStatus {
    /**
     * The item has no MythicMobsAddon identity marker.
     */
    UNMANAGED,
    /**
     * The marked item already uses the current source revision.
     */
    CURRENT,
    /**
     * The marked item was rebuilt from the current source revision.
     */
    UPDATED,
    /**
     * The marked item identity points to a removed source definition.
     */
    STALE
}
