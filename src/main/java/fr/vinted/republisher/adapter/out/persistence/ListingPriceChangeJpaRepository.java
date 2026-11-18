package fr.vinted.republisher.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

interface ListingPriceChangeJpaRepository extends JpaRepository<ListingPriceChangeJpaEntity, Long> {

    List<ListingPriceChangeJpaEntity> findByListingIdOrderByChangedAtDescIdDesc(long listingId);

    @Query(nativeQuery = true, value = """
            SELECT DISTINCT ON (listing_id) id, listing_id, price_amount, price_currency, changed_at
            FROM listing_price_change
            WHERE listing_id IN (:listingIds)
            ORDER BY listing_id, changed_at DESC, id DESC""")
    List<ListingPriceChangeJpaEntity> findLatestOf(Collection<Long> listingIds);
}
