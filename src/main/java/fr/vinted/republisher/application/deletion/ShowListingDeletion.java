package fr.vinted.republisher.application.deletion;

import fr.vinted.republisher.application.backup.ShowListingBackup;
import fr.vinted.republisher.domain.backup.BackupValidationRepository;
import fr.vinted.republisher.domain.backup.ListingBackupRepository;
import fr.vinted.republisher.domain.backup.ListingBackupView;
import fr.vinted.republisher.domain.backup.ListingNotBackedUp;
import fr.vinted.republisher.domain.backup.PhotoStorage;
import fr.vinted.republisher.domain.deletion.DeletionMode;
import fr.vinted.republisher.domain.deletion.DeletionReport;
import fr.vinted.republisher.domain.deletion.ListingDeletion;
import fr.vinted.republisher.domain.deletion.ListingDeletionRepository;
import fr.vinted.republisher.domain.republication.RepublicationDelay;
import fr.vinted.republisher.domain.wardrobe.ListingRepository;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.util.Optional;

/** The backup of a listing, verified afresh, with its last deletion and the mode deletions run in. */
public class ShowListingDeletion {

    private final ShowListingBackup showListingBackup;
    private final ListingDeletionRepository deletions;
    private final DeletionMode mode;
    private final RepublicationDelay republicationDelay;

    public ShowListingDeletion(ListingRepository listings, ListingBackupRepository listingBackups, PhotoStorage photos,
                               BackupValidationRepository validations, ListingDeletionRepository deletions,
                               DeletionMode mode, RepublicationDelay republicationDelay) {
        this.showListingBackup = new ShowListingBackup(listings, listingBackups, photos, validations);
        this.deletions = deletions;
        this.mode = mode;
        this.republicationDelay = republicationDelay;
    }

    /** Throws {@link ListingNotBackedUp} if the listing has no backup. */
    public DeletionReport show(VintedListingId listing) {
        return report(listing, false);
    }

    /** The given view, such as the one a validation answers with, completed with what concerns its deletion. */
    public DeletionReport around(ListingBackupView view) {
        return report(view, false);
    }

    DeletionReport report(VintedListingId listing, boolean dryRunPassed) {
        return report(showListingBackup.show(listing), dryRunPassed);
    }

    private DeletionReport report(ListingBackupView view, boolean dryRunPassed) {
        Optional<ListingDeletion> lastDeletion = deletions.findLatest(view.backup().id());
        return new DeletionReport(view, lastDeletion, lastDeletion.flatMap(republicationDelay::republishableAt), mode,
                dryRunPassed);
    }
}
