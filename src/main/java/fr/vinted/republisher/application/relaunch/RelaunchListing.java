package fr.vinted.republisher.application.relaunch;

import fr.vinted.republisher.application.deletion.DeleteListing;
import fr.vinted.republisher.application.deletion.RecheckListingDeletion;
import fr.vinted.republisher.application.inventory.ShowListingDetail;
import fr.vinted.republisher.application.republication.RepublishListing;
import fr.vinted.republisher.application.republication.VerifyListingRepublication;
import fr.vinted.republisher.domain.backup.ListingBackup;
import fr.vinted.republisher.domain.deletion.ListingDeletionRepository;
import fr.vinted.republisher.domain.inventory.InventoryEntry;
import fr.vinted.republisher.domain.inventory.ListingStage;
import fr.vinted.republisher.domain.relaunch.NotRelaunchable;
import fr.vinted.republisher.domain.relaunch.RelaunchAction;
import fr.vinted.republisher.domain.relaunch.RelaunchReport;
import fr.vinted.republisher.domain.relaunch.StageChanged;
import fr.vinted.republisher.domain.republication.RepublicationDelay;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.time.Instant;
import java.util.Optional;

/**
 * Moves a listing on by one stage, as the member asked from the list: the action is deduced from the stage and run by
 * the use case of that step, with all its checks, its cadence, its dry run and its journal.
 */
public class RelaunchListing {

    private final ShowListingDetail detail;
    private final ListingDeletionRepository deletions;
    private final RepublicationDelay delay;
    private final DeleteListing deleteListing;
    private final RecheckListingDeletion recheckListingDeletion;
    private final RepublishListing republishListing;
    private final VerifyListingRepublication verifyListingRepublication;

    public RelaunchListing(ShowListingDetail detail, ListingDeletionRepository deletions, RepublicationDelay delay,
                           DeleteListing deleteListing, RecheckListingDeletion recheckListingDeletion,
                           RepublishListing republishListing,
                           VerifyListingRepublication verifyListingRepublication) {
        this.detail = detail;
        this.deletions = deletions;
        this.delay = delay;
        this.deleteListing = deleteListing;
        this.recheckListingDeletion = recheckListingDeletion;
        this.republishListing = republishListing;
        this.verifyListingRepublication = verifyListingRepublication;
    }

    /** {@code seen} is the stage the member saw: a listing that has moved on since is not relaunched. */
    public RelaunchReport relaunch(VintedListingId id, ListingStage seen) {
        InventoryEntry entry = detail.show(id).entry();
        if (entry.stage() != seen) {
            throw new StageChanged(seen, entry.stage());
        }
        RelaunchAction action = RelaunchAction.after(entry.stage())
                .orElseThrow(() -> new NotRelaunchable(entry.stage(), republishableAt(id, entry.stage())));
        boolean dryRun = switch (action) {
            case DELETION -> deleteListing.delete(id, currentBackupOf(entry)).dryRunPassed();
            case DELETION_RECHECK -> recheckListingDeletion.recheck(id).dryRunPassed();
            case REPUBLICATION -> republishListing.republish(id, currentBackupOf(entry)).dryRunPassed();
            case REPUBLICATION_VERIFICATION -> verifyListingRepublication.verify(id).dryRunPassed();
        };
        return new RelaunchReport(action, dryRun, detail.show(id).entry());
    }

    /**
     * When relaunching a listing at this stage would write on Vinted for real, from when the write cadence allows it;
     * empty for a check or a dry run.
     */
    public Optional<Instant> realWriteAllowedAt(ListingStage stage) {
        return RelaunchAction.after(stage).flatMap(action -> switch (action) {
            case DELETION -> deleteListing.realWriteAllowedAt();
            case REPUBLICATION -> republishListing.realWriteAllowedAt();
            case DELETION_RECHECK, REPUBLICATION_VERIFICATION -> Optional.empty();
        });
    }

    private Optional<Instant> republishableAt(VintedListingId id, ListingStage stage) {
        if (stage != ListingStage.WAITING) {
            return Optional.empty();
        }
        return deletions.findLatest(id).flatMap(delay::republishableAt);
    }

    /** The validation that lets the listing be deleted or republished only holds for its current backup. */
    private static Instant currentBackupOf(InventoryEntry entry) {
        return entry.backup().map(ListingBackup::savedAt).orElseThrow();
    }
}
