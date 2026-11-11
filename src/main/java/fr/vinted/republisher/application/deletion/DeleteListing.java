package fr.vinted.republisher.application.deletion;

import fr.vinted.republisher.domain.backup.BackupNotVerified;
import fr.vinted.republisher.domain.backup.BackupReplaced;
import fr.vinted.republisher.domain.backup.BackupValidationRepository;
import fr.vinted.republisher.domain.backup.BackupVerification;
import fr.vinted.republisher.domain.backup.ListingBackup;
import fr.vinted.republisher.domain.backup.ListingBackupRepository;
import fr.vinted.republisher.domain.backup.ListingNotBackedUp;
import fr.vinted.republisher.domain.backup.PhotoStorage;
import fr.vinted.republisher.domain.deletion.BackupNotValidated;
import fr.vinted.republisher.domain.deletion.DeletionMode;
import fr.vinted.republisher.domain.deletion.DeletionReport;
import fr.vinted.republisher.domain.deletion.DeletionVisit;
import fr.vinted.republisher.domain.deletion.ListingChanged;
import fr.vinted.republisher.domain.deletion.ListingDeleter;
import fr.vinted.republisher.domain.deletion.ListingDifference;
import fr.vinted.republisher.domain.deletion.ListingDeletion;
import fr.vinted.republisher.domain.deletion.ListingDeletionRepository;
import fr.vinted.republisher.domain.session.BrowserAction;
import fr.vinted.republisher.domain.session.BrowserBusy;
import fr.vinted.republisher.domain.session.VintedSession;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.ListingLink;
import fr.vinted.republisher.domain.wardrobe.ListingRepository;
import fr.vinted.republisher.domain.wardrobe.ScannedListing;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Deletes a listing on Vinted, only once its backup is validated, as the member asked. */
public class DeleteListing {

    private static final Logger LOG = LoggerFactory.getLogger(DeleteListing.class);

    private final VintedSession session;
    private final ListingRepository listings;
    private final ListingBackupRepository listingBackups;
    private final PhotoStorage photos;
    private final BackupValidationRepository validations;
    private final ListingDeletionRepository deletions;
    private final ListingDeleter deleter;
    private final DeletionMode mode;
    private final Clock clock;
    private final ShowListingDeletion showListingDeletion;

    public DeleteListing(VintedSession session, ListingRepository listings, ListingBackupRepository listingBackups,
                         PhotoStorage photos, BackupValidationRepository validations,
                         ListingDeletionRepository deletions, ListingDeleter deleter, DeletionMode mode, Clock clock) {
        this.session = session;
        this.listings = listings;
        this.listingBackups = listingBackups;
        this.photos = photos;
        this.validations = validations;
        this.deletions = deletions;
        this.deleter = deleter;
        this.mode = mode;
        this.clock = clock;
        this.showListingDeletion =
                new ShowListingDeletion(listings, listingBackups, photos, validations, deletions, mode);
    }

    /**
     * {@code seen} is the date of the backup the member saw. Every check is made before Chrome opens, then again on
     * the page of the listing before any click: a refusal touches nothing on Vinted.
     */
    public DeletionReport delete(VintedListingId id, Instant seen) {
        ListingBackup backup = listingBackups.find(id).orElseThrow(() -> new ListingNotBackedUp(id));
        if (!backup.savedAt().equals(seen)) {
            throw new BackupReplaced(id, seen);
        }
        validations.find(id).filter(validation -> validation.holdsFor(backup))
                .orElseThrow(() -> new BackupNotValidated(id));
        // Before the verification: a deleted listing is absent, which must not hide why it cannot be deleted again.
        deletions.findLatest(id).ifPresent(ListingDeletion::allowNewAttempt);
        Optional<Listing> scanned = listings.find(id);
        BackupVerification verification = BackupVerification.of(backup, scanned, photos);
        if (!verification.passed()) {
            throw new BackupNotVerified(verification);
        }
        Listing listing = scanned.orElseThrow();
        if (!session.start(BrowserAction.DELETING)) {
            throw new BrowserBusy();
        }
        Attempt attempt = new Attempt(backup);
        try {
            Optional<ScannedListing> shownAfterwards =
                    deleter.delete(new ListingLink(listing.id(), listing.url()), attempt);
            attempt.started().ifPresent(deletion -> settle(deletion, listing, shownAfterwards));
        } catch (RuntimeException failure) {
            if (attempt.started().isEmpty()) {
                throw failure;
            }
            // Past Vinted's confirmation, trying again blindly could delete twice: the deletion stays in doubt.
            LOG.warn("Deletion of listing {} failed after Vinted's confirmation", id.value(), failure);
        } finally {
            session.finish();
        }
        return showListingDeletion.report(id, attempt.dryRunPassed);
    }

    private void settle(ListingDeletion deletion, Listing listing, Optional<ScannedListing> shownAfterwards) {
        if (shownAfterwards.isEmpty()) {
            deletions.save(deletion.confirmed(clock.instant()));
            listings.saveAll(List.of(listing.absent()));
        }
    }

    /** Decides on the page of the listing before any click, and records the attempt right before the confirmation. */
    private final class Attempt implements DeletionVisit {

        private final ListingBackup backup;
        private Optional<ListingDeletion> started = Optional.empty();
        private boolean dryRunPassed;

        private Attempt(ListingBackup backup) {
            this.backup = backup;
        }

        @Override
        public boolean agreesToDelete(ScannedListing read) {
            List<ListingDifference> differences = ListingDifference.between(backup.content(), read);
            if (!differences.isEmpty()) {
                throw new ListingChanged(differences);
            }
            if (mode == DeletionMode.DRY_RUN) {
                dryRunPassed = true;
                return false;
            }
            return true;
        }

        /** Saved before the click, so that a crash right after it leaves a doubt rather than no trace. */
        @Override
        public void aboutToConfirm() {
            ListingDeletion deletion = ListingDeletion.attempted(backup, clock.instant());
            deletions.save(deletion);
            started = Optional.of(deletion);
        }

        private Optional<ListingDeletion> started() {
            return started;
        }
    }
}
