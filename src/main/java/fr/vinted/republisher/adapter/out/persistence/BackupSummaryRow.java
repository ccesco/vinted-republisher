package fr.vinted.republisher.adapter.out.persistence;

import fr.vinted.republisher.domain.backup.BackupSummary;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.time.Instant;

/** A backup as the inventory reads it: its listing, its date and how many photos it keeps. */
record BackupSummaryRow(Long listingId, Instant savedAt, Integer photoCount) {

    BackupSummary toBackupSummary() {
        return new BackupSummary(new VintedListingId(listingId), savedAt, photoCount);
    }
}
