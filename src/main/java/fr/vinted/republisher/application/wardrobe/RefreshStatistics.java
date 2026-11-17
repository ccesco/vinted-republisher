package fr.vinted.republisher.application.wardrobe;

import fr.vinted.republisher.application.journal.JournalWriter;
import fr.vinted.republisher.domain.journal.ActionOutcome;
import fr.vinted.republisher.domain.journal.JournaledAction;
import fr.vinted.republisher.domain.session.BrowserAction;
import fr.vinted.republisher.domain.session.BrowserUnavailable;
import fr.vinted.republisher.domain.session.VintedSession;
import fr.vinted.republisher.domain.session.VintedUnreachable;
import fr.vinted.republisher.domain.wardrobe.LastStatisticsRefreshProblem;
import fr.vinted.republisher.domain.wardrobe.ListingLink;
import fr.vinted.republisher.domain.wardrobe.ListingStatisticsRepository;
import fr.vinted.republisher.domain.wardrobe.NoStatisticsShown;
import fr.vinted.republisher.domain.wardrobe.ScanPace;
import fr.vinted.republisher.domain.wardrobe.ScannedListing;
import fr.vinted.republisher.domain.wardrobe.StatisticsRefreshProblem;
import fr.vinted.republisher.domain.wardrobe.WardrobeListing;
import fr.vinted.republisher.domain.wardrobe.WardrobeReader;
import fr.vinted.republisher.domain.wardrobe.WardrobeUnreadable;
import fr.vinted.republisher.domain.wardrobe.WardrobeVisit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * Reads the views and favourites again on the cards of the wardrobe, without opening any listing: minutes instead of
 * a whole scan. Only adds readings, all or nothing; the listings and the scans are the scan's business.
 */
public class RefreshStatistics {

    private static final Logger LOG = LoggerFactory.getLogger(RefreshStatistics.class);

    private final VintedSession session;
    private final LastStatisticsRefreshProblem lastProblem;
    private final WardrobeReader reader;
    private final ListingStatisticsRepository statistics;
    private final ScanPace pace;
    private final Clock clock;
    private final Executor background;
    private final JournalWriter journal;

    public RefreshStatistics(VintedSession session, LastStatisticsRefreshProblem lastProblem, WardrobeReader reader,
                             ListingStatisticsRepository statistics, ScanPace pace, Clock clock, Executor background,
                             JournalWriter journal) {
        this.session = session;
        this.lastProblem = lastProblem;
        this.reader = reader;
        this.statistics = statistics;
        this.pace = pace;
        this.clock = clock;
        this.background = background;
        this.journal = journal;
    }

    /** Starts right away, so that the refresh shows as in progress, then reads Vinted in the background. */
    public void refresh() {
        if (!session.start(BrowserAction.REFRESHING_STATISTICS)) {
            return;
        }
        lastProblem.clear();
        journal.write(JournaledAction.STATISTICS_REFRESH, Optional.empty(), ActionOutcome.STARTED, Optional.empty());
        background.execute(this::refreshUntilDone);
    }

    private void refreshUntilDone() {
        try {
            reader.read(pace, new CardsOnly());
            journal.write(JournaledAction.STATISTICS_REFRESH, Optional.empty(), ActionOutcome.SUCCEEDED,
                    Optional.empty());
        } catch (BrowserUnavailable unavailable) {
            fail(StatisticsRefreshProblem.BROWSER_UNAVAILABLE, unavailable);
        } catch (VintedUnreachable unreachable) {
            fail(StatisticsRefreshProblem.VINTED_UNREACHABLE, unreachable);
        } catch (WardrobeUnreadable unreadable) {
            fail(StatisticsRefreshProblem.WARDROBE_UNREADABLE, unreadable);
        } catch (NoStatisticsShown notShown) {
            fail(StatisticsRefreshProblem.NO_STATISTICS_SHOWN, notShown);
        } catch (RuntimeException unexpected) {
            // The refresh must end whatever happens; the failure is rethrown for the executor to log it.
            report(StatisticsRefreshProblem.REFRESH_FAILED);
            throw unexpected;
        } finally {
            session.finish();
        }
    }

    /** The page only gives the problem: the details are in the logs. */
    private void fail(StatisticsRefreshProblem problem, RuntimeException cause) {
        LOG.warn("Statistics refresh failed", cause);
        report(problem);
    }

    private void report(StatisticsRefreshProblem problem) {
        lastProblem.report(problem);
        journal.write(JournaledAction.STATISTICS_REFRESH, Optional.empty(), ActionOutcome.FAILED,
                Optional.of(problem.name()));
    }

    /** Keeps the statistics of the cards, and opens nothing. */
    private final class CardsOnly implements WardrobeVisit {

        @Override
        public List<ListingLink> listingsToOpen(WardrobeListing wardrobe) {
            wardrobe.requireComplete();
            statistics.addAll(wardrobe.shownStatisticsReadAt(clock.instant()));
            return List.of();
        }

        @Override
        public void record(ScannedListing listing) {
            throw new IllegalStateException("A refresh of the statistics opens no listing");
        }
    }
}
