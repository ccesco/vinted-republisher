package fr.vinted.republisher.domain.tag;

import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.util.Objects;

/** A tag a listing bears. */
public record ListingTag(VintedListingId listingId, Tag tag) {

    public ListingTag {
        Objects.requireNonNull(listingId);
        Objects.requireNonNull(tag);
    }
}
