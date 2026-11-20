package fr.vinted.republisher.application.duplicate;

import fr.vinted.republisher.domain.backup.BackupSummary;
import fr.vinted.republisher.domain.backup.ListingBackupRepository;
import fr.vinted.republisher.domain.deletion.DeletionMode;
import fr.vinted.republisher.domain.deletion.ListingDeletion;
import fr.vinted.republisher.domain.deletion.ListingDeletionRepository;
import fr.vinted.republisher.domain.duplicate.DuplicateGroups;
import fr.vinted.republisher.domain.republication.ListingRepublication;
import fr.vinted.republisher.domain.republication.ListingRepublicationRepository;
import fr.vinted.republisher.domain.sale.ListingSale;
import fr.vinted.republisher.domain.sale.ListingSaleRepository;
import fr.vinted.republisher.domain.wardrobe.ListingRepository;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/** Reads the duplicates of the wardrobe from what the tool keeps, never Vinted (US-47). */
public class ShowDuplicates {

    private final ListingRepository listings;
    private final ListingBackupRepository backups;
    private final ListingDeletionRepository deletions;
    private final ListingRepublicationRepository republications;
    private final ListingSaleRepository sales;
    private final DeletionMode mode;

    public ShowDuplicates(ListingRepository listings, ListingBackupRepository backups,
                          ListingDeletionRepository deletions, ListingRepublicationRepository republications,
                          ListingSaleRepository sales, DeletionMode mode) {
        this.listings = listings;
        this.backups = backups;
        this.deletions = deletions;
        this.republications = republications;
        this.sales = sales;
        this.mode = mode;
    }

    public DuplicatesView show() {
        return new DuplicatesView(DuplicateGroups.among(listings.findAll()), withHistory(),
                backups.findAllSummaries().stream().map(BackupSummary::id).collect(Collectors.toSet()),
                mode == DeletionMode.DRY_RUN);
    }

    /** Old or new listing of a republication, whatever its outcome, deleted or sold listings. */
    private Set<VintedListingId> withHistory() {
        Set<VintedListingId> withHistory = new HashSet<>(republications.findNewListings());
        republications.findAllLatest().stream().map(ListingRepublication::listingId).forEach(withHistory::add);
        deletions.findAllLatest().stream().map(ListingDeletion::listingId).forEach(withHistory::add);
        sales.findAll().stream().map(ListingSale::listingId).forEach(withHistory::add);
        return withHistory;
    }
}
