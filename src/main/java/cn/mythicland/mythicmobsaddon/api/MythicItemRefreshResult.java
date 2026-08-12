package cn.mythicland.mythicmobsaddon.api;

import org.bukkit.inventory.ItemStack;

import java.util.Objects;

/**
 * Detached result of refreshing one item stack.
 *
 * @param status   refresh result category
 * @param original original stack, when present
 * @param item     replacement or the unchanged stack
 * @param identity hidden MM identity, when present
 */
public record MythicItemRefreshResult(
        MythicItemRefreshStatus status,
        ItemStack original,
        ItemStack item,
        MythicItemIdentity identity
) {

    public MythicItemRefreshResult {
        status = Objects.requireNonNull(status, "status");
        original = cloneItem(original);
        item = cloneItem(item);
        if (status == MythicItemRefreshStatus.UPDATED && item == null) {
            throw new IllegalArgumentException("UPDATED refresh result requires a replacement item");
        }
    }

    private static ItemStack cloneItem(ItemStack value) {
        return value == null ? null : value.clone();
    }

    /**
     * Indicates whether the caller must replace the original stack.
     *
     * @return true only when the current source revision rebuilt the item
     */
    public boolean changed() {
        return status == MythicItemRefreshStatus.UPDATED;
    }

    @Override
    public ItemStack original() {
        return cloneItem(original);
    }

    @Override
    public ItemStack item() {
        return cloneItem(item);
    }
}
