package cn.mythicland.mythicmobsaddon.api;

import cn.mythicland.lib.storage.YamlTree;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/** Detailed immutable MM item view. */
public record MythicItemDetails(
        MythicItemSummary summary,
        Map<String, Object> configuration,
        String rawYaml,
        ItemStack preview,
        String revision
) {
    public MythicItemDetails {
        if (summary == null) throw new IllegalArgumentException("summary is required");
        configuration = configuration == null ? Map.of() : YamlTree.immutableMap(configuration);
        rawYaml = rawYaml == null ? "" : rawYaml;
        revision = revision == null ? "" : revision;
        preview = preview == null ? null : preview.clone();
    }

    @Override
    public ItemStack preview() {
        return preview == null ? null : preview.clone();
    }
}
