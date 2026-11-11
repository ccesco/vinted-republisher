package fr.vinted.republisher.application.wardrobe;

import fr.vinted.republisher.domain.session.BrowserAction;
import fr.vinted.republisher.domain.session.VintedSession;
import fr.vinted.republisher.domain.wardrobe.LastScanProblem;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.ListingStatistics;
import fr.vinted.republisher.domain.wardrobe.StatisticsReading;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import fr.vinted.republisher.domain.wardrobe.ListingRepository;
import fr.vinted.republisher.domain.wardrobe.ScanProblem;
import fr.vinted.republisher.domain.wardrobe.WardrobeOverview;
import fr.vinted.republisher.domain.wardrobe.WardrobeScan;
import fr.vinted.republisher.fakes.InMemoryListingRepository;
import fr.vinted.republisher.fakes.InMemoryListingStatisticsRepository;
import fr.vinted.republisher.fakes.InMemoryWardrobeScanRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.anActiveListing;
import static org.assertj.core.api.Assertions.assertThat;

class ShowWardrobeTest {

    private static final Instant STARTED_AT = Instant.parse("2026-11-06T09:30:00Z");
    private static final Instant FINISHED_AT = Instant.parse("2026-11-06T10:00:00Z");

    private final VintedSession session = new VintedSession();
    private final LastScanProblem lastProblem = new LastScanProblem();
    private final InMemoryListingRepository listings = new InMemoryListingRepository();
    private final InMemoryListingStatisticsRepository statistics = new InMemoryListingStatisticsRepository();
    private final InMemoryWardrobeScanRepository scans = new InMemoryWardrobeScanRepository();
    private final ShowWardrobe showWardrobe = new ShowWardrobe(session, lastProblem, listings, statistics, scans);

    @Test
    void nothing_is_known_before_the_first_scan() {
        assertThat(showWardrobe.show())
                .isEqualTo(new WardrobeOverview(false, Optional.empty(), Optional.empty(), List.of(), List.of()));
    }

    @Test
    void shows_the_latest_statistics_reading_of_each_listing() {
        StatisticsReading older =
                new StatisticsReading(new VintedListingId(1), new ListingStatistics(12, 3), STARTED_AT);
        StatisticsReading latest =
                new StatisticsReading(new VintedListingId(1), new ListingStatistics(15, 3), FINISHED_AT);
        statistics.addAll(List.of(older, latest));

        assertThat(showWardrobe.show().latestStatistics()).containsExactly(latest);
    }

    @Test
    void shows_present_listings_most_recent_first() {
        Listing older = Listing.seen(anActiveListing(1), FINISHED_AT);
        Listing newer = Listing.seen(anActiveListing(2), FINISHED_AT);
        Listing absent = Listing.seen(anActiveListing(3), STARTED_AT).absent();
        listings.saveAll(List.of(older, absent, newer));

        assertThat(showWardrobe.show().listings()).containsExactly(newer, older);
    }

    @Test
    void date_of_the_last_scan_is_the_end_of_the_last_finished_one() {
        scans.save(WardrobeScan.startedAt(STARTED_AT).finished(FINISHED_AT));

        assertThat(showWardrobe.show().lastScanAt()).contains(FINISHED_AT);
    }

    @Test
    void unfinished_scan_gives_no_date() {
        scans.save(WardrobeScan.startedAt(STARTED_AT));
        listings.saveAll(List.of(Listing.seen(anActiveListing(1), FINISHED_AT)));

        assertThat(showWardrobe.show().lastScanAt()).isEmpty();
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

        assertThat(new ShowWardrobe(session, lastProblem, endingTheScan, statistics, scans).show().scanInProgress()).isTrue();
    }

    @Test
    void another_browser_action_is_not_a_scan_in_progress() {
        session.start(BrowserAction.CHECKING);

        assertThat(showWardrobe.show().scanInProgress()).isFalse();
    }

    @Test
    void shows_the_problem_of_the_last_scan() {
        lastProblem.report(ScanProblem.WARDROBE_UNREADABLE);

        assertThat(showWardrobe.show().problem()).contains(ScanProblem.WARDROBE_UNREADABLE);
    }
}
