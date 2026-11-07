package fr.vinted.republisher.domain.backup;

import java.util.List;

public interface ListingBackupRepository {

    List<ListingBackup> findAll();

    /** Replaces the previous backup of the listing, if any. */
    void save(ListingBackup backup);
}
