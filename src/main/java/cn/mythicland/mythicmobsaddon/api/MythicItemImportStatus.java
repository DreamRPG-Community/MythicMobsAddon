package cn.mythicland.mythicmobsaddon.api;

/**
 * Result state for previewing or committing an MM item import.
 */
public enum MythicItemImportStatus {
    PREVIEW,
    IMPORTED,
    CONFLICT,
    INVALID,
    FAILED
}
