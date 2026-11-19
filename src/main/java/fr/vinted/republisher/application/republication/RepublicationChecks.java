package fr.vinted.republisher.application.republication;

import fr.vinted.republisher.domain.backup.BackupNotVerified;
import fr.vinted.republisher.domain.backup.BackupReplaced;
import fr.vinted.republisher.domain.backup.BackupValidationRepository;
import fr.vinted.republisher.domain.backup.BackupVerification;
import fr.vinted.republisher.domain.backup.DownloadedPhoto;
import fr.vinted.republisher.domain.backup.ListingBackup;
import fr.vinted.republisher.domain.backup.ListingBackupRepository;
import fr.vinted.republisher.domain.backup.ListingNotBackedUp;
import fr.vinted.republisher.domain.backup.ListingPhoto;
import fr.vinted.republisher.domain.backup.PhotoStorage;
import fr.vinted.republisher.domain.deletion.BackupNotValidated;
import fr.vinted.republisher.domain.deletion.DeletionOutcome;
import fr.vinted.republisher.domain.deletion.ListingDeletion;
import fr.vinted.republisher.domain.deletion.ListingDeletionRepository;
import fr.vinted.republisher.domain.republication.ListingNotDeleted;
import fr.vinted.republisher.domain.republication.ListingRepublication;
import fr.vinted.republisher.domain.republication.ListingRepublicationRepository;
import fr.vinted.republisher.domain.republication.RepublicationDelay;
import fr.vinted.republisher.domain.republication.RepublicationTooEarly;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/** Everything checked before Chrome opens for a republication: one failure refuses it, touching nothing on Vinted. */
public class RepublicationChecks {

    private final ListingBackupRepository listingBackups;
    private final PhotoStorage photos;
    private final BackupValidationRepository validations;
    private final ListingDeletionRepository deletions;
    private final ListingRepublicationRepository republications;
    private final RepublicationDelay delay;
    private final Clock clock;

    public RepublicationChecks(ListingBackupRepository listingBackups, PhotoStorage photos,
                               BackupValidationRepository validations, ListingDeletionRepository deletions,
                               ListingRepublicationRepository republications, RepublicationDelay delay, Clock clock) {
        this.listingBackups = listingBackups;
        this.photos = photos;
        this.validations = validations;
        this.deletions = deletions;
        this.republications = republications;
        this.delay = delay;
        this.clock = clock;
    }

    /** {@code seen} is the date of the backup the member saw. Answers with the backup and its photo files, in order. */
    RepublishableBackup check(VintedListingId id, Instant seen) {
        return check(id, seen, true);
    }

    /** As {@link #check}, without waiting for the republication date: the member republishes by hand when they want. */
    RepublishableBackup checkWithoutDelay(VintedListingId id, Instant seen) {
        return check(id, seen, false);
    }

    private RepublishableBackup check(VintedListingId id, Instant seen, boolean waitsForRepublicationDate) {
        ListingBackup backup = listingBackups.find(id).orElseThrow(() -> new ListingNotBackedUp(id));
        if (!backup.savedAt().equals(seen)) {
            throw new BackupReplaced(id, seen);
        }
        republications.findLatest(id).ifPresent(ListingRepublication::allowNewAttempt);
        ListingDeletion deletion = confirmedDeletionOf(backup);
        if (waitsForRepublicationDate) {
            checkDelay(deletion);
        }
        validations.find(id).filter(validation -> validation.holdsFor(backup.summary()))
                .orElseThrow(() -> new BackupNotValidated(id));
        BackupVerification verification = BackupVerification.ofPhotos(backup, photos);
        if (!verification.passed()) {
            throw new BackupNotVerified(verification);
        }
        return new RepublishableBackup(backup, backup.photos().stream().map(photo -> fileOf(backup, photo)).toList());
    }

    private ListingDeletion confirmedDeletionOf(ListingBackup backup) {
        return deletions.findLatest(backup.id())
                .filter(latest -> latest.outcome() == DeletionOutcome.CONFIRMED)
                .filter(confirmed -> confirmed.backupSavedAt().equals(backup.savedAt()))
                .orElseThrow(() -> new ListingNotDeleted(backup.id()));
    }

    private void checkDelay(ListingDeletion deletion) {
        Instant republishableAt = delay.republishableAt(deletion).orElseThrow();
        if (clock.instant().isBefore(republishableAt)) {
            throw new RepublicationTooEarly(republishableAt);
        }
    }

    private DownloadedPhoto fileOf(ListingBackup backup, ListingPhoto photo) {
        return photos.read(backup.id(), photo).map(content -> new DownloadedPhoto(content, photo.mediaType()))
                .orElseThrow(() -> new IllegalStateException("Photo " + photo.rank() + " vanished once verified"));
    }

    /** The backup to republish, with the files of its photos as verified. */
    record RepublishableBackup(ListingBackup backup, List<DownloadedPhoto> photos) {
    }
}
