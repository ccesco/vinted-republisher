package fr.vinted.republisher.fakes;

import fr.vinted.republisher.domain.session.BrowserUnavailable;
import fr.vinted.republisher.domain.session.VintedUnreachable;
import fr.vinted.republisher.domain.wardrobe.ListingLink;
import fr.vinted.republisher.domain.wardrobe.ScanPace;
import fr.vinted.republisher.domain.wardrobe.ScannedListing;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import fr.vinted.republisher.domain.wardrobe.WardrobeListing;
import fr.vinted.republisher.domain.wardrobe.WardrobeReader;
import fr.vinted.republisher.domain.wardrobe.WardrobeVisit;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class FakeWardrobeReader implements WardrobeReader {

    private final Map<ListingLink, ScannedListing> wardrobe = new LinkedHashMap<>();
    private final List<VintedListingId> opened = new ArrayList<>();
    private final List<Duration> pausesBetweenListings = new ArrayList<>();
    private int displayedTotal;
    private int reads;
    private Optional<VintedListingId> refused = Optional.empty();
    private Optional<RuntimeException> failure = Optional.empty();
    private Runnable duringTheReading = () -> { };

    public FakeWardrobeReader holding(ScannedListing... listings) {
        return announcing(listings.length, listings);
    }

    /** A new wardrobe: what was opened or refused so far is forgotten. */
    public FakeWardrobeReader announcing(int total, ScannedListing... listings) {
        wardrobe.clear();
        List.of(listings).forEach(listing -> wardrobe.put(listing.link(), listing));
        displayedTotal = total;
        opened.clear();
        failure = Optional.empty();
        refused = Optional.empty();
        return this;
    }

    public FakeWardrobeReader whereVintedRefusesTheWardrobe() {
        failure = Optional.of(new VintedUnreachable("HTTP 403"));
        return this;
    }

    public FakeWardrobeReader whereVintedRefuses(long listing) {
        refused = Optional.of(new VintedListingId(listing));
        return this;
    }

    public FakeWardrobeReader whereBrowserCannotStart() {
        failure = Optional.of(new BrowserUnavailable("Chrome not found"));
        return this;
    }

    public FakeWardrobeReader whereBrowserFailsUnexpectedly() {
        failure = Optional.of(new IllegalStateException("unexpected browser failure"));
        return this;
    }

    public FakeWardrobeReader during(Runnable action) {
        duringTheReading = action;
        return this;
    }

    public List<VintedListingId> opened() {
        return opened;
    }

    public List<Duration> pausesBetweenListings() {
        return pausesBetweenListings;
    }

    public int reads() {
        return reads;
    }

    @Override
    public WardrobeListing read(ScanPace pace, WardrobeVisit visit) {
        reads++;
        duringTheReading.run();
        failure.ifPresent(reason -> {
            throw reason;
        });
        WardrobeListing found = new WardrobeListing(displayedTotal, List.copyOf(wardrobe.keySet()));
        Iterator<ListingLink> toOpen = visit.listingsToOpen(found).iterator();
        while (toOpen.hasNext()) {
            visit.record(open(toOpen.next()));
            if (toOpen.hasNext()) {
                pausesBetweenListings.add(pace.nextPauseBetweenListings());
            }
        }
        return found;
    }

    private ScannedListing open(ListingLink link) {
        opened.add(link.id());
        if (refused.filter(link.id()::equals).isPresent()) {
            throw new VintedUnreachable("HTTP 403");
        }
        return wardrobe.get(link);
    }
}
