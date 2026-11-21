package fr.vinted.republisher.domain.wardrobe;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Random;
import java.util.UUID;

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

    /** A listing read again at {@code lastSeenAt}, first seen by the tool at {@code firstSeenAt}. */
    public static Listing seenSince(ScannedListing scanned, Instant firstSeenAt, Instant lastSeenAt) {
        return new Listing(scanned.id(), articleOf(scanned.id()), scanned.url(), scanned.title(), scanned.price(), true,
                firstSeenAt, lastSeenAt);
    }

    /** As {@link Listing#seen}, with the same article for the same listing in every call, for comparisons. */
    public static Listing seen(ScannedListing scanned, Instant seenAt) {
        return seenSince(scanned, seenAt, seenAt);
    }

    /** One article per listing, the same in every call. */
    public static ArticleId articleOf(VintedListingId listing) {
        return new ArticleId(new UUID(0, listing.value()));
    }
}
