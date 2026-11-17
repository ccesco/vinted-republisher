package fr.vinted.republisher.domain.wardrobe;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Every statistics reading ever made, never changed nor removed. */
public interface ListingStatisticsRepository {

    /** All or nothing. */
    void addAll(List<StatisticsReading> readings);

    /** The most recent reading of each listing ever read. */
    List<StatisticsReading> findLatest();

    /** Every reading of the listing, most recent first. */
    List<StatisticsReading> historyOf(VintedListingId listing);

    /** When the views and favourites were last read, of any listing, by a scan or a refresh. */
    Optional<Instant> findLastReadAt();
}
