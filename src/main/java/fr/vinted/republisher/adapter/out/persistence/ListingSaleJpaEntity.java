package fr.vinted.republisher.adapter.out.persistence;

import fr.vinted.republisher.domain.sale.ListingSale;
import fr.vinted.republisher.domain.sale.SaleOrigin;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.domain.Persistable;

import java.time.Instant;

/** Always new when saved: a sale is inserted, never merged over one already recorded (US-51). */
@Entity
@Table(name = "listing_sale")
class ListingSaleJpaEntity implements Persistable<Long> {

    @Id
    @Column(name = "listing_id", nullable = false)
    private Long listingId;

    @Column(name = "noticed_at", nullable = false)
    private Instant noticedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false)
    private SaleOrigin origin;

    protected ListingSaleJpaEntity() {
    }

    ListingSaleJpaEntity(ListingSale sale) {
        listingId = sale.listingId().value();
        noticedAt = sale.noticedAt();
        origin = sale.origin();
    }

    ListingSale toListingSale() {
        return new ListingSale(new VintedListingId(listingId), noticedAt, origin);
    }

    @Override
    public Long getId() {
        return listingId;
    }

    @Override
    public boolean isNew() {
        return true;
    }
}
