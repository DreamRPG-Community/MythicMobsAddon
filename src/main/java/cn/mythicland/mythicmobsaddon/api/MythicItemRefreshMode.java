package cn.mythicland.mythicmobsaddon.api;

/**
 * Controls whether a refreshed MM item uses the configured amount or an existing stack amount.
 */
public enum MythicItemRefreshMode {
    /**
     * Use the amount configured by the current MM item definition.
     */
    TEMPLATE,
    /**
     * Keep the amount of the existing stack while replacing its definition.
     */
    EXISTING_INSTANCE
}
