package fr.vinted.republisher.acceptance;

import fr.vinted.republisher.application.cadence.CadenceCheck;
import fr.vinted.republisher.application.inventory.ShowListingInventory;
import fr.vinted.republisher.application.run.ShowRunQueue;
import fr.vinted.republisher.domain.backup.BackupValidation;
import fr.vinted.republisher.domain.backup.ListingBackup;
import fr.vinted.republisher.domain.cadence.WriteCadence;
import fr.vinted.republisher.domain.deletion.DeletionMode;
import fr.vinted.republisher.domain.deletion.ListingDeletion;
import fr.vinted.republisher.domain.relaunch.RelaunchAction;
import fr.vinted.republisher.domain.republication.RepublicationDelay;
import fr.vinted.republisher.domain.republication.RepublicationMode;
import fr.vinted.republisher.domain.run.AutomaticRun;
import fr.vinted.republisher.domain.run.ParallelismMode;
import fr.vinted.republisher.domain.run.RunWaitingReason;
import fr.vinted.republisher.domain.run.RunWorker;
import fr.vinted.republisher.domain.run.RunWorkerKind;
import fr.vinted.republisher.domain.run.QueuedAction;
import fr.vinted.republisher.domain.run.RunQueue;
import fr.vinted.republisher.domain.run.WriteModes;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import fr.vinted.republisher.fakes.AdjustableClock;
import fr.vinted.republisher.fakes.InMemoryBackupValidationRepository;
import fr.vinted.republisher.fakes.InMemoryBackupVerificationRepository;
import fr.vinted.republisher.fakes.InMemoryListingBackupRepository;
import fr.vinted.republisher.fakes.InMemoryListingDeletionRepository;
import fr.vinted.republisher.fakes.InMemoryListingRepository;
import fr.vinted.republisher.fakes.InMemoryListingRepublicationRepository;
import fr.vinted.republisher.fakes.InMemoryListingStatisticsRepository;
import fr.vinted.republisher.fakes.InMemoryListingTagRepository;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static fr.vinted.republisher.domain.backup.BackupFixtures.aBackup;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.ACTIVE;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.aListing;
import static org.assertj.core.api.Assertions.assertThat;

/** US-41: the member sees, in order and at the earliest, the actions the automatic run would launch. */
class ShowRunQueueAcceptanceTest {

    private static final Instant SAVED_AT = Instant.parse("2026-11-18T10:00:00Z");
    private static final Instant NOW = Instant.parse("2026-11-19T10:00:00Z");
    private static final WriteModes REAL = new WriteModes(DeletionMode.REAL, RepublicationMode.REAL);

    private final InMemoryListingRepository listings = new InMemoryListingRepository();
    private final InMemoryListingBackupRepository backups = new InMemoryListingBackupRepository();
    private final InMemoryBackupValidationRepository validations = new InMemoryBackupValidationRepository();
    private final InMemoryListingDeletionRepository deletions = new InMemoryListingDeletionRepository();
    private final InMemoryListingRepublicationRepository republications = new InMemoryListingRepublicationRepository();
    private final AdjustableClock clock = new AdjustableClock(NOW);
    private final RepublicationDelay delay = new RepublicationDelay(Duration.ofMinutes(5));
    private WriteCadence cadence = new WriteCadence(Duration.ofSeconds(30), Duration.ofMinutes(2), 140, 140);
    private AutomaticRun run = AutomaticRun.idle();

    @Test
    void queue_follows_the_run_order() {
        // Étant donné les annonces 30 « Prête à supprimer », 20 « À republier », 10 « Suppression à vérifier »
        // et 40 « À valider »
        aValidatedListing(30);
        aDeletedListing(20, NOW.minus(Duration.ofHours(1)));
        aDeletionInDoubt(10);
        aBackedUpListing(40);
        // Et la suppression et la republication en dry-run
        WriteModes dryRun = new WriteModes(DeletionMode.DRY_RUN, RepublicationMode.DRY_RUN);
        // Quand le membre affiche la file d'attente
        RunQueue queue = showRunQueue(dryRun).show();
        // Alors la file est : revérification de 10, republication de 20, suppression de 30, toutes maintenant
        // Et la republication et la suppression sont marquées dry-run ; l'annonce 40 n'y est pas
        assertThat(queue.computedAt()).isEqualTo(NOW);
        assertThat(queue.actions()).containsExactly(
                action(10, RelaunchAction.DELETION_RECHECK, NOW, false),
                action(20, RelaunchAction.REPUBLICATION, NOW, true),
                action(30, RelaunchAction.DELETION, NOW, true));
    }

