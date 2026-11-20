package fr.vinted.republisher.acceptance;

import fr.vinted.republisher.application.inventory.ShowListingInventory;
import fr.vinted.republisher.domain.backup.BackupValidation;
import fr.vinted.republisher.domain.backup.ListingBackup;
import fr.vinted.republisher.domain.cadence.WriteOrigin;
import fr.vinted.republisher.domain.deletion.ListingDeletion;
import fr.vinted.republisher.domain.inventory.BackupFilter;
import fr.vinted.republisher.domain.inventory.InventoryCriteria;
import fr.vinted.republisher.domain.inventory.InventorySort;
import fr.vinted.republisher.domain.inventory.ListingStage;
import fr.vinted.republisher.domain.inventory.PresenceFilter;
import fr.vinted.republisher.domain.inventory.SortDirection;
import fr.vinted.republisher.domain.republication.ListingRepublication;
import fr.vinted.republisher.domain.republication.RepublicationDelay;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import fr.vinted.republisher.fakes.AdjustableClock;
import fr.vinted.republisher.fakes.InMemoryBackupValidationRepository;
import fr.vinted.republisher.fakes.InMemoryBackupVerificationRepository;
import fr.vinted.republisher.fakes.InMemoryListingBackupRepository;
import fr.vinted.republisher.fakes.InMemoryListingDeletionRepository;
import fr.vinted.republisher.fakes.InMemoryListingRepository;
import fr.vinted.republisher.fakes.InMemoryListingRepublicationRepository;
import fr.vinted.republisher.fakes.InMemoryListingSaleRepository;
import fr.vinted.republisher.fakes.InMemoryListingStatisticsRepository;
import fr.vinted.republisher.fakes.InMemoryListingTagRepository;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static fr.vinted.republisher.domain.backup.BackupFixtures.aBackup;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.ACTIVE;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.aListing;
import static org.assertj.core.api.Assertions.assertThat;

/** US-14: the member sees where each listing stands. */
class ListingStageAcceptanceTest {

    private static final Instant SCANNED_AT = Instant.parse("2026-11-10T09:00:00Z");
    private static final Instant SAVED_AT = Instant.parse("2026-11-10T10:00:00Z");
    private static final Instant DELETED_AT = Instant.parse("2026-11-12T10:00:00Z");

    private final InMemoryListingRepository listings = new InMemoryListingRepository();
    private final InMemoryListingBackupRepository backups = new InMemoryListingBackupRepository();
    private final InMemoryBackupValidationRepository validations = new InMemoryBackupValidationRepository();
    private final InMemoryListingDeletionRepository deletions = new InMemoryListingDeletionRepository();
    private final InMemoryListingRepublicationRepository republications = new InMemoryListingRepublicationRepository();
    private final AdjustableClock clock = new AdjustableClock(DELETED_AT);
    private final ShowListingInventory showListingInventory = new ShowListingInventory(listings,
            new InMemoryListingStatisticsRepository(), backups, validations, deletions, republications,
            new RepublicationDelay(Duration.ofMinutes(5)), clock, new InMemoryListingTagRepository(),
            new InMemoryBackupVerificationRepository(), new InMemoryListingSaleRepository());

    @Test
    void listing_without_backup_is_to_back_up() {
        // Étant donné une annonce présente sans sauvegarde
        listings.saveAll(List.of(present(101)));

        // Quand le membre affiche la liste
        // Alors son étape est « À sauvegarder »
        assertThat(stageOf(101)).isEqualTo(ListingStage.TO_BACK_UP);
    }

    @Test
    void unvalidated_backup_is_to_validate() {
        // Étant donné une annonce présente sauvegardée, sauvegarde non validée
        listings.saveAll(List.of(present(101)));
        backups.save(aBackup(101, SAVED_AT));

        // Quand le membre affiche la liste
        // Alors son étape est « À valider »
        assertThat(stageOf(101)).isEqualTo(ListingStage.TO_VALIDATE);
    }

    @Test
    void validation_of_a_replaced_backup_does_not_count() {
        // Étant donné une annonce dont la sauvegarde validée a été remplacée par une nouvelle sauvegarde
        listings.saveAll(List.of(present(101)));
        validated(101);
        backups.save(aBackup(101, SAVED_AT.plus(Duration.ofDays(1))));

        // Quand le membre affiche la liste
        // Alors son étape est « À valider »
        assertThat(stageOf(101)).isEqualTo(ListingStage.TO_VALIDATE);
    }

    @Test
    void validated_listing_is_to_delete() {
        // Étant donné une annonce présente dont la sauvegarde actuelle est validée, sans suppression
        listings.saveAll(List.of(present(101)));
        validated(101);

        // Quand le membre affiche la liste
        // Alors son étape est « Prête à supprimer »
        assertThat(stageOf(101)).isEqualTo(ListingStage.TO_DELETE);
    }

    @Test
    void unconfirmed_deletion_is_to_check() {
        // Étant donné une annonce dont la dernière suppression est non confirmée
        listings.saveAll(List.of(present(101)));
        deletions.save(ListingDeletion.attempted(validated(101), DELETED_AT, WriteOrigin.RUN));

        // Quand le membre affiche la liste
        // Alors son étape est « Suppression à vérifier »
        assertThat(stageOf(101)).isEqualTo(ListingStage.DELETION_TO_CHECK);
    }

