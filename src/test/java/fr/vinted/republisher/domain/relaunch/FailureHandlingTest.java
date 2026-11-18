package fr.vinted.republisher.domain.relaunch;

import fr.vinted.republisher.domain.backup.BackupReplaced;
import fr.vinted.republisher.domain.backup.ListingFormField;
import fr.vinted.republisher.domain.backup.ListingNotBackedUp;
import fr.vinted.republisher.domain.backup.ListingUnreadable;
import fr.vinted.republisher.domain.cadence.CadenceLimitReached;
import fr.vinted.republisher.domain.deletion.BackupNotValidated;
import fr.vinted.republisher.domain.deletion.DeletionUnconfirmed;
import fr.vinted.republisher.domain.deletion.ListingAlreadyDeleted;
import fr.vinted.republisher.domain.deletion.ListingChanged;
import fr.vinted.republisher.domain.deletion.ListingPageUnreadable;
import fr.vinted.republisher.domain.deletion.NoUnconfirmedDeletion;
import fr.vinted.republisher.domain.inventory.ListingNotFound;
import fr.vinted.republisher.domain.inventory.ListingStage;
import fr.vinted.republisher.domain.relaunch.FailureHandling.Conduct;
import fr.vinted.republisher.domain.republication.FieldNotFillable;
import fr.vinted.republisher.domain.republication.ListingAlreadyRepublished;
import fr.vinted.republisher.domain.republication.ListingFormUnreadable;
import fr.vinted.republisher.domain.republication.ListingNotDeleted;
import fr.vinted.republisher.domain.republication.NoRepublicationToVerify;
import fr.vinted.republisher.domain.republication.RepublicationInError;
import fr.vinted.republisher.domain.republication.RepublicationTooEarly;
import fr.vinted.republisher.domain.session.BrowserBusy;
import fr.vinted.republisher.domain.session.BrowserUnavailable;
import fr.vinted.republisher.domain.session.SessionDisconnected;
import fr.vinted.republisher.domain.session.VintedUnreachable;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import fr.vinted.republisher.domain.wardrobe.WardrobeUnreadable;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** US-17: each failure of a relaunch says whether to wait, skip the listing or stop the run. */
class FailureHandlingTest {

    private static final VintedListingId LISTING = new VintedListingId(1);
    private static final Instant AT_10_02 = Instant.parse("2026-11-11T10:02:00Z");
    private static final Instant AT_10_05 = Instant.parse("2026-11-11T10:05:00Z");

    @Test
    void waits_for_cadence_republication_date_or_busy_browser() {
        assertThat(FailureHandling.of(new CadenceLimitReached(AT_10_02)))
                .isEqualTo(new FailureHandling(Conduct.WAIT, Optional.of(AT_10_02)));
        assertThat(FailureHandling.of(new NotRelaunchable(ListingStage.WAITING, Optional.of(AT_10_05))))
                .isEqualTo(new FailureHandling(Conduct.WAIT, Optional.of(AT_10_05)));
        assertThat(FailureHandling.of(new RepublicationTooEarly(AT_10_05)))
                .isEqualTo(new FailureHandling(Conduct.WAIT, Optional.of(AT_10_05)));
        assertThat(FailureHandling.of(new BrowserBusy()))
                .isEqualTo(new FailureHandling(Conduct.WAIT, Optional.empty()));
    }

    @Test
    void skips_the_listing_on_a_refusal_of_its_own() {
        Stream.of(new StageChanged(ListingStage.TO_DELETE, ListingStage.DELETION_TO_CHECK), new ListingNotFound(LISTING),
                        new NotRelaunchable(ListingStage.TO_VALIDATE, Optional.empty()),
                        new ListingNotBackedUp(LISTING), new BackupReplaced(LISTING, AT_10_02),
                        new BackupNotValidated(LISTING), new ListingAlreadyDeleted(LISTING),
                        new DeletionUnconfirmed(LISTING), new NoUnconfirmedDeletion(LISTING),
                        new ListingChanged(List.of()), new ListingNotDeleted(LISTING),
                        new ListingAlreadyRepublished(LISTING), new RepublicationInError(LISTING),
                        new FieldNotFillable(ListingFormField.SIZE, Optional.of(588L)),
                        new NoRepublicationToVerify(LISTING))
                .forEach(failure -> assertThat(FailureHandling.of(failure)).as(failure.getClass().getSimpleName())
                        .isEqualTo(new FailureHandling(Conduct.SKIP_LISTING, Optional.empty())));
    }

    @Test
    void stops_the_run_on_a_problem_of_the_account_or_of_vinted() {
        Stream.of(new SessionDisconnected("login page"), new BrowserUnavailable("no Chrome"),
                        new VintedUnreachable("HTTP 403"), new ListingPageUnreadable("no delete button"),
                        new ListingFormUnreadable("no title field"), new ListingUnreadable("no photo"),
                        new WardrobeUnreadable("no link found"), new IllegalStateException("unexpected"))
                .forEach(failure -> assertThat(FailureHandling.of(failure)).as(failure.getClass().getSimpleName())
                        .isEqualTo(new FailureHandling(Conduct.STOP_RUN, Optional.empty())));
    }
}
