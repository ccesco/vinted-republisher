package fr.vinted.republisher.domain.wardrobe;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.util.Random;

/** Invented listings: never real data from a wardrobe. */
public final class WardrobeFixtures {

    public static final boolean ACTIVE = true;
    public static final boolean INACTIVE = false;

    private WardrobeFixtures() {
    }

    public static ListingLink aLink(long id) {
        return new ListingLink(new VintedListingId(id), URI.create("https://vinted.test/items/" + id));
    }

    public static ScannedListing anActiveListing(long id) {
        return aListing(id, ACTIVE);
    }

    public static ScannedListing anInactiveListing(long id) {
        return aListing(id, INACTIVE);
    }

    public static ScannedListing aListing(long id, boolean active) {
        return aListing(id, "Pull rayé " + id, "12.50", active);
    }

    public static ScannedListing aListing(long id, String title, String price, boolean active) {
        return new ScannedListing(aLink(id), title, new Price(new BigDecimal(price), "EUR"), active);
    }

    public static ScanPace noPause() {
        PauseRange none = new PauseRange(Duration.ZERO, Duration.ZERO);
        return new ScanPace(none, none, new Random(42));
    }
}
