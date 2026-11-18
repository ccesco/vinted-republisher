package fr.vinted.republisher.domain.wardrobe;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;

/**
 * A listing known to the tool. It stays in the inventory when it leaves the wardrobe: later backups hang on it. The
 * date the tool first saw it never changes afterwards.
 */
public record Listing(VintedListingId id, URI url, String title, Price price, boolean present, Instant firstSeenAt,
                      Instant lastSeenAt) {

    public Listing {
        Objects.requireNonNull(id);
        Objects.requireNonNull(url);
        Objects.requireNonNull(title);
        Objects.requireNonNull(price);
        Objects.requireNonNull(firstSeenAt);
        Objects.requireNonNull(lastSeenAt);
    }

    public static Listing seen(ScannedListing scanned, Instant scannedAt) {
        return new Listing(scanned.id(), scanned.url(), scanned.title(), scanned.price(), true, scannedAt, scannedAt);
    }

    public Listing absent() {
        return new Listing(id, url, title, price, false, firstSeenAt, lastSeenAt);
    }
}
