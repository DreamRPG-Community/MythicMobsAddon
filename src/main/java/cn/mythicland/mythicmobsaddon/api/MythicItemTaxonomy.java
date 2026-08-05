package cn.mythicland.mythicmobsaddon.api;

import java.util.List;

/** Immutable addon-owned item tags. */
public record MythicItemTaxonomy(List<MythicItemTag> tags) {
    public MythicItemTaxonomy {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
