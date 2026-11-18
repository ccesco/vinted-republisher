package fr.vinted.republisher.adapter.out.persistence;

import fr.vinted.republisher.domain.wardrobe.Price;
import fr.vinted.republisher.domain.wardrobe.PriceChange;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "listing_price_change")
class ListingPriceChangeJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "listing_id", nullable = false)
    private long listingId;

    @Column(name = "price_amount", nullable = false)
    private BigDecimal priceAmount;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "price_currency", nullable = false, length = 3)
    private String priceCurrency;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    protected ListingPriceChangeJpaEntity() {
    }

    ListingPriceChangeJpaEntity(PriceChange change) {
        listingId = change.listingId().value();
        priceAmount = change.price().amount();
        priceCurrency = change.price().currency();
        changedAt = change.changedAt();
    }

    PriceChange toPriceChange() {
        return new PriceChange(new VintedListingId(listingId), new Price(priceAmount, priceCurrency), changedAt);
    }
}
