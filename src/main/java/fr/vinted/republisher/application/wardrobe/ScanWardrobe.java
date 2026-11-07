package fr.vinted.republisher.application.wardrobe;

import fr.vinted.republisher.domain.session.BrowserAction;
import fr.vinted.republisher.domain.session.BrowserUnavailable;
import fr.vinted.republisher.domain.session.VintedSession;
import fr.vinted.republisher.domain.session.VintedUnreachable;
import fr.vinted.republisher.domain.wardrobe.LastScanProblem;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.ListingLink;
import fr.vinted.republisher.domain.wardrobe.ListingRepository;
import fr.vinted.republisher.domain.wardrobe.ScanPace;
import fr.vinted.republisher.domain.wardrobe.ScanProblem;
import fr.vinted.republisher.domain.wardrobe.ScannedListing;
import fr.vinted.republisher.domain.wardrobe.WardrobeListing;
import fr.vinted.republisher.domain.wardrobe.WardrobeReader;
import fr.vinted.republisher.domain.wardrobe.WardrobeScan;
import fr.vinted.republisher.domain.wardrobe.WardrobeScanRepository;
import fr.vinted.republisher.domain.wardrobe.WardrobeUnreadable;
import fr.vinted.republisher.domain.wardrobe.WardrobeVisit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Saves each listing as soon as it is read, so that a failure loses nothing and the next scan resumes. Only a
 * scan that reached its end can tell that a listing has left the wardrobe.
 */
public class ScanWardrobe {
    private static final Logger LOG = LoggerFactory.getLogger(ScanWardrobe.class);

    private final VintedSession session;
    private final LastScanProblem lastProblem;
    private final WardrobeScanRepository scans;
    private final WardrobeReader reader;
    private final ListingRepository listings;
    private final ScanPace pace;
    private final Clock clock;
    private final Executor background;

    public ScanWardrobe(VintedSession session, LastScanProblem lastProblem, WardrobeScanRepository scans,
                        WardrobeReader reader, ListingRepository listings, ScanPace pace, Clock clock,
                        Executor background) {
        this.session = session;
        this.lastProblem = lastProblem;
        this.scans = scans;
        this.reader = reader;
        this.listings = listings;
        this.pace = pace;
        this.clock = clock;
        this.background = background;
    }

    /** Starts right away, so that the scan shows as in progress, then visits Vinted in the background. */
    public void scan() {
        if (!session.start(BrowserAction.SCANNING)) {
            return;
        }
        lastProblem.clear();
        background.execute(this::scanUntilDone);
    }

    private void scanUntilDone() {
        try {
            scanToTheEnd();
        } catch (BrowserUnavailable unavailable) {
            fail(ScanProblem.BROWSER_UNAVAILABLE, unavailable);
        } catch (VintedUnreachable unreachable) {
            fail(ScanProblem.VINTED_UNREACHABLE, unreachable);
        } catch (WardrobeUnreadable unreadable) {
            fail(ScanProblem.WARDROBE_UNREADABLE, unreadable);
        } catch (RuntimeException unexpected) {
            // The scan must end whatever happens; the failure is rethrown for the executor to log it.
            lastProblem.report(ScanProblem.SCAN_FAILED);
            throw unexpected;
        } finally {
            session.finish();
        }
    }

    /** The page only gives the problem: the details are in the logs. */
    private void fail(ScanProblem problem, RuntimeException cause) {
        LOG.warn("Wardrobe scan failed", cause);
        lastProblem.report(problem);
    }

    private void scanToTheEnd() {
        WardrobeScan scan = scans.findUnfinished().orElseGet(this::startScan);
        reader.read(pace, new Visit(scan));
        listings.saveAll(scan.missingFrom(listings.findAll()));
        scans.save(scan.finished(clock.instant()));
    }

    private WardrobeScan startScan() {
        WardrobeScan scan = WardrobeScan.startedAt(clock.instant());
        scans.save(scan);
        return scan;
    }

    private final class Visit implements WardrobeVisit {

        private final WardrobeScan scan;

        private Visit(WardrobeScan scan) {
            this.scan = scan;
        }

        @Override
        public List<ListingLink> listingsToOpen(WardrobeListing wardrobe) {
            wardrobe.requireComplete();
            return scan.linksToOpen(wardrobe.links(), listings.findAll());
        }

        @Override
        public void record(ScannedListing listing) {
            if (listing.active()) {
                listings.saveAll(List.of(Listing.seen(listing, clock.instant())));
            }
        }
    }
}
