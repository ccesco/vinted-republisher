package fr.vinted.republisher.domain.wardrobe;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.aLink;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.anActiveListing;
import static org.assertj.core.api.Assertions.assertThat;

class WardrobeScanTest {

    private static final Instant STARTED_AT = Instant.parse("2026-11-06T10:00:00Z");
    private static final Instant LATER = STARTED_AT.plusSeconds(60);
    private static final Instant EARLIER = STARTED_AT.minusSeconds(60);

    private final WardrobeScan scan = WardrobeScan.startedAt(STARTED_AT);

    @Test
    void new_scan_is_not_finished() {
        assertThat(scan.finishedAt()).isEmpty();
    }

    @Test
    void finished_scan_keeps_its_identity_and_gives_its_end() {
        WardrobeScan finished = scan.finished(LATER);

        assertThat(finished.id()).isEqualTo(scan.id());
        assertThat(finished.startedAt()).isEqualTo(STARTED_AT);
        assertThat(finished.finishedAt()).contains(LATER);
    }

    @Test
    void two_scans_have_different_identities() {
        assertThat(WardrobeScan.startedAt(STARTED_AT).id()).isNotEqualTo(scan.id());
    }

    @Test
    void links_to_open_leave_out_listings_seen_since_the_start() {
        Listing read = Listing.seen(anActiveListing(1), LATER);
        Listing notYetRead = Listing.seen(anActiveListing(2), EARLIER);

        assertThat(scan.linksToOpen(List.of(aLink(1), aLink(2), aLink(3)), List.of(read, notYetRead)))
                .containsExactly(aLink(2), aLink(3));
    }

    @Test
    void listing_seen_at_the_very_start_is_already_read() {
        Listing read = Listing.seen(anActiveListing(1), STARTED_AT);

        assertThat(scan.linksToOpen(List.of(aLink(1)), List.of(read))).isEmpty();
    }

    @Test
    void present_listing_not_seen_since_the_start_is_missing() {
        Listing missing = Listing.seen(anActiveListing(1), EARLIER);

        assertThat(scan.missingFrom(List.of(missing))).containsExactly(missing.absent());
    }

    @Test
    void listing_seen_since_the_start_is_not_missing() {
        assertThat(scan.missingFrom(List.of(Listing.seen(anActiveListing(1), LATER)))).isEmpty();
    }

    @Test
    void listing_already_absent_is_left_alone() {
        assertThat(scan.missingFrom(List.of(Listing.seen(anActiveListing(1), EARLIER).absent()))).isEmpty();
    }
}
