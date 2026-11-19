package fr.vinted.republisher.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

interface ListingBackupJpaRepository extends JpaRepository<ListingBackupJpaEntity, Long> {

    /** One statement: the photos are counted by the database, never loaded. */
    @Query("select new fr.vinted.republisher.adapter.out.persistence.BackupSummaryRow("
            + "backup.listingId, backup.savedAt, size(backup.photos)) from ListingBackupJpaEntity backup")
    List<BackupSummaryRow> findAllSummaries();
}
