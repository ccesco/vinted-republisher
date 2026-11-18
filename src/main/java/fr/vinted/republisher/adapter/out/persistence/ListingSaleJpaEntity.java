package fr.vinted.republisher.adapter.out.persistence;

import fr.vinted.republisher.domain.sale.ListingSale;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "listing_sale")
class ListingSaleJpaEntity {

    @Id
    @Column(name = "listing_id", nullable = false)
    private Long listingId;

    @Column(name = "noticed_at", nullable = false)
    private Instant noticedAt;

    protected ListingSaleJpaEntity() {
    }

    ListingSaleJpaEntity(ListingSale sale) {
        listingId = sale.listingId().value();
        noticedAt = sale.noticedAt();
    }

    ListingSale toListingSale() {
        return new ListingSale(new VintedListingId(listingId), noticedAt);
    }
}
