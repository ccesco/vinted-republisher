package fr.vinted.republisher.domain.wardrobe;

public record VintedListingId(long value) {

    public VintedListingId {
        if (value <= 0) {
            throw new InvalidListing("identifier " + value);
        }
    }
}
