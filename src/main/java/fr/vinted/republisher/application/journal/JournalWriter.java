package fr.vinted.republisher.application.journal;

import fr.vinted.republisher.domain.backup.BackupNotVerified;
import fr.vinted.republisher.domain.backup.BackupReplaced;
import fr.vinted.republisher.domain.backup.ListingNotBackedUp;
import fr.vinted.republisher.domain.cadence.CadenceLimitReached;
import fr.vinted.republisher.domain.deletion.BackupNotValidated;
import fr.vinted.republisher.domain.deletion.DeletionUnconfirmed;
import fr.vinted.republisher.domain.deletion.ListingAlreadyDeleted;
import fr.vinted.republisher.domain.deletion.ListingChanged;
import fr.vinted.republisher.domain.deletion.ListingPageUnreadable;
import fr.vinted.republisher.domain.deletion.NoUnconfirmedDeletion;
import fr.vinted.republisher.domain.journal.ActionJournal;
import fr.vinted.republisher.domain.journal.ActionOutcome;
import fr.vinted.republisher.domain.journal.ActionRecord;
import fr.vinted.republisher.domain.journal.JournaledAction;
import fr.vinted.republisher.domain.republication.FieldNotFillable;
import fr.vinted.republisher.domain.republication.ListingAlreadyRepublished;
import fr.vinted.republisher.domain.republication.ListingFormUnreadable;
import fr.vinted.republisher.domain.republication.ListingNotDeleted;
import fr.vinted.republisher.domain.republication.RepublicationInError;
import fr.vinted.republisher.domain.republication.RepublicationTooEarly;
import fr.vinted.republisher.domain.session.BrowserBusy;
import fr.vinted.republisher.domain.session.BrowserUnavailable;
import fr.vinted.republisher.domain.session.SessionDisconnected;
import fr.vinted.republisher.domain.session.VintedUnreachable;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Writes the journal for the use cases. The journal is a witness: failing to write it is logged and never changes the
 * action nor its answer.
 */
public class JournalWriter {

    private static final Logger LOG = LoggerFactory.getLogger(JournalWriter.class);

    /** Refusals before anything is done (the API's 409 and 422), with the same codes as its answers. */
    private static final Map<Class<?>, String> REFUSAL_REASONS = Map.ofEntries(
            Map.entry(BackupReplaced.class, "BACKUP_REPLACED"),
            Map.entry(BackupNotVerified.class, "VERIFICATION_FAILED"),
            Map.entry(BackupNotValidated.class, "NOT_VALIDATED"),
            Map.entry(ListingAlreadyDeleted.class, "ALREADY_DELETED"),
            Map.entry(DeletionUnconfirmed.class, "DELETION_UNCONFIRMED"),
            Map.entry(NoUnconfirmedDeletion.class, "NO_UNCONFIRMED_DELETION"),
            Map.entry(ListingChanged.class, "LISTING_CHANGED"),
            Map.entry(ListingNotDeleted.class, "NOT_DELETED"),
            Map.entry(RepublicationTooEarly.class, "TOO_EARLY"),
            Map.entry(ListingAlreadyRepublished.class, "ALREADY_REPUBLISHED"),
            Map.entry(RepublicationInError.class, "REPUBLICATION_IN_ERROR"),
            Map.entry(FieldNotFillable.class, "FIELD_NOT_FILLABLE"),
            Map.entry(BrowserBusy.class, "BROWSER_BUSY"),
            Map.entry(CadenceLimitReached.class, "CADENCE_LIMIT_REACHED"));

    /** A republication refuses a backup not verified with its own code, as its API answer does. */
    private static final Map<Class<?>, String> REPUBLICATION_REFUSAL_REASONS =
            Map.of(BackupNotVerified.class, "PHOTOS_NOT_VERIFIED");

    /** Failures on the browser's or Vinted's side (the API's 502), with the same codes as its answers. */
    private static final Map<Class<?>, String> FAILURE_REASONS = Map.of(
            BrowserUnavailable.class, "BROWSER_UNAVAILABLE",
            VintedUnreachable.class, "VINTED_UNREACHABLE",
            SessionDisconnected.class, "SESSION_DISCONNECTED",
            ListingPageUnreadable.class, "LISTING_PAGE_UNREADABLE",
            ListingFormUnreadable.class, "FORM_UNREADABLE");

    private final ActionJournal journal;
    private final Clock clock;

    public JournalWriter(ActionJournal journal, Clock clock) {
        this.journal = journal;
        this.clock = clock;
    }

    public void write(JournaledAction action, Optional<VintedListingId> listing, ActionOutcome outcome,
                      Optional<String> reason) {
        try {
            journal.write(new ActionRecord(clock.instant(), action, outcome, listing, reason));
        } catch (RuntimeException failure) {
            LOG.error("Journal not written: {} {} {} {}", action, listing, outcome, reason, failure);
        }
    }

    /**
     * Runs an action on a listing and journals how it ended: the outcome of its answer, a refusal with its reason, a
     * failure on the browser's or Vinted's side with its reason, or an unexpected failure with {@code failureReason}.
     * A listing never backed up is not an action: nothing is written. The answer and any exception are passed on
     * unchanged.
     */
    public <T> T journaled(JournaledAction action, VintedListingId listing, Supplier<T> work,
                           Function<T, ActionOutcome> outcomeOf, Optional<String> failureReason) {
        T answer;
        try {
            answer = work.get();
        } catch (ListingNotBackedUp nothingToDo) {
            throw nothingToDo;
        } catch (RuntimeException refusalOrFailure) {
            Optional<String> refusal = reasonOf(refusalOrFailure, action == JournaledAction.REPUBLICATION
                    ? REPUBLICATION_REFUSAL_REASONS : Map.of())
                    .or(() -> reasonOf(refusalOrFailure, REFUSAL_REASONS));
            if (refusal.isPresent()) {
                write(action, Optional.of(listing), ActionOutcome.REFUSED, refusal);
            } else {
                write(action, Optional.of(listing), ActionOutcome.FAILED,
                        reasonOf(refusalOrFailure, FAILURE_REASONS).or(() -> failureReason));
            }
            throw refusalOrFailure;
        }
        try {
            write(action, Optional.of(listing), outcomeOf.apply(answer), Optional.empty());
        } catch (RuntimeException unknownOutcome) {
            LOG.error("Journal not written: outcome of {} on listing {} unknown", action, listing.value(),
                    unknownOutcome);
        }
        return answer;
    }

    /** The reason of the exception's class, or of the closest superclass that has one. */
    private static Optional<String> reasonOf(RuntimeException exception, Map<Class<?>, String> reasons) {
        for (Class<?> type = exception.getClass(); type != null; type = type.getSuperclass()) {
            if (reasons.containsKey(type)) {
                return Optional.of(reasons.get(type));
            }
        }
        return Optional.empty();
    }
}
