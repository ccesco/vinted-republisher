package fr.vinted.republisher.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

interface ListingStatisticsReadingJpaRepository extends JpaRepository<ListingStatisticsReadingJpaEntity, Long> {

    @Query(nativeQuery = true, value = """
            SELECT DISTINCT ON (listing_id) id, listing_id, view_count, favourite_count, read_at
            FROM listing_statistics_reading
            ORDER BY listing_id DESC, read_at DESC""")
    List<ListingStatisticsReadingJpaEntity> findLatestOfEachListing();
}
