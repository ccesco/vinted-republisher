package fr.vinted.republisher.domain.wardrobe;

import java.util.List;

/** Every statistics reading ever made, never changed nor removed. */
public interface ListingStatisticsRepository {

    /** All or nothing. */
    void addAll(List<StatisticsReading> readings);

    /** The most recent reading of each listing ever read. */
    List<StatisticsReading> findLatest();
}
