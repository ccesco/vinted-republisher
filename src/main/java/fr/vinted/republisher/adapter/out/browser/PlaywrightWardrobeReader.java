package fr.vinted.republisher.adapter.out.browser;

import com.microsoft.playwright.Page;
import fr.vinted.republisher.domain.wardrobe.ListingLink;
import fr.vinted.republisher.domain.wardrobe.ScanPace;
import fr.vinted.republisher.domain.wardrobe.WardrobeListing;
import fr.vinted.republisher.domain.wardrobe.WardrobeReader;
import fr.vinted.republisher.domain.wardrobe.WardrobeVisit;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;

/** Scrolls through the wardrobe, then opens its listings one by one, as a member would, slowly and in one tab. */
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
    public WardrobeListing read(ScanPace pace, WardrobeVisit visit) {
        return tabs.inNewTab(page -> {
            WardrobeListing wardrobe = goThroughWardrobe(page, pace);
            openListings(page, pace, visit, wardrobe);
            return wardrobe;
        });
    }

    private WardrobeListing goThroughWardrobe(Page page, ScanPace pace) {
        WardrobePage wardrobe = new WardrobePage(page, wardrobeUrl);
        wardrobe.open();
        WardrobeListing found = new WardrobeListing(wardrobe.displayedTotal(stallTimeout), wardrobe.listingLinks());
        Instant lastProgress = clock.instant();
        while (!found.allLinksFound() && !hasStalled(lastProgress)) {
            wardrobe.scrollDown();
            ChromeTabs.pause(page, pace.nextPauseBetweenScrolls(), clock);
            WardrobeListing extended = found.with(wardrobe.listingLinks());
            if (extended.links().size() > found.links().size()) {
                lastProgress = clock.instant();
            }
            found = extended;
        }
        return found;
    }

    private boolean hasStalled(Instant lastProgress) {
        return clock.instant().isAfter(lastProgress.plus(stallTimeout));
    }

    private void openListings(Page page, ScanPace pace, WardrobeVisit visit, WardrobeListing wardrobe) {
        Iterator<ListingLink> links = visit.listingsToOpen(wardrobe).iterator();
        while (links.hasNext()) {
            visit.record(ListingPage.open(page, links.next()));
            if (links.hasNext()) {
                ChromeTabs.pause(page, pace.nextPauseBetweenListings(), clock);
            }
        }
    }
}
