package fr.vinted.republisher.application.deletion;

import fr.vinted.republisher.domain.backup.BackupValidationRepository;
import fr.vinted.republisher.domain.backup.ListingBackupRepository;
import fr.vinted.republisher.domain.backup.ListingNotBackedUp;
import fr.vinted.republisher.domain.backup.PhotoStorage;
import fr.vinted.republisher.domain.deletion.DeletionMode;
import fr.vinted.republisher.domain.deletion.DeletionOutcome;
import fr.vinted.republisher.domain.deletion.DeletionReport;
import fr.vinted.republisher.domain.deletion.ListingDeleter;
import fr.vinted.republisher.domain.deletion.ListingDeletion;
import fr.vinted.republisher.domain.deletion.ListingDeletionRepository;
import fr.vinted.republisher.domain.deletion.NoUnconfirmedDeletion;
import fr.vinted.republisher.domain.session.BrowserAction;
import fr.vinted.republisher.domain.session.BrowserBusy;
import fr.vinted.republisher.domain.session.VintedSession;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.ListingLink;
import fr.vinted.republisher.domain.wardrobe.ListingRepository;
import fr.vinted.republisher.domain.wardrobe.ScannedListing;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

/**
 * Settles a deletion in doubt by only looking at the page of the listing, never clicking. A scan cannot settle it:
 * the listing may have left the wardrobe because it was sold.
 */
public class RecheckListingDeletion {

    private final VintedSession session;
    private final ListingRepository listings;
    private final ListingBackupRepository listingBackups;
    private final ListingDeletionRepository deletions;
    private final ListingDeleter deleter;
    private final Clock clock;
    private final ShowListingDeletion showListingDeletion;

    public RecheckListingDeletion(VintedSession session, ListingRepository listings,
                                  ListingBackupRepository listingBackups, PhotoStorage photos,
                                  BackupValidationRepository validations, ListingDeletionRepository deletions,
                                  ListingDeleter deleter, DeletionMode mode, Clock clock) {
        this.session = session;
        this.listings = listings;
        this.listingBackups = listingBackups;
        this.deletions = deletions;
        this.deleter = deleter;
        this.clock = clock;
        this.showListingDeletion =
                new ShowListingDeletion(listings, listingBackups, photos, validations, deletions, mode);
    }

    /**
     * Throws {@link ListingNotBackedUp}, {@link NoUnconfirmedDeletion} or {@link BrowserBusy} before opening Chrome;
     * a browser failure leaves the doubt as it was.
     */
    public DeletionReport recheck(VintedListingId id) {
        listingBackups.find(id).orElseThrow(() -> new ListingNotBackedUp(id));
        ListingDeletion deletion = deletions.findLatest(id)
                .filter(latest -> latest.outcome() == DeletionOutcome.UNCONFIRMED)
                .orElseThrow(() -> new NoUnconfirmedDeletion(id));
        Listing listing = listings.find(id).orElseThrow(() -> new ListingNotBackedUp(id));
        if (!session.start(BrowserAction.DELETING)) {
            throw new BrowserBusy();
        }
        try {
            settle(deletion, listing, deleter.show(new ListingLink(listing.id(), listing.url())));
        } finally {
            session.finish();
        }
        return showListingDeletion.show(id);
    }

    /** A listing shown but no longer active proves nothing either way: the doubt stays. */
    private void settle(ListingDeletion deletion, Listing listing, Optional<ScannedListing> shown) {
        if (shown.isEmpty()) {
            deletions.save(deletion.confirmed(clock.instant()));
            listings.saveAll(List.of(listing.absent()));
        } else if (shown.get().active()) {
            deletions.save(deletion.notDone(clock.instant()));
        }
    }
}