    @Test
    void real_writes_follow_the_cadence_and_deletions_lead_to_republications() {
        // Étant donné une suppression faite il y a 10 secondes, sur une annonce déjà traitée
        deletions.save(ListingDeletion.attempted(aBackup(9, SAVED_AT), NOW.minusSeconds(10)));
        // Et les annonces 1 et 2 « Prêtes à supprimer »
        aValidatedListing(1);
        aValidatedListing(2);
        // Quand le membre affiche la file d'attente
        List<QueuedAction> actions = showRunQueue(REAL).show().actions();
        // Alors suppression de 1 dans 20 s, de 2 dans 50 s, republication de 1 dans 5 min 20 s, de 2 dans 7 min 20 s
        assertThat(actions).containsExactly(
                action(1, RelaunchAction.DELETION, NOW.plusSeconds(20), false),
                action(2, RelaunchAction.DELETION, NOW.plusSeconds(50), false),
                action(1, RelaunchAction.REPUBLICATION, NOW.plusSeconds(320), false),
                action(2, RelaunchAction.REPUBLICATION, NOW.plusSeconds(440), false));
    }

    @Test
    void waiting_listing_goes_first_once_republishable() {
        // Étant donné l'annonce 5 supprimée, republiable dans 1 minute
        aDeletedListing(5, NOW.minus(Duration.ofMinutes(4)));
        // Et les annonces 1, 2 et 3 « Prêtes à supprimer », aucune écriture récente
        aValidatedListing(1);
        aValidatedListing(2);
        aValidatedListing(3);
        // Quand le membre affiche la file d'attente
        List<QueuedAction> actions = showRunQueue(REAL).show().actions();
        // Alors la file commence par : suppression de 1 maintenant, de 2 dans 30 s, republication de 5 dans 1 min,
        // suppression de 3 dans 1 min (chaque type d'écriture a sa cadence, US-46)
        assertThat(actions).startsWith(
                action(1, RelaunchAction.DELETION, NOW, false),
                action(2, RelaunchAction.DELETION, NOW.plusSeconds(30), false),
                action(5, RelaunchAction.REPUBLICATION, NOW.plusSeconds(60), false),
                action(3, RelaunchAction.DELETION, NOW.plusSeconds(60), false));
    }

    @Test
    void each_write_type_has_its_own_cadence() {
        // AT-46-13
        // Étant donné une annonce « À republier » et une « Prête à supprimer » validée, en mode réel, aucune écriture
        // récente
        aDeletedListing(5, NOW.minus(Duration.ofMinutes(6)));
        aValidatedListing(1);
        // Quand le membre ouvre la file d'attente
        List<QueuedAction> actions = showRunQueue(REAL).show().actions();
        // Alors la republication et la suppression sont prévues « maintenant » toutes les deux
        assertThat(actions).startsWith(
                action(5, RelaunchAction.REPUBLICATION, NOW, false),
                action(1, RelaunchAction.DELETION, NOW, false));
    }

    @Test
    void daily_limit_pushes_the_write_back() {
        // Étant donné un plafond de 2 suppressions par 24 heures
        cadence = new WriteCadence(Duration.ofSeconds(30), Duration.ofMinutes(2), 2, 140);
        // Et deux suppressions faites il y a 23 heures et 22 heures, sur des annonces déjà traitées
        deletions.save(ListingDeletion.attempted(aBackup(8, SAVED_AT), NOW.minus(Duration.ofHours(23))));
        deletions.save(ListingDeletion.attempted(aBackup(9, SAVED_AT), NOW.minus(Duration.ofHours(22))));
        // Et l'annonce 1 « Prête à supprimer »
        aValidatedListing(1);
        // Quand le membre affiche la file d'attente
        List<QueuedAction> actions = showRunQueue(REAL).show().actions();
        // Alors la file commence par la suppression de 1, prévue dans 1 heure
        assertThat(actions).first()
                .isEqualTo(action(1, RelaunchAction.DELETION, NOW.plus(Duration.ofHours(1)), false));
    }

