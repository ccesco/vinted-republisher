package fr.vinted.republisher.application.wardrobe;

import fr.vinted.republisher.domain.session.BrowserAction;
import fr.vinted.republisher.domain.session.VintedSession;
import fr.vinted.republisher.domain.wardrobe.LastScanProblem;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.ListingRepository;
import fr.vinted.republisher.domain.wardrobe.ListingStatisticsRepository;
import fr.vinted.republisher.domain.wardrobe.WardrobeOverview;
import fr.vinted.republisher.domain.wardrobe.WardrobeScan;
import fr.vinted.republisher.domain.wardrobe.WardrobeScanRepository;

import java.util.Comparator;

public class ShowWardrobe {

    // Vinted's identifiers grow over time: the highest is the most recent listing.
    private static final Comparator<Listing> MOST_RECENT_FIRST =
            Comparator.comparingLong((Listing listing) -> listing.id().value()).reversed();

    private final VintedSession session;
    private final LastScanProblem lastProblem;
    private final ListingRepository listings;
    private final ListingStatisticsRepository statistics;
    private final WardrobeScanRepository scans;

    public ShowWardrobe(VintedSession session, LastScanProblem lastProblem, ListingRepository listings,
                        ListingStatisticsRepository statistics, WardrobeScanRepository scans) {
        this.session = session;
        this.lastProblem = lastProblem;
        this.listings = listings;
        this.statistics = statistics;
        this.scans = scans;
    }

    /** The scan is looked at first: a page told it is over must find its result in the inventory. */
    public WardrobeOverview show() {
        boolean scanInProgress = session.overview().currentAction().filter(BrowserAction.SCANNING::equals).isPresent();
        return new WardrobeOverview(
                scanInProgress,
                scans.findLastFinished().flatMap(WardrobeScan::finishedAt),
                lastProblem.current(),
                listings.findAll().stream().filter(Listing::present).sorted(MOST_RECENT_FIRST).toList(),
                statistics.findLatest());
    }
}
