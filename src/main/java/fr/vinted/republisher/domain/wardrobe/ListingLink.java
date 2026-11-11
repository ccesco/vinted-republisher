package fr.vinted.republisher.domain.wardrobe;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** A listing as found on the wardrobe: where to open it, and the statistics its card shows the member, if any. */
public record ListingLink(VintedListingId id, URI url, Optional<ListingStatistics> statistics) {

    // The link comes from Vinted, opens in Chrome and ends up in a page: nothing else may pass, such as javascript:.
    private static final Set<String> WEB_SCHEMES = Set.of("http", "https");

    public ListingLink {
        Objects.requireNonNull(id);
        Objects.requireNonNull(url);
        Objects.requireNonNull(statistics);
        if (url.getScheme() == null || !WEB_SCHEMES.contains(url.getScheme())) {
            throw new InvalidListing("link " + url);
        }
    }

    public ListingLink(VintedListingId id, URI url) {
        this(id, url, Optional.empty());
    }

    public ListingLink showing(ListingStatistics shown) {
        return new ListingLink(id, url, Optional.of(shown));
    }
}
