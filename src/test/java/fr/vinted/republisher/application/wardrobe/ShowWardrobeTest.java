package fr.vinted.republisher.application.wardrobe;

import fr.vinted.republisher.domain.session.BrowserAction;
import fr.vinted.republisher.domain.session.VintedSession;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.ListingRepository;
import fr.vinted.republisher.domain.wardrobe.ScanProblem;
import fr.vinted.republisher.domain.wardrobe.WardrobeOverview;
import fr.vinted.republisher.domain.wardrobe.WardrobeScan;
import fr.vinted.republisher.fakes.InMemoryListingRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.anActiveListing;
import static org.assertj.core.api.Assertions.assertThat;

class ShowWardrobeTest {

    private static final Instant SCANNED_AT = Instant.parse("2026-11-06T10:00:00Z");

    private final VintedSession session = new VintedSession();
    private final WardrobeScan wardrobeScan = new WardrobeScan();
    private final InMemoryListingRepository listings = new InMemoryListingRepository();
    private final ShowWardrobe showWardrobe = new ShowWardrobe(session, wardrobeScan, listings);

    @Test
    void nothing_is_known_before_the_first_scan() {
        assertThat(showWardrobe.show())
                .isEqualTo(new WardrobeOverview(false, Optional.empty(), Optional.empty(), List.of()));
    }

    @Test
    void shows_present_listings_most_recent_first_with_the_date_of_the_last_scan() {
        Listing older = Listing.seen(anActiveListing(1), SCANNED_AT);
        Listing newer = Listing.seen(anActiveListing(2), SCANNED_AT);
        Listing absent = Listing.seen(anActiveListing(3), SCANNED_AT.minusSeconds(3600)).absent();
        listings.saveAll(List.of(older, absent, newer));

        WardrobeOverview overview = showWardrobe.show();

        assertThat(overview.listings()).containsExactly(newer, older);
        assertThat(overview.lastScanAt()).contains(SCANNED_AT);
    }

    @Test
    void tells_a_scan_is_in_progress() {
        session.start(BrowserAction.SCANNING);

        assertThat(showWardrobe.show().scanInProgress()).isTrue();
    }

    @Test
    void scan_ending_during_the_reading_still_shows_in_progress_so_that_the_page_reads_again() {
        session.start(BrowserAction.SCANNING);
        ListingRepository endingTheScan = new InMemoryListingRepository() {
            @Override
            public List<Listing> findAll() {
                session.finish();
                return super.findAll();
            }
        };

        assertThat(new ShowWardrobe(session, wardrobeScan, endingTheScan).show().scanInProgress()).isTrue();
    }

    @Test
    void another_browser_action_is_not_a_scan_in_progress() {
        session.start(BrowserAction.CHECKING);

        assertThat(showWardrobe.show().scanInProgress()).isFalse();
    }

    @Test
    void shows_the_problem_of_the_last_scan() {
        wardrobeScan.fail(ScanProblem.WARDROBE_UNREADABLE);

        assertThat(showWardrobe.show().problem()).contains(ScanProblem.WARDROBE_UNREADABLE);
    }
}
