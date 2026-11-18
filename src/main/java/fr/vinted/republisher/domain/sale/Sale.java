package fr.vinted.republisher.domain.sale;

import fr.vinted.republisher.domain.wardrobe.Price;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.time.Instant;
import java.util.Objects;

/** A sale as shown to the member: its listing's title and last price read, not the amount actually received. */
public record Sale(VintedListingId listingId, String title, Price price, Instant noticedAt) {

    public Sale {
        Objects.requireNonNull(listingId);
        Objects.requireNonNull(title);
        Objects.requireNonNull(price);
        Objects.requireNonNull(noticedAt);
    }
}
