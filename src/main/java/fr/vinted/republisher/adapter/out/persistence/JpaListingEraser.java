package fr.vinted.republisher.adapter.out.persistence;

import fr.vinted.republisher.domain.duplicate.ListingEraser;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Erases a listing and every row about it, children first, in one transaction. The journal and the history tables
 * (deletions, republications, sales) are left alone: a listing with history is never erased (US-47).
 */
@Repository
class JpaListingEraser implements ListingEraser {

    private static final List<String> ERASED_IN_ORDER = List.of(
            "DELETE FROM backup_verification WHERE listing_id = :listing",
            "DELETE FROM backup_validation WHERE listing_id = :listing",
            "DELETE FROM listing_photo WHERE listing_id = :listing",
            "DELETE FROM listing_content WHERE listing_id = :listing",
            "DELETE FROM listing_statistics_reading WHERE listing_id = :listing",
            "DELETE FROM listing_price_change WHERE listing_id = :listing",
            // Its article (US-53) goes with it, tags included, unless another listing puts it on sale.
            """
            DELETE FROM article_tag WHERE article_id = (SELECT article_id FROM listing WHERE vinted_id = :listing)
              AND NOT EXISTS (SELECT 1 FROM listing other
                              WHERE other.article_id = article_tag.article_id AND other.vinted_id <> :listing)""",
            """
            WITH erased AS (DELETE FROM listing WHERE vinted_id = :listing RETURNING article_id)
            DELETE FROM article WHERE id IN (SELECT article_id FROM erased)
              AND NOT EXISTS (SELECT 1 FROM listing other WHERE other.article_id = article.id
                                                            AND other.vinted_id <> :listing)""");

    private final EntityManager entityManager;

    JpaListingEraser(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public void erase(VintedListingId listing) {
        ERASED_IN_ORDER.forEach(statement -> entityManager.createNativeQuery(statement)
                .setParameter("listing", listing.value()).executeUpdate());
    }
}
