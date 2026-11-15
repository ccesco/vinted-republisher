package fr.vinted.republisher.domain.inventory;

import java.util.Objects;

/** What the member asks to see of the inventory; the search is kept as it is compared. */
public record InventoryCriteria(PresenceFilter presence, BackupFilter backup, String search, InventorySort sort,
                                SortDirection direction) {

    public static final int MAXIMUM_SEARCH_LENGTH = 100;

    public InventoryCriteria {
        Objects.requireNonNull(presence);
        Objects.requireNonNull(backup);
        Objects.requireNonNull(sort);
        Objects.requireNonNull(direction);
        search = search == null ? "" : search;
        if (search.length() > MAXIMUM_SEARCH_LENGTH) {
            throw new IllegalArgumentException("Search longer than " + MAXIMUM_SEARCH_LENGTH + " characters");
        }
        search = SearchText.of(search);
    }

    public static InventoryCriteria defaults() {
        return new InventoryCriteria(PresenceFilter.PRESENT, BackupFilter.ALL, "", InventorySort.RECENCY,
                SortDirection.DESC);
    }

    boolean accepts(InventoryEntry entry) {
        return presence.accepts(entry.listing()) && backup.accepts(entry)
                && SearchText.of(entry.listing().title()).contains(search);
    }
}