    @Test
    void listings_set_aside_by_the_current_run_are_left_out() {
        // Étant donné une série en cours qui a écarté l'annonce 1
        run = AutomaticRun.startedAt(NOW.minusSeconds(60), ParallelismMode.SEQUENTIAL.workerKinds()).skipping(new VintedListingId(1));
        // Et les annonces 1 et 2 « Prêtes à supprimer »
        aValidatedListing(1);
        aValidatedListing(2);
        // Quand le membre affiche la file d'attente
        List<QueuedAction> actions = showRunQueue(new WriteModes(DeletionMode.DRY_RUN, RepublicationMode.DRY_RUN))
                .show().actions();
        // Alors seule la suppression de 2 y figure
        assertThat(actions).containsExactly(action(2, RelaunchAction.DELETION, NOW, true));
    }

    @Test
    void listings_the_run_is_relaunching_are_left_out() {
        // Étant donné une série en train de relancer l'annonce 1, et les annonces 1 et 2 « Prêtes à supprimer »
        run = AutomaticRun.startedAt(NOW.minusSeconds(60), ParallelismMode.WORKERS.workerKinds())
                .withWorker(RunWorker.idle(RunWorkerKind.DELETION).working(new VintedListingId(1), RelaunchAction.DELETION));
        aValidatedListing(1);
        aValidatedListing(2);
        // Quand le membre affiche la file d'attente
        List<QueuedAction> actions = showRunQueue(new WriteModes(DeletionMode.DRY_RUN, RepublicationMode.DRY_RUN))
                .show().actions();
        // Alors l'annonce 1, déjà en cours, n'y est pas
        assertThat(actions).containsExactly(action(2, RelaunchAction.DELETION, NOW, true));
    }

    @Test
    void listings_are_back_once_their_worker_waits() {
        run = AutomaticRun.startedAt(NOW.minusSeconds(60), ParallelismMode.WORKERS.workerKinds())
                .withWorker(RunWorker.idle(RunWorkerKind.DELETION).waiting(NOW.plusSeconds(30), RunWaitingReason.CADENCE));
        aValidatedListing(1);

        List<QueuedAction> actions = showRunQueue(new WriteModes(DeletionMode.DRY_RUN, RepublicationMode.DRY_RUN))
                .show().actions();

        assertThat(actions).containsExactly(action(1, RelaunchAction.DELETION, NOW, true));
    }

    @Test
    void listings_set_aside_by_an_ended_run_are_back() {
        run = AutomaticRun.startedAt(NOW.minusSeconds(60), ParallelismMode.SEQUENTIAL.workerKinds()).skipping(new VintedListingId(1)).finishedAt(NOW);
        aValidatedListing(1);

        List<QueuedAction> actions = showRunQueue(new WriteModes(DeletionMode.DRY_RUN, RepublicationMode.DRY_RUN))
                .show().actions();

        assertThat(actions).containsExactly(action(1, RelaunchAction.DELETION, NOW, true));
    }

    private ShowRunQueue showRunQueue(WriteModes modes) {
        ShowListingInventory inventory = new ShowListingInventory(listings, new InMemoryListingStatisticsRepository(),
                backups, validations, deletions, republications, delay, clock, new InMemoryListingTagRepository(),
                new InMemoryBackupVerificationRepository());
        return new ShowRunQueue(inventory, deletions, delay, new CadenceCheck(deletions, republications, cadence, clock),
                modes, () -> run, clock);
    }

    private static QueuedAction action(long id, RelaunchAction action, Instant at, boolean dryRun) {
        return new QueuedAction(new VintedListingId(id), "Annonce " + id, action, at, dryRun);
    }

    private ListingBackup aBackedUpListing(long id) {
        listings.saveAll(List.of(Listing.seen(aListing(id, "Annonce " + id, "10.00", ACTIVE), SAVED_AT)));
        ListingBackup backup = aBackup(id, SAVED_AT);
        backups.save(backup);
        return backup;
    }

    private ListingBackup aValidatedListing(long id) {
        ListingBackup backup = aBackedUpListing(id);
        validations.save(BackupValidation.of(backup, SAVED_AT));
        return backup;
    }

    /** Deleted, the deletion proven at {@code provenAt}: absent, republishable after the delay. */
    private void aDeletedListing(long id, Instant provenAt) {
        ListingBackup backup = aValidatedListing(id);
        listings.saveAll(List.of(Listing.seen(aListing(id, "Annonce " + id, "10.00", ACTIVE), SAVED_AT).absent()));
        deletions.save(ListingDeletion.attempted(backup, provenAt.minusSeconds(5)).confirmed(provenAt));
    }

    private void aDeletionInDoubt(long id) {
        deletions.save(ListingDeletion.attempted(aValidatedListing(id), NOW.minus(Duration.ofHours(2))));
    }
}
