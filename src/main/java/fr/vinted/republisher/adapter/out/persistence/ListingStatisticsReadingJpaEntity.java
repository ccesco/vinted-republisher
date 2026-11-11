package fr.vinted.republisher.adapter.out.persistence;

import fr.vinted.republisher.domain.wardrobe.ListingStatistics;
import fr.vinted.republisher.domain.wardrobe.StatisticsReading;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "listing_statistics_reading")
class ListingStatisticsReadingJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "listing_id", nullable = false)
    private long listingId;

    @Column(name = "view_count", nullable = false)
    private int viewCount;

    @Column(name = "favourite_count", nullable = false)
    private int favouriteCount;

    @Column(name = "read_at", nullable = false)
    private Instant readAt;

    protected ListingStatisticsReadingJpaEntity() {
    }

    ListingStatisticsReadingJpaEntity(StatisticsReading reading) {
        listingId = reading.listingId().value();
        viewCount = reading.statistics().viewCount();
        favouriteCount = reading.statistics().favouriteCount();
        readAt = reading.readAt();
    }

    StatisticsReading toStatisticsReading() {
        return new StatisticsReading(new VintedListingId(listingId), new ListingStatistics(viewCount, favouriteCount),
                readAt);
    }
}
