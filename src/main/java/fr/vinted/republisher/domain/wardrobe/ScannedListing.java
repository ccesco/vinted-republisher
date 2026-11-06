package fr.vinted.republisher.domain.wardrobe;

import java.net.URI;
import java.util.Objects;
import java.util.Set;

/** A listing as read on Vinted, active or not. */
public record ScannedListing(VintedListingId id, URI url, String title, Price price, ListingStatusFlags flags) {

    // The link comes from Vinted and ends up in a page: nothing else may be stored, such as javascript:.
    private static final Set<String> WEB_SCHEMES = Set.of("http", "https");

    public ScannedListing {
        Objects.requireNonNull(id);
        Objects.requireNonNull(url);
        Objects.requireNonNull(price);
        Objects.requireNonNull(flags);
        if (title == null || title.isBlank()) {
            throw new InvalidListing("blank title on " + id);
        }
        if (url.getScheme() == null || !WEB_SCHEMES.contains(url.getScheme())) {
            throw new InvalidListing("link " + url);
        }
    }

    boolean isActive() {
        return flags.isActive();
    }
}
