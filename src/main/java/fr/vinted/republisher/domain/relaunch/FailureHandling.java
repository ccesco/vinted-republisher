package fr.vinted.republisher.domain.relaunch;

import fr.vinted.republisher.domain.backup.BackupNotVerified;
import fr.vinted.republisher.domain.backup.BackupReplaced;
import fr.vinted.republisher.domain.backup.ListingNotBackedUp;
import fr.vinted.republisher.domain.cadence.CadenceLimitReached;
import fr.vinted.republisher.domain.deletion.BackupNotValidated;
import fr.vinted.republisher.domain.deletion.DeletionUnconfirmed;
import fr.vinted.republisher.domain.deletion.ListingAlreadyDeleted;
import fr.vinted.republisher.domain.deletion.ListingChanged;
import fr.vinted.republisher.domain.deletion.NoUnconfirmedDeletion;
import fr.vinted.republisher.domain.inventory.ListingNotFound;
import fr.vinted.republisher.domain.republication.FieldNotFillable;
import fr.vinted.republisher.domain.republication.ListingAlreadyRepublished;
import fr.vinted.republisher.domain.republication.ListingNotDeleted;
import fr.vinted.republisher.domain.republication.NoRepublicationToVerify;
import fr.vinted.republisher.domain.republication.RepublicationInError;
import fr.vinted.republisher.domain.republication.RepublicationTooEarly;
import fr.vinted.republisher.domain.session.BrowserBusy;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * What to do after a relaunch failed: wait (until a date when it is known), skip the listing, or stop the whole run.
 * A write is never tried again: its doubtful outcome already leaves the listing to check.
 */
public record FailureHandling(Conduct conduct, Optional<Instant> until) {

    public enum Conduct {
        WAIT,
        SKIP_LISTING,
        STOP_RUN
    }

    public FailureHandling {
        Objects.requireNonNull(conduct);
        Objects.requireNonNull(until);
    }

    /**
     * Anything else, expected or not, stops the run: a lost session, an unavailable browser, an unreachable Vinted (a
     * possible anti-bot block) or an unreadable page (Vinted changed it) would make every listing fail.
     */
    public static FailureHandling of(RuntimeException failure) {
        return switch (failure) {
            case CadenceLimitReached limit -> waitUntil(Optional.of(limit.allowedAt()));
            case RepublicationTooEarly tooEarly -> waitUntil(Optional.of(tooEarly.republishableAt()));
            case BrowserBusy busy -> waitUntil(Optional.empty());
            case NotRelaunchable notRelaunchable when notRelaunchable.allowedAt().isPresent() ->
                    waitUntil(notRelaunchable.allowedAt());
            // Refusals that only concern the listing: the member decides on its page.
            case NotRelaunchable _, StageChanged _, ListingNotFound _, ListingNotBackedUp _, BackupReplaced _,
                 BackupNotValidated _, BackupNotVerified _, ListingAlreadyDeleted _, DeletionUnconfirmed _,
                 NoUnconfirmedDeletion _, ListingChanged _, ListingNotDeleted _, ListingAlreadyRepublished _,
                 RepublicationInError _, FieldNotFillable _, NoRepublicationToVerify _ -> skip();
            default -> new FailureHandling(Conduct.STOP_RUN, Optional.empty());
        };
    }

    private static FailureHandling waitUntil(Optional<Instant> until) {
        return new FailureHandling(Conduct.WAIT, until);
    }

    private static FailureHandling skip() {
        return new FailureHandling(Conduct.SKIP_LISTING, Optional.empty());
    }
}
