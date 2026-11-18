package fr.vinted.republisher.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

interface ListingStatisticsReadingJpaRepository extends JpaRepository<ListingStatisticsReadingJpaEntity, Long> {

    @Query(nativeQuery = true, value = """
            SELECT DISTINCT ON (listing_id) id, listing_id, view_count, favourite_count, read_at
            FROM listing_statistics_reading
            ORDER BY listing_id DESC, read_at DESC""")
    List<ListingStatisticsReadingJpaEntity> findLatestOfEachListing();

    @Query(nativeQuery = true, value = """
            SELECT DISTINCT ON (listing_id) id, listing_id, view_count, favourite_count, read_at
            FROM listing_statistics_reading
            WHERE read_at <= :at
            ORDER BY listing_id DESC, read_at DESC""")
    List<ListingStatisticsReadingJpaEntity> findLatestOfEachListingAsOf(@Param("at") Instant at);

    List<ListingStatisticsReadingJpaEntity> findByListingIdOrderByReadAtDesc(long listingId);

    @Query("select max(reading.readAt) from ListingStatisticsReadingJpaEntity reading")
    Optional<Instant> findLastReadAt();
}
