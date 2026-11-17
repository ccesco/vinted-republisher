package fr.vinted.republisher.domain.inventory;

import fr.vinted.republisher.domain.republication.ListingRepublication;
import fr.vinted.republisher.domain.wardrobe.ListingStatistics;
import fr.vinted.republisher.domain.wardrobe.StatisticsReading;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The views and favourites of a listing put on sale by a republication, next to those of the listing it replaces
 * (before: its last reading, when it left Vinted), and their total over the whole chain of republications, a listing
 * never read counting for nothing.
 */
public record StatisticsComparison(VintedListingId previousListing, Instant republishedAt,
                                   Optional<StatisticsReading> before, Optional<StatisticsReading> after,
                                   ListingStatistics cumulated, int previousListingCount) {

    public StatisticsComparison {
        Objects.requireNonNull(previousListing);
        Objects.requireNonNull(republishedAt);
        Objects.requireNonNull(before);
        Objects.requireNonNull(after);
        Objects.requireNonNull(cumulated);
    }

    /**
     * {@code previousLatest}: the last reading of each listing that came before, the one replaced directly first;
     * {@code latest}: the last reading of the listing the republication put on sale.
     */
    public static StatisticsComparison of(ListingRepublication republication,
                                          List<Optional<StatisticsReading>> previousLatest,
                                          Optional<StatisticsReading> latest) {
        if (republication.publishedAt().isEmpty() || previousLatest.isEmpty()) {
            throw new IllegalArgumentException("Only a published republication of a known listing is compared");
        }
        List<ListingStatistics> read = Stream.concat(previousLatest.stream(), Stream.of(latest))
                .flatMap(Optional::stream).map(StatisticsReading::statistics).toList();
        ListingStatistics cumulated = new ListingStatistics(
                read.stream().mapToInt(ListingStatistics::viewCount).sum(),
                read.stream().mapToInt(ListingStatistics::favouriteCount).sum());
        return new StatisticsComparison(republication.listingId(), republication.publishedAt().get(),
                previousLatest.getFirst(), latest, cumulated, previousLatest.size());
    }
}
