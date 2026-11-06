package fr.vinted.republisher.adapter.out.persistence;

import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.Price;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;

@Entity
@Table(name = "listing")
class ListingJpaEntity {

    @Id
    @Column(name = "vinted_id")
    private Long vintedId;

    @Column(nullable = false)
    private String url;

    @Column(nullable = false)
    private String title;

    @Column(name = "price_amount", nullable = false)
    private BigDecimal priceAmount;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "price_currency", nullable = false, length = 3)
    private String priceCurrency;

    @Column(nullable = false)
    private boolean present;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    protected ListingJpaEntity() {
    }

    ListingJpaEntity(Listing listing) {
        vintedId = listing.id().value();
        url = listing.url().toString();
        title = listing.title();
        priceAmount = listing.price().amount();
        priceCurrency = listing.price().currency();
        present = listing.present();
        lastSeenAt = listing.lastSeenAt();
    }

    Listing toListing() {
        return new Listing(new VintedListingId(vintedId), URI.create(url), title,
                new Price(priceAmount, priceCurrency), present, lastSeenAt);
    }
}
