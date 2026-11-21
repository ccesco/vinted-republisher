package fr.vinted.republisher.adapter.out.persistence;

import fr.vinted.republisher.domain.sale.SaleOrigin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;

interface ListingSaleJpaRepository extends JpaRepository<ListingSaleJpaEntity, Long> {

    /** One statement, so two concurrent notes cannot both pass: the number of rows inserted, 0 if already sold. */
    @Modifying
    @Query(value = "INSERT INTO listing_sale (listing_id, noticed_at, origin) VALUES (:listingId, :noticedAt, :origin)"
            + " ON CONFLICT (listing_id) DO NOTHING", nativeQuery = true)
    int insertIfAbsent(Long listingId, Instant noticedAt, String origin);

    @Modifying
    @Query("DELETE FROM ListingSaleJpaEntity sale WHERE sale.listingId = :listingId AND sale.origin = :origin")
    int deleteByListingIdAndOrigin(Long listingId, SaleOrigin origin);
}
