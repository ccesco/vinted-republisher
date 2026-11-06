package fr.vinted.republisher.domain.wardrobe;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.ACTIVE;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.aListing;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.anActiveListing;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WardrobeSnapshotTest {

    private static final ListingStatusFlags RESERVED = new ListingStatusFlags(false, false, true, false, false);
    private static final Instant SCANNED_AT = Instant.parse("2026-11-06T10:00:00Z");
    private static final Instant EARLIER = SCANNED_AT.minusSeconds(3600);

    @Nested
    class Completeness {

        @Test
        void complete_when_every_listing_announced_was_read() {
            WardrobeSnapshot snapshot = new WardrobeSnapshot(2, List.of(anActiveListing(1), anActiveListing(2)));

            assertThatCode(snapshot::requireComplete).doesNotThrowAnyException();
        }

        @Test
        void inactive_listings_count_in_the_total() {
            WardrobeSnapshot snapshot = new WardrobeSnapshot(2, List.of(anActiveListing(1), aListing(2, RESERVED)));

            assertThatCode(snapshot::requireComplete).doesNotThrowAnyException();
        }

        @Test
        void unreadable_when_fewer_listings_than_announced_were_read() {
            WardrobeSnapshot snapshot = new WardrobeSnapshot(3, List.of(anActiveListing(1), anActiveListing(2)));

            assertThatThrownBy(snapshot::requireComplete)
                    .isInstanceOf(WardrobeUnreadable.class)
                    .hasMessage("The wardrobe cannot be read: 2 listings read, 3 announced");
        }

        @Test
        void unreadable_when_more_listings_than_announced_were_read() {
            WardrobeSnapshot snapshot = new WardrobeSnapshot(1, List.of(anActiveListing(1), anActiveListing(2)));

            assertThatThrownBy(snapshot::requireComplete).isInstanceOf(WardrobeUnreadable.class);
        }

        @Test
        void unreadable_when_no_listing_was_read_even_if_none_was_announced() {
            WardrobeSnapshot snapshot = new WardrobeSnapshot(0, List.of());

            assertThatThrownBy(snapshot::requireComplete)
                    .isInstanceOf(WardrobeUnreadable.class)
                    .hasMessage("The wardrobe cannot be read: no listing read");
        }

        @Test
        void listing_received_twice_counts_once() {
            WardrobeSnapshot snapshot = new WardrobeSnapshot(1, List.of(anActiveListing(1), anActiveListing(1)));

            assertThat(snapshot.listings()).hasSize(1);
        }

        @Test
        void further_listings_add_up_without_duplicates() {
            WardrobeSnapshot snapshot = new WardrobeSnapshot(2, List.of(anActiveListing(1)))
                    .with(List.of(anActiveListing(1), anActiveListing(2)));

            assertThat(snapshot.listings()).extracting(ScannedListing::id)
                    .containsExactly(new VintedListingId(1), new VintedListingId(2));
        }
    }

    @Nested
    class Applying_to_the_inventory {

        @Test
        void creates_a_listing_seen_for_the_first_time() {
            WardrobeSnapshot snapshot = new WardrobeSnapshot(1, List.of(anActiveListing(1)));

            assertThat(snapshot.applyTo(List.of(), SCANNED_AT))
                    .containsExactly(Listing.seen(anActiveListing(1), SCANNED_AT));
        }

        @Test
        void keeps_active_listings_only() {
            WardrobeSnapshot snapshot = new WardrobeSnapshot(2, List.of(anActiveListing(1), aListing(2, RESERVED)));

            assertThat(snapshot.applyTo(List.of(), SCANNED_AT)).extracting(Listing::id)
                    .containsExactly(new VintedListingId(1));
        }

        @Test
        void updates_a_known_listing_without_duplicating_it() {
            Listing known = Listing.seen(aListing(1, "Ancien titre", "10.00", ACTIVE), EARLIER);
            ScannedListing changed = aListing(1, "Nouveau titre", "8.00", ACTIVE);

            assertThat(new WardrobeSnapshot(1, List.of(changed)).applyTo(List.of(known), SCANNED_AT))
                    .containsExactly(Listing.seen(changed, SCANNED_AT));
        }

        @Test
        void marks_absent_a_known_listing_missing_from_the_wardrobe() {
            Listing known = Listing.seen(anActiveListing(2), EARLIER);

            assertThat(new WardrobeSnapshot(1, List.of(anActiveListing(1))).applyTo(List.of(known), SCANNED_AT))
                    .contains(known.absent());
        }

        @Test
        void marks_absent_a_known_listing_no_longer_active() {
            Listing known = Listing.seen(anActiveListing(1), EARLIER);

            assertThat(new WardrobeSnapshot(1, List.of(aListing(1, RESERVED))).applyTo(List.of(known), SCANNED_AT))
                    .containsExactly(known.absent());
        }

        @Test
        void absent_listing_found_again_is_present_again() {
            Listing absent = Listing.seen(anActiveListing(1), EARLIER).absent();

            assertThat(new WardrobeSnapshot(1, List.of(anActiveListing(1))).applyTo(List.of(absent), SCANNED_AT))
                    .containsExactly(Listing.seen(anActiveListing(1), SCANNED_AT));
        }
    }
}
