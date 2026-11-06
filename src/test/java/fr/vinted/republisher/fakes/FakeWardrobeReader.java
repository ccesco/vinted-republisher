package fr.vinted.republisher.fakes;

import fr.vinted.republisher.domain.session.BrowserUnavailable;
import fr.vinted.republisher.domain.session.VintedUnreachable;
import fr.vinted.republisher.domain.wardrobe.ScanPace;
import fr.vinted.republisher.domain.wardrobe.ScannedListing;
import fr.vinted.republisher.domain.wardrobe.WardrobeReader;
import fr.vinted.republisher.domain.wardrobe.WardrobeSnapshot;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class FakeWardrobeReader implements WardrobeReader {

    private Supplier<WardrobeSnapshot> reading = () -> new WardrobeSnapshot(0, List.of());
    private final List<Duration> pauses = new ArrayList<>();
    private int scrolls;
    private int reads;

    public FakeWardrobeReader holding(ScannedListing... listings) {
        return announcing(listings.length, listings);
    }

    public FakeWardrobeReader announcing(int displayedTotal, ScannedListing... listings) {
        reading = () -> new WardrobeSnapshot(displayedTotal, List.of(listings));
        return this;
    }

    public FakeWardrobeReader scrolling(int times) {
        scrolls = times;
        return this;
    }

    public FakeWardrobeReader whereVintedRefuses() {
        reading = () -> {
            throw new VintedUnreachable("HTTP 403");
        };
        return this;
    }

    public FakeWardrobeReader whereBrowserCannotStart() {
        reading = () -> {
            throw new BrowserUnavailable("Chrome not found");
        };
        return this;
    }

    public FakeWardrobeReader whereBrowserFailsUnexpectedly() {
        reading = () -> {
            throw new IllegalStateException("unexpected browser failure");
        };
        return this;
    }

    public FakeWardrobeReader during(Runnable action) {
        Supplier<WardrobeSnapshot> reading = this.reading;
        this.reading = () -> {
            action.run();
            return reading.get();
        };
        return this;
    }

    public List<Duration> pauses() {
        return pauses;
    }

    public int reads() {
        return reads;
    }

    @Override
    public WardrobeSnapshot read(ScanPace pace) {
        reads++;
        for (int scroll = 0; scroll < scrolls; scroll++) {
            pauses.add(pace.nextPause());
        }
        return reading.get();
    }
}
