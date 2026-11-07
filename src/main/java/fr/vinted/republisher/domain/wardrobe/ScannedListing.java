package fr.vinted.republisher.domain.wardrobe;

import java.net.URI;
import java.util.Objects;

/** A listing as read on its page on Vinted, active or not. */
public record ScannedListing(ListingLink link, String title, Price price, boolean active) {

    public ScannedListing {
        Objects.requireNonNull(link);
        Objects.requireNonNull(price);
        if (title == null || title.isBlank()) {
            throw new InvalidListing("blank title on " + link.id());
        }
    }

    public VintedListingId id() {
        return link.id();
    }

    public URI url() {
        return link.url();
    }
}