    @Test
    void deletion_not_done_is_ignored() {
        // Étant donné une annonce présente, validée, dont la dernière suppression est « non faite »
        listings.saveAll(List.of(present(101)));
        deletions.save(ListingDeletion.attempted(validated(101), DELETED_AT,
                WriteOrigin.RUN).notDone(DELETED_AT.plusSeconds(60)));

        // Quand le membre affiche la liste
        // Alors son étape est « Prête à supprimer »
        assertThat(stageOf(101)).isEqualTo(ListingStage.TO_DELETE);
    }

    @Test
    void only_the_latest_deletion_counts() {
        // Étant donné une suppression « non faite » puis une nouvelle suppression non confirmée
        listings.saveAll(List.of(present(101)));
        ListingBackup backup = validated(101);
        deletions.save(ListingDeletion.attempted(backup, DELETED_AT,
                WriteOrigin.RUN).notDone(DELETED_AT.plusSeconds(60)));
        deletions.save(ListingDeletion.attempted(backup, DELETED_AT.plusSeconds(120), WriteOrigin.RUN));

        // Quand le membre affiche la liste
        // Alors son étape est « Suppression à vérifier »
        assertThat(stageOf(101)).isEqualTo(ListingStage.DELETION_TO_CHECK);
    }

    @Test
    void confirmed_deletion_within_delay_is_waiting() {
        // Étant donné une annonce dont la suppression a été prouvée il y a 2 minutes, délai minimal de 5 minutes
        deleted(101);
        clock.set(DELETED_AT.plus(Duration.ofMinutes(2)));

        // Quand le membre affiche la liste
        // Alors son étape est « En attente avant republication »
        assertThat(stageOf(101)).isEqualTo(ListingStage.WAITING);
    }

    @Test
    void confirmed_deletion_after_delay_is_to_republish() {
        // Étant donné une annonce dont la suppression a été prouvée il y a 5 minutes ou plus
        deleted(101);
        clock.set(DELETED_AT.plus(Duration.ofMinutes(5)));

        // Quand le membre affiche la liste
        // Alors son étape est « À republier »
        assertThat(stageOf(101)).isEqualTo(ListingStage.TO_REPUBLISH);
    }

    @Test
    void failed_republication_is_to_check() {
        // Étant donné une annonce dont la dernière republication est en erreur
        republications.save(ListingRepublication.attempted(deleted(101), DELETED_AT.plusSeconds(600), WriteOrigin.RUN));
        clock.set(DELETED_AT.plusSeconds(900));

        // Quand le membre affiche la liste
        // Alors son étape est « Republication à vérifier »
        assertThat(stageOf(101)).isEqualTo(ListingStage.REPUBLICATION_TO_CHECK);
    }

    @Test
    void republished_listing_and_its_new_listing() {
        // Étant donné une annonce dont la republication a réussi
        republications.save(ListingRepublication.attempted(deleted(101), DELETED_AT.plusSeconds(600), WriteOrigin.RUN)
                .published(new VintedListingId(202), DELETED_AT.plusSeconds(610)));
        listings.saveAll(List.of(present(202)));
        clock.set(DELETED_AT.plusSeconds(900));

        // Quand le membre affiche la liste
        // Alors l'ancienne annonce est « Republiée »
        // Et la nouvelle annonce, sans sauvegarde, est « À sauvegarder »
        assertThat(stages()).containsExactlyInAnyOrderEntriesOf(Map.of(
                101L, ListingStage.REPUBLISHED, 202L, ListingStage.TO_BACK_UP));
    }

    @Test
    void absent_listing_without_deletion_is_gone() {
        // Étant donné une annonce absente du dressing au dernier scan, sans suppression confirmée
        listings.saveAll(List.of(present(101).absent()));
        validated(101);

        // Quand le membre affiche la liste avec toutes les annonces
        // Alors son étape est « Absente de Vinted »
        assertThat(stageOf(101)).isEqualTo(ListingStage.GONE);
    }

    private ListingBackup validated(long id) {
        ListingBackup backup = aBackup(id, SAVED_AT);
        backups.save(backup);
        validations.save(BackupValidation.of(backup, SAVED_AT.plusSeconds(60)));
        return backup;
    }

    /** Validated, then deleted with proof at {@code DELETED_AT}: the listing is absent. */
    private ListingBackup deleted(long id) {
        listings.saveAll(List.of(present(id).absent()));
        ListingBackup backup = validated(id);
        deletions.save(ListingDeletion.attempted(backup, DELETED_AT.minusSeconds(2),
                WriteOrigin.RUN).confirmed(DELETED_AT));
        return backup;
    }

    private static Listing present(long id) {
        return Listing.seen(aListing(id, "Pull " + id, "10", ACTIVE), SCANNED_AT);
    }

    private ListingStage stageOf(long id) {
        return stages().get(id);
    }

    private Map<Long, ListingStage> stages() {
        InventoryCriteria all = new InventoryCriteria(PresenceFilter.ALL, BackupFilter.ALL, "", InventorySort.RECENCY,
                SortDirection.DESC);
        return showListingInventory.show(all).stream()
                .collect(Collectors.toMap(entry -> entry.listing().id().value(), entry -> entry.stage()));
    }
}
