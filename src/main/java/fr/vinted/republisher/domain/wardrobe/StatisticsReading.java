package fr.vinted.republisher.domain.wardrobe;

import java.time.Instant;
import java.util.Objects;

/** The views and favourites of a listing at one moment; kept forever, to follow the listing over time. */
public record StatisticsReading(VintedListingId listingId, ListingStatistics statistics, Instant readAt) {

    public StatisticsReading {
        Objects.requireNonNull(listingId);
        Objects.requireNonNull(statistics);
        Objects.requireNonNull(readAt);
    }
}
