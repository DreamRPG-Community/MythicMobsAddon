package cn.mythicland.mythicmobsaddon.api;

import java.util.Objects;

/**
 * Hidden identity attached to an item materialized by MythicMobsAddon.
 *
 * @param internalName MM item internal name
 * @param revision     revision of the source configuration
 */
public record MythicItemIdentity(String internalName, String revision) {

    public MythicItemIdentity {
        internalName = requireText(internalName, "internalName");
        revision = requireText(revision, "revision");
    }

    private static String requireText(String value, String fieldName) {
        String text = Objects.requireNonNull(value, fieldName).trim();
        if (text.isBlank()) throw new IllegalArgumentException(fieldName + " cannot be blank");
        return text;
    }
}
