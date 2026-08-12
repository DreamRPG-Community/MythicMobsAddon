package cn.mythicland.mythicmobsaddon.api;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MythicItemSyncModelTest {

    @Test
    void refreshResultDetachesStacks() {
        ItemStack original = new ItemStack(Material.STONE, 3);
        ItemStack replacement = new ItemStack(Material.DIAMOND, 2);
        MythicItemRefreshResult result = new MythicItemRefreshResult(
                MythicItemRefreshStatus.UPDATED,
                original,
                replacement,
                new MythicItemIdentity("test_item", "revision")
        );

        assertNotSame(original, result.original());
        assertNotSame(replacement, result.item());
        assertEquals(3, result.original().getAmount());
        assertEquals("test_item", result.identity().internalName());

        result.item().setAmount(1);
        assertEquals(2, result.item().getAmount());
    }

    @Test
    void changedEventCopiesCollections() {
        MythicItemsChangedEvent event = new MythicItemsChangedEvent(
                Set.of("new_item"),
                Set.of("old_item"),
                Map.of("old_item", "old-revision"),
                Map.of("new_item", "new-revision"),
                4L
        );

        assertEquals(Set.of("new_item"), event.changedItemIds());
        assertEquals(Set.of("old_item"), event.removedItemIds());
        assertEquals(4L, event.generation());
    }

    @Test
    void changedEventRejectsInvalidRevisionEntries() {
        assertThrows(IllegalArgumentException.class, () -> new MythicItemsChangedEvent(
                Set.of("item"),
                Set.of(),
                Map.of("item", " "),
                Map.of("item", "revision"),
                1L
        ));
    }

    @Test
    void updatedRefreshResultRequiresReplacement() {
        assertThrows(IllegalArgumentException.class, () -> new MythicItemRefreshResult(
                MythicItemRefreshStatus.UPDATED,
                null,
                null,
                new MythicItemIdentity("item", "revision")
        ));
    }
}
