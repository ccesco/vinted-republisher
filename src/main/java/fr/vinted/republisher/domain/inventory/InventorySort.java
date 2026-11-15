package fr.vinted.republisher.domain.inventory;

import java.util.Comparator;
import java.util.function.Predicate;

/**
 * What the listings are sorted on. A listing without the value sorted on ends the list whatever the direction;
 * ties put the most recent listing first.
 */
public enum InventorySort {
    RECENCY(entry -> true, Comparator.comparingLong(entry -> entry.listing().id().value())),
    TITLE(entry -> true, Comparator.comparing(entry -> SearchText.of(entry.listing().title()))),
    PRICE(entry -> true, Comparator.comparing(entry -> entry.listing().price().amount())),
    VIEWS(entry -> entry.statistics().isPresent(),
            Comparator.comparingInt(entry -> entry.statistics().orElseThrow().statistics().viewCount())),
    FAVOURITES(entry -> entry.statistics().isPresent(),
            Comparator.comparingInt(entry -> entry.statistics().orElseThrow().statistics().favouriteCount())),
    SAVED_AT(entry -> entry.backup().isPresent(),
            Comparator.comparing(entry -> entry.backup().orElseThrow().savedAt()));

    private final Predicate<InventoryEntry> hasValue;
    private final Comparator<InventoryEntry> values;

    InventorySort(Predicate<InventoryEntry> hasValue, Comparator<InventoryEntry> values) {
        this.hasValue = hasValue;
        this.values = values;
    }

    Comparator<InventoryEntry> order(SortDirection direction) {
        Comparator<InventoryEntry> directed = direction == SortDirection.ASC ? values : values.reversed();
        return Comparator.comparing((InventoryEntry entry) -> !hasValue.test(entry))
                .thenComparing((first, second) -> hasValue.test(first) ? directed.compare(first, second) : 0)
                .thenComparing(RECENCY.values.reversed());
    }
}
