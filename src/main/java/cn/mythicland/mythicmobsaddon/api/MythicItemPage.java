package cn.mythicland.mythicmobsaddon.api;

import java.util.List;

/**
 * Paged immutable MythicMobs item result.
 */
public record MythicItemPage(
        List<MythicItemSummary> items,
        int page,
        int pageSize,
        int total,
        long generation
) {
    public MythicItemPage {
        items = items == null ? List.of() : List.copyOf(items);
        if (page < 0) throw new IllegalArgumentException("page must not be negative");
        if (pageSize < 1) throw new IllegalArgumentException("pageSize must be positive");
        if (total < 0) throw new IllegalArgumentException("total must not be negative");
    }
}
