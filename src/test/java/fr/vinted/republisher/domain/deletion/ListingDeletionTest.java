package fr.vinted.republisher.domain.deletion;

import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static fr.vinted.republisher.domain.backup.BackupFixtures.aBackup;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ListingDeletionTest {

    private static final Instant SAVED_AT = Instant.parse("2026-11-10T10:00:00Z");
    private static final Instant CLICKED_AT = Instant.parse("2026-11-11T10:00:00Z");
    private static final Instant SETTLED_AT = Instant.parse("2026-11-11T10:01:00Z");

    private final ListingDeletion attempt = ListingDeletion.attempted(aBackup(1, SAVED_AT), CLICKED_AT);

    @Test
    void an_attempt_is_unconfirmed_until_settled() {
        assertThat(attempt.listingId()).isEqualTo(new VintedListingId(1));
        assertThat(attempt.backupSavedAt()).isEqualTo(SAVED_AT);
        assertThat(attempt.attemptedAt()).isEqualTo(CLICKED_AT);
        assertThat(attempt.outcome()).isEqualTo(DeletionOutcome.UNCONFIRMED);
        assertThat(attempt.settledAt()).isEmpty();
    }

    @Test
    void a_proven_attempt_is_confirmed() {
        ListingDeletion confirmed = attempt.confirmed(SETTLED_AT);

        assertThat(confirmed.id()).isEqualTo(attempt.id());
        assertThat(confirmed.outcome()).isEqualTo(DeletionOutcome.CONFIRMED);
        assertThat(confirmed.settledAt()).isEqualTo(Optional.of(SETTLED_AT));
    }

    @Test
    void an_attempt_whose_listing_is_still_there_was_not_done() {
        ListingDeletion notDone = attempt.notDone(SETTLED_AT);

        assertThat(notDone.outcome()).isEqualTo(DeletionOutcome.NOT_DONE);
        assertThat(notDone.settledAt()).isEqualTo(Optional.of(SETTLED_AT));
    }

    @Test
    void a_settled_deletion_cannot_be_settled_again() {
        ListingDeletion confirmed = attempt.confirmed(SETTLED_AT);

        assertThatThrownBy(() -> confirmed.notDone(SETTLED_AT)).isInstanceOf(NoUnconfirmedDeletion.class);
        assertThatThrownBy(() -> attempt.notDone(SETTLED_AT).confirmed(SETTLED_AT))
                .isInstanceOf(NoUnconfirmedDeletion.class);
    }

    @Nested
    class New_attempt {

        @Test
        void is_refused_while_the_last_one_is_unconfirmed() {
            assertThatThrownBy(attempt::allowNewAttempt).isInstanceOf(DeletionUnconfirmed.class);
        }

        @Test
        void is_refused_once_the_listing_is_deleted() {
            assertThatThrownBy(() -> attempt.confirmed(SETTLED_AT).allowNewAttempt())
                    .isInstanceOf(ListingAlreadyDeleted.class);
        }

        @Test
        void is_allowed_after_an_attempt_not_done() {
            attempt.notDone(SETTLED_AT).allowNewAttempt();
        }
    }
}
