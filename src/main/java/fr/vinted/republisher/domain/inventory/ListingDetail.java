package fr.vinted.republisher.domain.inventory;

import fr.vinted.republisher.domain.journal.JournalEntry;
import fr.vinted.republisher.domain.wardrobe.PriceChange;
import fr.vinted.republisher.domain.wardrobe.StatisticsReading;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The listing as the inventory shows it, with every statistics reading and every price of it, most recent first,
 * and the life of the article it belongs to.
 */
public record ListingDetail(InventoryEntry entry, List<StatisticsReading> history, List<JournalEntry> journal,
                            Optional<StatisticsComparison> comparison, List<PriceChange> priceHistory,
                            ListingLifecycle lifecycle) {

    public ListingDetail {
        Objects.requireNonNull(entry);
        history = List.copyOf(history);
        journal = List.copyOf(journal);
        Objects.requireNonNull(comparison);
        priceHistory = List.copyOf(priceHistory);
        Objects.requireNonNull(lifecycle);
    }
}
