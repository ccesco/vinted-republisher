package fr.vinted.republisher.application.wardrobe;

import fr.vinted.republisher.domain.session.BrowserAction;
import fr.vinted.republisher.domain.session.VintedSession;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.ListingRepository;
import fr.vinted.republisher.domain.wardrobe.WardrobeOverview;
import fr.vinted.republisher.domain.wardrobe.LastScanProblem;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class ShowWardrobe {

    // Vinted's identifiers grow over time: the highest is the most recent listing.
    private static final Comparator<Listing> MOST_RECENT_FIRST =
            Comparator.comparingLong((Listing listing) -> listing.id().value()).reversed();

    private final VintedSession session;
    private final LastScanProblem lastProblem;
    private final ListingRepository listings;

    public ShowWardrobe(VintedSession session, LastScanProblem lastProblem, ListingRepository listings) {
        this.session = session;
        this.lastProblem = lastProblem;
        this.listings = listings;
    }

    /** The scan is looked at first: a page told it is over must find its result in the inventory. */
    public WardrobeOverview show() {
        boolean scanInProgress = session.overview().currentAction().filter(BrowserAction.SCANNING::equals).isPresent();
        List<Listing> inventory = listings.findAll();
        return new WardrobeOverview(
                scanInProgress,
                lastScanAt(inventory),
                lastProblem.current(),
                inventory.stream().filter(Listing::present).sorted(MOST_RECENT_FIRST).toList());
    }

    /** The last scan that found an active listing: every such scan stamps the listings it keeps. */
    private static Optional<Instant> lastScanAt(List<Listing> inventory) {
        return inventory.stream().map(Listing::lastSeenAt).max(Comparator.naturalOrder());
    }
}
