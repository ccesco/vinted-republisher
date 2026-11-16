package fr.vinted.republisher.domain.inventory;

import fr.vinted.republisher.domain.journal.JournalEntry;
import fr.vinted.republisher.domain.wardrobe.StatisticsReading;

import java.util.List;
import java.util.Objects;

/** The listing as the inventory shows it, with every statistics reading of it, most recent first. */
public record ListingDetail(InventoryEntry entry, List<StatisticsReading> history, List<JournalEntry> journal) {

    public ListingDetail {
        Objects.requireNonNull(entry);
        history = List.copyOf(history);
        journal = List.copyOf(journal);
    }
}
