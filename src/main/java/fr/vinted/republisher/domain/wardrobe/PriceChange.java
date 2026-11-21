package fr.vinted.republisher.domain.wardrobe;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** A price a listing had, from the moment the tool first saw it at that price; kept forever. */
public record PriceChange(VintedListingId listingId, Price price, Instant changedAt) {

    public PriceChange {
        Objects.requireNonNull(listingId);
        Objects.requireNonNull(price);
        Objects.requireNonNull(changedAt);
    }

    /**
     * The change to add to the history of its article when the listing is saved: none if its price is the latest one,
     * nor once the history has begun if the listing is absent, its price being then an old one (US-53).
     */
    public static Optional<PriceChange> of(Optional<PriceChange> latest, Listing saved) {
        if (latest.isPresent() && !saved.present()
                || latest.map(PriceChange::price).filter(saved.price()::equals).isPresent()) {
            return Optional.empty();
        }
        return Optional.of(new PriceChange(saved.id(), saved.price(), saved.lastSeenAt()));
    }
}
