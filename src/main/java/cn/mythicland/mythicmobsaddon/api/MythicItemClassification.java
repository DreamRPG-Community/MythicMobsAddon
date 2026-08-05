package cn.mythicland.mythicmobsaddon.api;

import java.util.List;

/** Addon-owned tag metadata for one MM item. */
public record MythicItemClassification(List<String> tagIds) {
    public MythicItemClassification {
        tagIds = tagIds == null ? List.of() : tagIds.stream()
                .filter(tag -> tag != null && !tag.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    public static MythicItemClassification defaults() {
        return new MythicItemClassification(List.of());
    }
}
