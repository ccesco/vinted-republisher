package fr.vinted.republisher.application.journal;

import fr.vinted.republisher.domain.backup.BackupNotVerified;
import fr.vinted.republisher.domain.backup.BackupReplaced;
import fr.vinted.republisher.domain.backup.ListingNotBackedUp;
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

    /** Refusals, with the same codes as the API answers. */
    private static final Map<Class<? extends RuntimeException>, String> REFUSAL_REASONS = Map.ofEntries(
            Map.entry(BackupReplaced.class, "BACKUP_REPLACED"),
            Map.entry(BackupNotVerified.class, "VERIFICATION_FAILED"),
            Map.entry(BackupNotValidated.class, "NOT_VALIDATED"),
            Map.entry(ListingAlreadyDeleted.class, "ALREADY_DELETED"),
            Map.entry(DeletionUnconfirmed.class, "DELETION_UNCONFIRMED"),
            Map.entry(NoUnconfirmedDeletion.class, "NO_UNCONFIRMED_DELETION"),
            Map.entry(ListingChanged.class, "LISTING_CHANGED"),
            Map.entry(ListingPageUnreadable.class, "LISTING_PAGE_UNREADABLE"),
            Map.entry(ListingNotDeleted.class, "NOT_DELETED"),
            Map.entry(RepublicationTooEarly.class, "TOO_EARLY"),
            Map.entry(ListingAlreadyRepublished.class, "ALREADY_REPUBLISHED"),
            Map.entry(RepublicationInError.class, "REPUBLICATION_IN_ERROR"),
            Map.entry(FieldNotFillable.class, "FIELD_NOT_FILLABLE"),
            Map.entry(ListingFormUnreadable.class, "FORM_UNREADABLE"),
            Map.entry(BrowserBusy.class, "BROWSER_BUSY"),
            Map.entry(BrowserUnavailable.class, "BROWSER_UNAVAILABLE"),
            Map.entry(VintedUnreachable.class, "VINTED_UNREACHABLE"),
            Map.entry(SessionDisconnected.class, "SESSION_DISCONNECTED"));

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
     * Runs an action on a listing and journals how it ended: the outcome of its answer, a refusal with its reason, or
     * a failure with {@code failureReason}. A listing never backed up is not an action: nothing is written. The
     * answer and any exception are passed on unchanged.
     */
    public <T> T journaled(JournaledAction action, VintedListingId listing, Supplier<T> work,
                           Function<T, ActionOutcome> outcomeOf, Optional<String> failureReason) {
        T answer;
        try {
            answer = work.get();
        } catch (ListingNotBackedUp nothingToDo) {
            throw nothingToDo;
        } catch (RuntimeException refusalOrFailure) {
            Optional<String> refusal = Optional.ofNullable(REFUSAL_REASONS.get(refusalOrFailure.getClass()));
            write(action, Optional.of(listing), refusal.isPresent() ? ActionOutcome.REFUSED : ActionOutcome.FAILED,
                    refusal.or(() -> failureReason));
            throw refusalOrFailure;
        }
        write(action, Optional.of(listing), outcomeOf.apply(answer), Optional.empty());
        return answer;
    }
}
