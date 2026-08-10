package cn.mythicland.mythicmobsaddon.api;

import java.util.List;

/**
 * Immutable summary of one loaded or diagnosed MythicMobs item.
 *
 * @param internalName MM internal identifier
 * @param relativeFile source file relative to MythicMobs/Items
 * @param managed      whether the entry is in MythicMobsAddon's MM item file
 * @param status       runtime status
 * @param displayName  resolved display name
 * @param materialName resolved material name
 * @param amount       default amount
 * @param warnings     diagnostic warnings
 * @param revision     content revision
 */
public record MythicItemSummary(
        String internalName,
        String relativeFile,
        boolean managed,
        MythicItemStatus status,
        String displayName,
        String materialName,
        int amount,
        List<String> warnings,
        String revision,
        boolean editable,
        MythicItemClassification classification,
        List<String> iconUrls
) {
    public MythicItemSummary(
            String internalName,
            String relativeFile,
            boolean managed,
            MythicItemStatus status,
            String displayName,
            String materialName,
            int amount,
            List<String> warnings,
            String revision
    ) {
        this(
                internalName,
                relativeFile,
                managed,
                status,
                displayName,
                materialName,
                amount,
                warnings,
                revision,
                true,
                MythicItemClassification.defaults(),
                List.of()
        );
    }

    public MythicItemSummary {
        if (internalName == null || internalName.isBlank())
            throw new IllegalArgumentException("internalName is required");
        relativeFile = relativeFile == null ? "" : relativeFile;
        status = status == null ? MythicItemStatus.MISSING : status;
        displayName = displayName == null ? internalName : displayName;
        materialName = materialName == null ? "" : materialName;
        if (amount < 1) amount = 1;
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        revision = revision == null ? "" : revision;
        classification = classification == null ? MythicItemClassification.defaults() : classification;
        iconUrls = iconUrls == null ? List.of() : List.copyOf(iconUrls);
    }
}
