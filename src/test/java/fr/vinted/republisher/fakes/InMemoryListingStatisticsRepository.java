package fr.vinted.republisher.fakes;

import fr.vinted.republisher.domain.wardrobe.ListingStatisticsRepository;
import fr.vinted.republisher.domain.wardrobe.StatisticsReading;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class InMemoryListingStatisticsRepository implements ListingStatisticsRepository {

    private final List<StatisticsReading> readings = new ArrayList<>();

    /** Every reading ever added, in order. */
    public List<StatisticsReading> readings() {
        return List.copyOf(readings);
    }

    @Override
    public void addAll(List<StatisticsReading> added) {
        readings.addAll(added);
    }

    @Override
    public List<StatisticsReading> findLatest() {
        Map<VintedListingId, StatisticsReading> latest = new LinkedHashMap<>();
        readings.forEach(reading -> latest.merge(reading.listingId(), reading,
                (kept, other) -> other.readAt().isAfter(kept.readAt()) ? other : kept));
        return List.copyOf(latest.values());
    }
}
