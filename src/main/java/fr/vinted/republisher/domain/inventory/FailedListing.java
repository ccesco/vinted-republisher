package fr.vinted.republisher.domain.inventory;

import fr.vinted.republisher.domain.journal.JournalEntry;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A listing whose last journal entry is a failure, which gives its cause; never one gone from Vinted nor one
 * republished since, as nothing is left to do on it.
 */
public record FailedListing(Listing listing, ListingStage stage, JournalEntry failure) {

    public FailedListing {
        Objects.requireNonNull(listing);
        Objects.requireNonNull(stage);
        Objects.requireNonNull(failure);
    }

    public FailureKind kind() {
        return switch (failure.record().outcome()) {
            case IN_DOUBT -> FailureKind.IN_DOUBT;
            case REFUSED -> FailureKind.REFUSED;
            default -> FailureKind.OTHER;
        };
    }

    /** {@code lastEntries}: the last journal entry of each listing. Most recent failure first, in journal order. */
    public static List<FailedListing> among(List<InventoryEntry> inventory, List<JournalEntry> lastEntries) {
        Map<VintedListingId, InventoryEntry> byListing = inventory.stream()
                .collect(Collectors.toMap(entry -> entry.listing().id(), Function.identity()));
        return lastEntries.stream()
                .filter(entry -> entry.record().isFailure())
                .sorted(Comparator.comparingLong(JournalEntry::id).reversed())
                .flatMap(entry -> failedListing(entry, byListing).stream())
                .toList();
    }

    private static Optional<FailedListing> failedListing(JournalEntry entry,
                                                         Map<VintedListingId, InventoryEntry> byListing) {
        return entry.record().listingId()
                .map(byListing::get)
                .filter(known -> known.stage() != ListingStage.GONE && known.stage() != ListingStage.REPUBLISHED)
                .map(known -> new FailedListing(known.listing(), known.stage(), entry));
    }
}
