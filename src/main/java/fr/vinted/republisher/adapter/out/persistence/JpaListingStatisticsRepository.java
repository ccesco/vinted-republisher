package fr.vinted.republisher.adapter.out.persistence;

import fr.vinted.republisher.domain.wardrobe.ListingStatisticsRepository;
import fr.vinted.republisher.domain.wardrobe.StatisticsReading;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
class JpaListingStatisticsRepository implements ListingStatisticsRepository {

    private final ListingStatisticsReadingJpaRepository repository;

    JpaListingStatisticsRepository(ListingStatisticsReadingJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void addAll(List<StatisticsReading> readings) {
        repository.saveAll(readings.stream().map(ListingStatisticsReadingJpaEntity::new).toList());
    }

    @Override
    public List<StatisticsReading> findLatest() {
        return repository.findLatestOfEachListing().stream()
                .map(ListingStatisticsReadingJpaEntity::toStatisticsReading).toList();
    }

    @Override
    public List<StatisticsReading> historyOf(VintedListingId listing) {
        return repository.findByListingIdOrderByReadAtDesc(listing.value()).stream()
                .map(ListingStatisticsReadingJpaEntity::toStatisticsReading).toList();
    }
}
