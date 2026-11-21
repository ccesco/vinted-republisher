package fr.vinted.republisher.domain.wardrobe;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.anActiveListing;
import static org.assertj.core.api.Assertions.assertThat;

class ListingTest {

    private static final Instant SCANNED_AT = Instant.parse("2026-11-06T10:00:00Z");

    @Test
    void listing_seen_by_a_scan_is_present() {
        Listing listing = Listing.seen(anActiveListing(1), SCANNED_AT);

        assertThat(listing.present()).isTrue();
        assertThat(listing.lastSeenAt()).isEqualTo(SCANNED_AT);
    }

    @Test
    void listing_seen_is_first_seen_at_that_reading() {
        Listing listing = Listing.seen(anActiveListing(1), SCANNED_AT);

        assertThat(listing.firstSeenAt()).isEqualTo(SCANNED_AT);
    }

    @Test
    void absent_listing_keeps_the_date_it_was_last_seen() {
        Listing absent = Listing.seen(anActiveListing(1), SCANNED_AT).absent();

        assertThat(absent.present()).isFalse();
        assertThat(absent.lastSeenAt()).isEqualTo(SCANNED_AT);
        assertThat(absent.firstSeenAt()).isEqualTo(SCANNED_AT);
    }

    @Test
    void each_listing_seen_belongs_to_a_new_article() {
        Listing first = Listing.seen(anActiveListing(1), SCANNED_AT);
        Listing second = Listing.seen(anActiveListing(2), SCANNED_AT);

        assertThat(first.articleId()).isNotEqualTo(second.articleId());
        assertThat(first.absent().articleId()).isEqualTo(first.articleId());
    }
}
