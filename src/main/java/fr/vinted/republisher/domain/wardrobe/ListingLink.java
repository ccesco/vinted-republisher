package fr.vinted.republisher.domain.wardrobe;

import java.net.URI;
import java.util.Objects;
import java.util.Set;

/** A listing as found on the wardrobe: where to open it. */
public record ListingLink(VintedListingId id, URI url) {

    // The link comes from Vinted, opens in Chrome and ends up in a page: nothing else may pass, such as javascript:.
    private static final Set<String> WEB_SCHEMES = Set.of("http", "https");

    public ListingLink {
        Objects.requireNonNull(id);
        Objects.requireNonNull(url);
        if (url.getScheme() == null || !WEB_SCHEMES.contains(url.getScheme())) {
            throw new InvalidListing("link " + url);
        }
    }
}
