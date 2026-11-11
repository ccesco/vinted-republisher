package fr.vinted.republisher.domain.backup;

import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static fr.vinted.republisher.domain.backup.BackupFixtures.aBackup;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.anActiveListing;
import static org.assertj.core.api.Assertions.assertThat;

class WardrobeBackupTest {

    private static final Instant STARTED_AT = Instant.parse("2026-11-07T10:00:00Z");
    private static final Instant EARLIER = STARTED_AT.minusSeconds(60);

    private final WardrobeBackup backup = WardrobeBackup.startedAt(STARTED_AT);

    @Test
    void opens_every_present_listing_never_backed_up() {
        List<VintedListingId> toOpen = backup.listingsToOpen(List.of(present(1), present(2)), List.of());

        assertThat(toOpen).containsExactly(new VintedListingId(1), new VintedListingId(2));
    }

    @Test
    void opens_again_a_listing_backed_up_before_it_started() {
        List<VintedListingId> toOpen = backup.listingsToOpen(List.of(present(1)), List.of(savedAt(1, EARLIER)));

        assertThat(toOpen).containsExactly(new VintedListingId(1));
    }

    @Test
    void skips_a_listing_it_already_backed_up() {
        List<VintedListingId> toOpen = backup.listingsToOpen(List.of(present(1), present(2)),
                List.of(savedAt(1, STARTED_AT)));

        assertThat(toOpen).containsExactly(new VintedListingId(2));
    }

    @Test
    void skips_an_absent_listing() {
        List<VintedListingId> toOpen = backup.listingsToOpen(List.of(present(1).absent()), List.of());

        assertThat(toOpen).isEmpty();
    }

    @Test
    void keeps_its_identity_and_start_when_finished() {
        WardrobeBackup finished = backup.finished(STARTED_AT.plusSeconds(600));

        assertThat(finished.id()).isEqualTo(backup.id());
        assertThat(finished.startedAt()).isEqualTo(STARTED_AT);
        assertThat(finished.finishedAt()).contains(STARTED_AT.plusSeconds(600));
    }

    private static Listing present(long id) {
        return Listing.seen(anActiveListing(id), EARLIER);
    }

    private static ListingBackup savedAt(long id, Instant at) {
        return aBackup(id, at);
    }
}
