package fr.vinted.republisher.adapter.out.browser;

import fr.vinted.republisher.domain.wardrobe.ScanPace;
import fr.vinted.republisher.domain.wardrobe.WardrobeReader;
import fr.vinted.republisher.domain.wardrobe.WardrobeSnapshot;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** Scrolls through the wardrobe as a member would, and gathers what the page loads on the way. */
public class PlaywrightWardrobeReader implements WardrobeReader {

    private final URI wardrobeUrl;
    private final Duration stallTimeout;
    private final ChromeTabs tabs;
    private final Clock clock;

    public PlaywrightWardrobeReader(URI wardrobeUrl, Duration stallTimeout, ChromeTabs tabs, Clock clock) {
        this.wardrobeUrl = wardrobeUrl;
        this.stallTimeout = stallTimeout;
        this.tabs = tabs;
        this.clock = clock;
    }

    @Override
    public WardrobeSnapshot read(ScanPace pace) {
        return tabs.inNewTab(page -> read(new WardrobePage(page, wardrobeUrl, clock), pace));
    }

    private WardrobeSnapshot read(WardrobePage wardrobe, ScanPace pace) {
        wardrobe.open();
        int displayedTotal = wardrobe.displayedTotal(stallTimeout);
        wardrobe.askForFirstSlice();
        WardrobeSnapshot snapshot = new WardrobeSnapshot(displayedTotal, wardrobe.listingsLoaded());
        Instant lastProgress = clock.instant();
        while (snapshot.listings().size() < displayedTotal && !hasStalled(lastProgress)) {
            wardrobe.scrollDown();
            wardrobe.pause(pace.nextPause());
            WardrobeSnapshot extended = snapshot.with(wardrobe.listingsLoaded());
            if (extended.listings().size() > snapshot.listings().size()) {
                lastProgress = clock.instant();
            }
            snapshot = extended;
        }
        return snapshot;
    }

    private boolean hasStalled(Instant lastProgress) {
        return clock.instant().isAfter(lastProgress.plus(stallTimeout));
    }
}
