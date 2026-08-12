package cn.mythicland.mythicmobsaddon.api;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.*;

/**
 * Published after a successful MM item catalog refresh changes one or more item revisions.
 */
public final class MythicItemsChangedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Set<String> changedItemIds;
    private final Set<String> removedItemIds;
    private final Map<String, String> previousRevisions;
    private final Map<String, String> currentRevisions;
    private final long generation;

    /**
     * Creates a catalog change event.
     *
     * @param changedItemIds    IDs whose revision changed or which were newly added
     * @param removedItemIds    IDs removed from the current catalog
     * @param previousRevisions revisions before the refresh
     * @param currentRevisions  revisions after the refresh
     * @param generation        current monotonic catalog generation
     * @throws NullPointerException     if a collection or map is null
     * @throws IllegalArgumentException if a key, value, or generation is invalid
     */
    public MythicItemsChangedEvent(
            Set<String> changedItemIds,
            Set<String> removedItemIds,
            Map<String, String> previousRevisions,
            Map<String, String> currentRevisions,
            long generation
    ) {
        this.changedItemIds = immutableSet(changedItemIds, "changedItemIds");
        this.removedItemIds = immutableSet(removedItemIds, "removedItemIds");
        this.previousRevisions = immutableMap(previousRevisions, "previousRevisions");
        this.currentRevisions = immutableMap(currentRevisions, "currentRevisions");
        if (generation < 0L) throw new IllegalArgumentException("generation cannot be negative");
        this.generation = generation;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    private static Set<String> immutableSet(Collection<String> values, String fieldName) {
        Objects.requireNonNull(values, fieldName);
        Set<String> copy = new LinkedHashSet<>();
        for (String value : values) copy.add(requireText(value, fieldName));
        return Collections.unmodifiableSet(copy);
    }

    private static Map<String, String> immutableMap(Map<String, String> values, String fieldName) {
        Objects.requireNonNull(values, fieldName);
        Map<String, String> copy = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            String normalizedKey = requireText(key, fieldName + " key");
            String normalizedValue = requireText(value, fieldName + " value");
            copy.put(normalizedKey, normalizedValue);
        });
        return Collections.unmodifiableMap(copy);
    }

    private static String requireText(String value, String fieldName) {
        String text = Objects.requireNonNull(value, fieldName).trim();
        if (text.isBlank()) throw new IllegalArgumentException(fieldName + " cannot be blank");
        return text;
    }

    /**
     * Returns item IDs whose current revision differs from the previous catalog.
     *
     * @return immutable changed ID set
     */
    public Set<String> changedItemIds() {
        return changedItemIds;
    }

    /**
     * Returns item IDs removed from the current catalog.
     *
     * @return immutable removed ID set
     */
    public Set<String> removedItemIds() {
        return removedItemIds;
    }

    /**
     * Returns revisions observed before the catalog refresh.
     *
     * @return immutable previous revision map
     */
    public Map<String, String> previousRevisions() {
        return previousRevisions;
    }

    /**
     * Returns revisions observed after the catalog refresh.
     *
     * @return immutable current revision map
     */
    public Map<String, String> currentRevisions() {
        return currentRevisions;
    }

    /**
     * Returns the monotonic catalog generation that produced this event.
     *
     * @return non-negative catalog generation
     */
    public long generation() {
        return generation;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
}
