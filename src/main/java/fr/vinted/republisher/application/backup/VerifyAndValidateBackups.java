package fr.vinted.republisher.application.backup;

import fr.vinted.republisher.application.inventory.ShowListingInventory;
import fr.vinted.republisher.application.journal.JournalWriter;
import fr.vinted.republisher.domain.backup.BackupPace;
import fr.vinted.republisher.domain.backup.BackupValidation;
import fr.vinted.republisher.domain.backup.BackupValidationRepository;
import fr.vinted.republisher.domain.backup.BackupVerification;
import fr.vinted.republisher.domain.backup.BackupVerificationProgress;
import fr.vinted.republisher.domain.backup.BackupVerificationRepository;
import fr.vinted.republisher.domain.backup.BackupVerificationStopReason;
import fr.vinted.republisher.domain.backup.ListingBackup;
import fr.vinted.republisher.domain.backup.ListingBackupRepository;
import fr.vinted.republisher.domain.backup.ListingContentReader;
import fr.vinted.republisher.domain.backup.ListingFormReading;
import fr.vinted.republisher.domain.backup.ListingNotBackedUp;
import fr.vinted.republisher.domain.backup.ListingUnreadable;
import fr.vinted.republisher.domain.backup.PhotoStorage;
import fr.vinted.republisher.domain.backup.VerificationFailure;
import fr.vinted.republisher.domain.backup.VerificationFailure.FieldChanged;
import fr.vinted.republisher.domain.backup.VerificationResult;
import fr.vinted.republisher.domain.inventory.BackupFilter;
import fr.vinted.republisher.domain.inventory.InventoryCriteria;
import fr.vinted.republisher.domain.inventory.InventorySort;
import fr.vinted.republisher.domain.inventory.ListingStage;
import fr.vinted.republisher.domain.inventory.PresenceFilter;
import fr.vinted.republisher.domain.inventory.SortDirection;
import fr.vinted.republisher.domain.journal.ActionOutcome;
import fr.vinted.republisher.domain.journal.JournaledAction;
import fr.vinted.republisher.domain.session.BrowserAction;
import fr.vinted.republisher.domain.session.BrowserBusy;
import fr.vinted.republisher.domain.session.BrowserUnavailable;
import fr.vinted.republisher.domain.session.SessionDisconnected;
import fr.vinted.republisher.domain.session.SessionStatus;
import fr.vinted.republisher.domain.session.VintedSession;
import fr.vinted.republisher.domain.session.VintedUnreachable;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * Reads again on Vinted, one by one, the edit form of every present listing to validate, in error of verification or
 * ready to delete, compares it with the current backup and validates at once the backups found identical (US-40).
 * A failure is kept and cancels the validation, so that the automatic run leaves the listing alone. Each result is
 * written as soon as it is known: a stop loses nothing.
 */
public class VerifyAndValidateBackups {

    private static final Logger LOG = LoggerFactory.getLogger(VerifyAndValidateBackups.class);

    private static final Set<ListingStage> TO_VERIFY =
            Set.of(ListingStage.TO_VALIDATE, ListingStage.VERIFICATION_FAILED, ListingStage.TO_DELETE);
    private static final InventoryCriteria EVERY_LISTING = new InventoryCriteria(PresenceFilter.ALL,
            BackupFilter.ALL, "", InventorySort.RECENCY, SortDirection.DESC);

    private final VintedSession session;
    private final BooleanSupplier automaticRunInProgress;
    private final ShowListingInventory inventory;
    private final ListingContentReader reader;
    private final ListingBackupRepository listingBackups;
    private final PhotoStorage photos;
    private final BackupValidationRepository validations;
    private final BackupVerificationRepository verifications;
    private final BackupPace pace;
    private final Clock clock;
    private final Executor background;
    private final JournalWriter journal;
    private BackupVerificationProgress progress = BackupVerificationProgress.idle();

    public VerifyAndValidateBackups(VintedSession session, BooleanSupplier automaticRunInProgress,
                                    ShowListingInventory inventory, ListingContentReader reader,
                                    ListingBackupRepository listingBackups, PhotoStorage photos,
                                    BackupValidationRepository validations,
                                    BackupVerificationRepository verifications, BackupPace pace, Clock clock,
                                    Executor background, JournalWriter journal) {
        this.session = session;
        this.automaticRunInProgress = automaticRunInProgress;
        this.inventory = inventory;
        this.reader = reader;
        this.listingBackups = listingBackups;
        this.photos = photos;
        this.validations = validations;
        this.verifications = verifications;
        this.pace = pace;
        this.clock = clock;
        this.background = background;
        this.journal = journal;
    }

    public synchronized BackupVerificationProgress progress() {
        return progress;
    }

