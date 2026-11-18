package fr.vinted.republisher.domain.inventory;

import fr.vinted.republisher.domain.tag.Tag;

import java.util.Objects;
import java.util.Optional;

/** What the member asks to see of the inventory; the search is kept as it is compared. */
public record InventoryCriteria(PresenceFilter presence, BackupFilter backup, String search, InventorySort sort,
                                SortDirection direction, Optional<Tag> tag) {

    public static final int MAXIMUM_SEARCH_LENGTH = 100;

    public InventoryCriteria {
        Objects.requireNonNull(presence);
        Objects.requireNonNull(backup);
        Objects.requireNonNull(sort);
        Objects.requireNonNull(direction);
        Objects.requireNonNull(tag);
        search = search == null ? "" : search;
        if (search.length() > MAXIMUM_SEARCH_LENGTH) {
            throw new IllegalArgumentException("Search longer than " + MAXIMUM_SEARCH_LENGTH + " characters");
        }
        search = SearchText.of(search);
    }

    /** Without tag filter. */
    public InventoryCriteria(PresenceFilter presence, BackupFilter backup, String search, InventorySort sort,
                             SortDirection direction) {
        this(presence, backup, search, sort, direction, Optional.empty());
    }

    public static InventoryCriteria defaults() {
        return new InventoryCriteria(PresenceFilter.PRESENT, BackupFilter.ALL, "", InventorySort.RECENCY,
                SortDirection.DESC);
    }

    /** Present and absent listings, whatever their backup. */
    public static InventoryCriteria everyListing() {
        return new InventoryCriteria(PresenceFilter.ALL, BackupFilter.ALL, "", InventorySort.RECENCY,
                SortDirection.DESC);
    }

    boolean accepts(InventoryEntry entry) {
        return presence.accepts(entry.listing()) && backup.accepts(entry)
                && SearchText.of(entry.listing().title()).contains(search)
                && tag.map(entry.tags()::contains).orElse(true);
    }
}
