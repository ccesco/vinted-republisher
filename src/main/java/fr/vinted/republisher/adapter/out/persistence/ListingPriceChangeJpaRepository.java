package fr.vinted.republisher.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

interface ListingPriceChangeJpaRepository extends JpaRepository<ListingPriceChangeJpaEntity, Long> {

    /** The history of the listing's article (US-53), most recent first. */
    @Query(nativeQuery = true, value = """
            SELECT change.id, change.listing_id, change.article_id, change.price_amount, change.price_currency,
                   change.changed_at FROM listing_price_change change
            JOIN listing ON listing.article_id = change.article_id
            WHERE listing.vinted_id = :listingId
            ORDER BY change.changed_at DESC, change.id DESC""")
    List<ListingPriceChangeJpaEntity> findHistoryOfArticleOf(long listingId);

    @Query(nativeQuery = true, value = """
            SELECT DISTINCT ON (article_id) id, listing_id, article_id, price_amount, price_currency, changed_at
            FROM listing_price_change
            WHERE article_id IN (:articleIds)
            ORDER BY article_id, changed_at DESC, id DESC""")
    List<ListingPriceChangeJpaEntity> findLatestOf(Collection<UUID> articleIds);
}