    /**
     * Takes the browser and fixes the listings to verify right away, so that the verification shows as in progress,
     * then visits Vinted in the background. Throws {@link BrowserBusy} during another browser action or an automatic
     * run, and then launches nothing.
     */
    public void launch() {
        if (automaticRunInProgress.getAsBoolean() || !session.start(BrowserAction.VERIFYING_BACKUPS)) {
            throw new BrowserBusy();
        }
        List<VintedListingId> toVerify;
        try {
            toVerify = listingsToVerify();
            update(current -> BackupVerificationProgress.startedFor(toVerify.size()));
            background.execute(() -> verifyUntilDone(toVerify));
        } catch (RuntimeException notLaunched) {
            update(current -> current.stopped(BackupVerificationStopReason.VERIFICATION_ERROR));
            session.finish();
            throw notLaunched;
        }
    }

    private List<VintedListingId> listingsToVerify() {
        return inventory.show(EVERY_LISTING).stream()
                .filter(entry -> TO_VERIFY.contains(entry.stage()))
                .map(entry -> entry.listing().id())
                .sorted(Comparator.comparingLong(VintedListingId::value))
                .toList();
    }

    private void verifyUntilDone(List<VintedListingId> toVerify) {
        try {
            if (!toVerify.isEmpty()) {
                reader.readForms(toVerify, pace, this::verify);
            }
            update(BackupVerificationProgress::finished);
            session.finish();
        } catch (SessionDisconnected disconnected) {
            stop(BackupVerificationStopReason.SESSION_DISCONNECTED, disconnected);
            session.complete(SessionStatus.DISCONNECTED);
        } catch (BrowserUnavailable unavailable) {
            stopAndFinish(BackupVerificationStopReason.BROWSER_UNAVAILABLE, unavailable);
        } catch (VintedUnreachable unreachable) {
            stopAndFinish(BackupVerificationStopReason.VINTED_UNREACHABLE, unreachable);
        } catch (ListingUnreadable unreadable) {
            stopAndFinish(BackupVerificationStopReason.LISTING_UNREADABLE, unreadable);
        } catch (RuntimeException unexpected) {
            // The verification must end whatever happens; the failure is rethrown for the executor to log it.
            update(current -> current.stopped(BackupVerificationStopReason.VERIFICATION_ERROR));
            session.finish();
            throw unexpected;
        }
    }

    private void stopAndFinish(BackupVerificationStopReason reason, RuntimeException cause) {
        stop(reason, cause);
        session.finish();
    }

    /** The page only gives the reason: the details are in the logs. */
    private void stop(BackupVerificationStopReason reason, RuntimeException cause) {
        LOG.warn("Backup verification stopped", cause);
        update(current -> current.stopped(reason));
    }

    /** The result holds for the version compared, even if the backup were replaced meanwhile. */
    private void verify(ListingFormReading reading) {
        VintedListingId id = reading.id();
        ListingBackup backup = listingBackups.find(id).orElseThrow(() -> new ListingNotBackedUp(id));
        Instant now = clock.instant();
        VerificationResult result =
                VerificationResult.of(backup, BackupVerification.online(backup, reading.content(), photos), now);
        if (result.passed()) {
            // Validated again, the same version keeps its first validation date (US-08).
            if (validations.find(id).filter(existing -> existing.holdsFor(backup)).isEmpty()) {
                validations.save(BackupValidation.of(backup, now));
            }
        }
        verifications.save(result);
        journal.write(JournaledAction.BACKUP_VERIFICATION, Optional.of(id),
                result.passed() ? ActionOutcome.SUCCEEDED : ActionOutcome.FAILED,
                result.passed() ? Optional.empty() : Optional.of(reasonOf(result.failures())));
        update(current -> current.verified(result.passed()));
    }

    /** The failure codes of the API, a changed field followed by its name: {@code FIELD_CHANGED:SIZE}. */
    private static String reasonOf(List<VerificationFailure> failures) {
        return failures.stream().map(VerifyAndValidateBackups::codeOf).distinct().collect(Collectors.joining(", "));
    }

    private static String codeOf(VerificationFailure failure) {
        return switch (failure) {
            case VerificationFailure.NoPhoto _ -> "NO_PHOTO";
            case VerificationFailure.PhotoMissing _ -> "PHOTO_MISSING";
            case VerificationFailure.PhotoAltered _ -> "PHOTO_ALTERED";
            case VerificationFailure.ListingAbsent _ -> "LISTING_ABSENT";
            case VerificationFailure.TitleChanged _ -> "TITLE_CHANGED";
            case VerificationFailure.PriceChanged _ -> "PRICE_CHANGED";
            case FieldChanged changed -> "FIELD_CHANGED:" + changed.difference().field().name();
        };
    }

    private synchronized void update(UnaryOperator<BackupVerificationProgress> change) {
        progress = change.apply(progress);
    }
}
