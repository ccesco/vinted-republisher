package fr.vinted.republisher.domain.backup;

import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.util.List;
import java.util.Optional;

public interface ListingBackupRepository {

    List<ListingBackup> findAll();

    /** The summary of every backup, read without the content nor the photos. */
    List<BackupSummary> findAllSummaries();

    Optional<ListingBackup> find(VintedListingId listing);

    /** Replaces the previous backup of the listing, if any. */
    void save(ListingBackup backup);
}
