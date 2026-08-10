package cn.mythicland.mythicmobsaddon.api;

/**
 * Request to delete one MythicMobs item library configuration.
 */
public record MythicItemDeleteRequest(String internalName, String expectedRevision, boolean confirmExternalMutation) {
    /**
     * Compatibility constructor for callers compiled against the initial API shape.
     */
    @SuppressWarnings("unused")
    public MythicItemDeleteRequest(String internalName, String expectedRevision) {
        this(internalName, expectedRevision, false);
    }

    public MythicItemDeleteRequest {
        internalName = MythicItemCreateRequest.requireName(internalName);
        expectedRevision = expectedRevision == null ? "" : expectedRevision.trim();
    }
}
