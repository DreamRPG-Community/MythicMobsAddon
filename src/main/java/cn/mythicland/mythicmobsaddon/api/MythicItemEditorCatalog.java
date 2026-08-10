package cn.mythicland.mythicmobsaddon.api;

import cn.mythicland.lib.material.EnchantmentEntry;
import cn.mythicland.lib.material.MaterialEntry;

import java.util.List;

/**
 * Immutable data required by the browser editor.
 */
public record MythicItemEditorCatalog(
        List<MaterialEntry> materials,
        List<EnchantmentEntry> enchantments,
        List<String> itemFlags,
        MythicItemTaxonomy taxonomy
) {
    public MythicItemEditorCatalog {
        materials = materials == null ? List.of() : List.copyOf(materials);
        enchantments = enchantments == null ? List.of() : List.copyOf(enchantments);
        itemFlags = itemFlags == null ? List.of() : List.copyOf(itemFlags);
        taxonomy = taxonomy == null ? new MythicItemTaxonomy(List.of()) : taxonomy;
    }
}
