package cn.mythicland.mythicmobsaddon.api;

import cn.mythicland.lib.storage.YamlTree;

import java.util.Map;

/**
 * Request to create one item in the MythicMobs item library.
 */
public record MythicItemCreateRequest(
        String internalName,
        Map<String, Object> configuration,
        MythicItemClassification classification
) {
    public MythicItemCreateRequest(String internalName, Map<String, Object> configuration) {
        this(internalName, configuration, MythicItemClassification.defaults());
    }

    public MythicItemCreateRequest {
        internalName = requireName(internalName);
        configuration = configuration == null ? Map.of() : YamlTree.immutableMap(configuration);
        classification = classification == null ? MythicItemClassification.defaults() : classification;
    }

    static String requireName(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("internalName is required");
        return value.trim();
    }
}
