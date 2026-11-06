package fr.vinted.republisher.domain.wardrobe;

import java.math.BigDecimal;
import java.net.URI;

/** Invented listings: never real data from a wardrobe. */
public final class WardrobeFixtures {

    public static final ListingStatusFlags ACTIVE = new ListingStatusFlags(false, false, false, false, false);

    private WardrobeFixtures() {
    }

    public static ScannedListing anActiveListing(long id) {
        return aListing(id, ACTIVE);
    }

    public static ScannedListing aListing(long id, ListingStatusFlags flags) {
        return aListing(id, "Pull rayé " + id, "12.50", flags);
    }

    public static ScannedListing aListing(long id, String title, String price, ListingStatusFlags flags) {
        return new ScannedListing(new VintedListingId(id), URI.create("https://vinted.test/items/" + id),
                title, new Price(new BigDecimal(price), "EUR"), flags);
    }
}
