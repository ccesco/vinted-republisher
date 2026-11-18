package fr.vinted.republisher;

import fr.vinted.republisher.domain.backup.BackupValidation;
import fr.vinted.republisher.domain.backup.BackupValidationRepository;
import fr.vinted.republisher.domain.backup.ListingBackup;
import fr.vinted.republisher.domain.backup.ListingBackupRepository;
import fr.vinted.republisher.domain.backup.WardrobeBackup;
import fr.vinted.republisher.domain.backup.WardrobeBackupRepository;
import fr.vinted.republisher.domain.deletion.ListingDeletion;
import fr.vinted.republisher.domain.deletion.ListingDeletionRepository;
import fr.vinted.republisher.domain.journal.ActionJournal;
import fr.vinted.republisher.domain.journal.ActionOutcome;
import fr.vinted.republisher.domain.journal.ActionRecord;
import fr.vinted.republisher.domain.journal.JournaledAction;
import fr.vinted.republisher.domain.republication.ListingRepublication;
import fr.vinted.republisher.domain.republication.ListingRepublicationRepository;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.ListingRepository;
import fr.vinted.republisher.domain.wardrobe.ListingStatistics;
import fr.vinted.republisher.domain.wardrobe.ListingStatisticsRepository;
import fr.vinted.republisher.domain.wardrobe.StatisticsReading;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import fr.vinted.republisher.domain.wardrobe.WardrobeScan;
import fr.vinted.republisher.domain.wardrobe.WardrobeScanRepository;
import org.springframework.context.ApplicationContext;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static fr.vinted.republisher.domain.backup.BackupFixtures.aBackup;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.anActiveListing;

/**
 * Two listings, the first one backed up, validated, deleted and republished as the second, with readings, journal
 * entries, a scan and a wardrobe backup: at least one row in every table of the changelog, through the application's
 * own repositories.
 */
public final class DataInEveryTable {

    public static final long DELETED_LISTING = 10208778166L;
    public static final long NEW_LISTING = 10208778167L;
    private static final Instant AT = Instant.parse("2026-11-17T10:00:00Z");

    private DataInEveryTable() {
    }

    public static void fill(ApplicationContext application) {
        application.getBean(WardrobeScanRepository.class).save(WardrobeScan.startedAt(AT).finished(AT.plusSeconds(60)));
        ListingRepository listings = application.getBean(ListingRepository.class);
        listings.saveAll(List.of(Listing.seen(anActiveListing(DELETED_LISTING), AT).absent()));
        listings.saveAll(List.of(Listing.seen(anActiveListing(NEW_LISTING), AT.plusSeconds(900))));
        application.getBean(WardrobeBackupRepository.class)
                .save(WardrobeBackup.startedAt(AT.plusSeconds(60)).finished(AT.plusSeconds(120)));
        ListingBackupRepository backups = application.getBean(ListingBackupRepository.class);
        backups.save(aBackup(DELETED_LISTING, AT.plusSeconds(90)));
        ListingBackup backup = backups.find(new VintedListingId(DELETED_LISTING)).orElseThrow();
        application.getBean(BackupValidationRepository.class).save(BackupValidation.of(backup, AT.plusSeconds(180)));
        application.getBean(ListingDeletionRepository.class)
                .save(ListingDeletion.attempted(backup, AT.plusSeconds(240)).confirmed(AT.plusSeconds(245)));
        application.getBean(ListingRepublicationRepository.class).save(ListingRepublication
                .attempted(backup, AT.plusSeconds(600)).published(new VintedListingId(NEW_LISTING), AT.plusSeconds(900)));
        application.getBean(ListingStatisticsRepository.class).addAll(List.of(
                new StatisticsReading(new VintedListingId(DELETED_LISTING), new ListingStatistics(42, 3), AT)));
        application.getBean(ActionJournal.class).write(new ActionRecord(AT, JournaledAction.SCAN,
                ActionOutcome.STARTED, Optional.empty(), Optional.empty()));
    }
}
