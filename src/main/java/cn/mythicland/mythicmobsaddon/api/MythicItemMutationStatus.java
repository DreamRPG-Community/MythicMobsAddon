package cn.mythicland.mythicmobsaddon.api;

/** Result status for a MythicMobs item library mutation. */
public enum MythicItemMutationStatus {
    CREATED,
    UPDATED,
    RENAMED,
    DELETED,
    CONFLICT,
    INVALID,
    FAILED
}
