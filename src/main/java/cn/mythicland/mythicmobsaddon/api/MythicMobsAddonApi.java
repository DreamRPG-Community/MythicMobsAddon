package cn.mythicland.mythicmobsaddon.api;

import org.bukkit.inventory.ItemStack;

import java.util.Optional;

/**
 * Public MythicMobs item API exposed by MythicMobsAddon.
 */
public interface MythicMobsAddonApi {

    MythicItemPage search(MythicItemQuery query);

    Optional<MythicItemDetails> find(String internalName);

    ItemStack getItemStack(String internalName, int amount);

    MythicItemWriteResult create(MythicItemCreateRequest request);

    MythicItemWriteResult update(MythicItemUpdateRequest request);

    MythicItemWriteResult delete(MythicItemDeleteRequest request);

    MythicItemsReloadResult reload();

    MythicItemImportResult previewImport(MythicItemImportRequest request);

    MythicItemImportResult importItems(MythicItemImportRequest request);

    MythicItemEditorCatalog editorCatalog();

    MythicItemTaxonomy taxonomy();
}
