package fr.vinted.republisher.application.wardrobe;

import fr.vinted.republisher.domain.session.BrowserAction;
import fr.vinted.republisher.domain.session.BrowserUnavailable;
import fr.vinted.republisher.domain.session.VintedSession;
import fr.vinted.republisher.domain.session.VintedUnreachable;
import fr.vinted.republisher.domain.wardrobe.ListingRepository;
import fr.vinted.republisher.domain.wardrobe.ScanPace;
import fr.vinted.republisher.domain.wardrobe.ScanProblem;
import fr.vinted.republisher.domain.wardrobe.WardrobeReader;
import fr.vinted.republisher.domain.wardrobe.LastScanProblem;
import fr.vinted.republisher.domain.wardrobe.WardrobeSnapshot;
import fr.vinted.republisher.domain.wardrobe.WardrobeUnreadable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.concurrent.Executor;

/** Reads the whole wardrobe, then updates the inventory; a scan that does not end well changes nothing. */
public class ScanWardrobe {

    private static final Logger LOG = LoggerFactory.getLogger(ScanWardrobe.class);

    private final VintedSession session;
    private final LastScanProblem lastProblem;
    private final WardrobeReader reader;
    private final ListingRepository listings;
    private final ScanPace pace;
    private final Clock clock;
    private final Executor background;

    public ScanWardrobe(VintedSession session, LastScanProblem lastProblem, WardrobeReader reader,
                        ListingRepository listings, ScanPace pace, Clock clock, Executor background) {
        this.session = session;
        this.lastProblem = lastProblem;
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
            updateInventory();
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
        LOG.warn("Wardrobe scan failed: {}", cause.getMessage());
        lastProblem.report(problem);
    }

    private void updateInventory() {
        WardrobeSnapshot snapshot = reader.read(pace);
        snapshot.requireComplete();
        listings.saveAll(snapshot.applyTo(listings.findAll(), clock.instant()));
    }
}
