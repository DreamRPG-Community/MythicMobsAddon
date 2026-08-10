package cn.mythicland.mythicmobsaddon.api;

import java.util.List;

/**
 * Result of a MythicMobs item write operation.
 */
public record MythicItemWriteResult(
        MythicItemMutationStatus status,
        String internalName,
        String message,
        MythicItemDetails item,
        String revision,
        String previousInternalName,
        List<String> affectedFiles
) {
    /**
     * Compatibility constructor for callers compiled against the initial API shape.
     */
    @SuppressWarnings("unused")
    public MythicItemWriteResult(
            MythicItemMutationStatus status,
            String internalName,
            String message,
            MythicItemDetails item,
            String revision
    ) {
        this(status, internalName, message, item, revision, "", List.of());
    }

    public MythicItemWriteResult {
        status = status == null ? MythicItemMutationStatus.FAILED : status;
        internalName = internalName == null ? "" : internalName;
        message = message == null ? "" : message;
        revision = revision == null ? "" : revision;
        previousInternalName = previousInternalName == null ? "" : previousInternalName;
        affectedFiles = affectedFiles == null ? List.of() : List.copyOf(affectedFiles);
    }
}
