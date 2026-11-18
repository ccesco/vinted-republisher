package fr.vinted.republisher.acceptance;

import fr.vinted.republisher.application.cadence.CadenceCheck;
import fr.vinted.republisher.application.deletion.DeleteListing;
import fr.vinted.republisher.application.deletion.RecheckListingDeletion;
import fr.vinted.republisher.application.deletion.ShowListingDeletion;
import fr.vinted.republisher.application.inventory.ShowListingDetail;
import fr.vinted.republisher.application.inventory.ShowListingInventory;
import fr.vinted.republisher.application.journal.JournalWriter;
import fr.vinted.republisher.application.relaunch.RelaunchListing;
import fr.vinted.republisher.application.republication.NewListingSearch;
import fr.vinted.republisher.application.republication.RepublicationChecks;
import fr.vinted.republisher.application.republication.RepublishListing;
import fr.vinted.republisher.application.republication.ShowListingRepublication;
import fr.vinted.republisher.application.republication.VerifyListingRepublication;
import fr.vinted.republisher.application.run.LaunchAutomaticRun;
import fr.vinted.republisher.domain.backup.BackupValidation;
import fr.vinted.republisher.domain.backup.ListingBackup;
import fr.vinted.republisher.domain.backup.ListingContent;
import fr.vinted.republisher.domain.backup.ListingReading;
import fr.vinted.republisher.domain.cadence.WriteCadence;
import fr.vinted.republisher.domain.deletion.DeletionMode;
import fr.vinted.republisher.domain.deletion.DeletionOutcome;
import fr.vinted.republisher.domain.deletion.DeletionVisit;
import fr.vinted.republisher.domain.deletion.ListingDeleter;
import fr.vinted.republisher.domain.deletion.ListingDeletion;
import fr.vinted.republisher.domain.republication.ListingRepublication;
import fr.vinted.republisher.domain.republication.RepublicationDelay;
import fr.vinted.republisher.domain.republication.RepublicationMode;
import fr.vinted.republisher.domain.republication.RepublicationOutcome;
import fr.vinted.republisher.domain.run.AutomaticRun;
import fr.vinted.republisher.domain.run.RunStatus;
import fr.vinted.republisher.domain.run.RunStopReason;
import fr.vinted.republisher.domain.run.RunWaiting;
import fr.vinted.republisher.domain.session.BrowserAction;
import fr.vinted.republisher.domain.session.SessionDisconnected;
import fr.vinted.republisher.domain.session.VintedSession;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.ListingLink;
import fr.vinted.republisher.domain.wardrobe.ScannedListing;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import fr.vinted.republisher.fakes.AdjustableClock;
import fr.vinted.republisher.fakes.FakeListingContentReader;
import fr.vinted.republisher.fakes.FakeListingPublisher;
import fr.vinted.republisher.fakes.FakeWardrobeReader;
import fr.vinted.republisher.fakes.InMemoryActionJournal;
import fr.vinted.republisher.fakes.InMemoryBackupValidationRepository;
import fr.vinted.republisher.fakes.InMemoryListingBackupRepository;
import fr.vinted.republisher.fakes.InMemoryListingDeletionRepository;
import fr.vinted.republisher.fakes.InMemoryListingRepository;
import fr.vinted.republisher.fakes.InMemoryListingRepublicationRepository;
import fr.vinted.republisher.fakes.InMemoryListingStatisticsRepository;
import fr.vinted.republisher.fakes.InMemoryListingTagRepository;
import fr.vinted.republisher.fakes.InMemoryPhotoStorage;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;

import static fr.vinted.republisher.domain.backup.BackupFixtures.aBackup;
import static fr.vinted.republisher.domain.backup.BackupFixtures.aContent;
import static fr.vinted.republisher.domain.backup.BackupFixtures.aPhoto;
import static fr.vinted.republisher.domain.backup.BackupFixtures.noBackupPause;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.ACTIVE;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.aListing;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.noPause;
import static org.assertj.core.api.Assertions.assertThat;

/** US-19: one click relaunches every ready listing in turn, waiting when it must and stopping on a problem. */
class AutomaticRunAcceptanceTest {

    private static final VintedListingId ROBE = new VintedListingId(1);
    private static final VintedListingId PULL = new VintedListingId(2);
    private static final Instant SAVED_AT = Instant.parse("2026-11-10T10:00:00Z");
    private static final Instant NOW = Instant.parse("2026-11-11T10:00:00Z");

    private final InMemoryListingRepository listings = new InMemoryListingRepository();
    private final InMemoryListingBackupRepository listingBackups = new InMemoryListingBackupRepository();
    private final InMemoryPhotoStorage photos = new InMemoryPhotoStorage();
    private final InMemoryBackupValidationRepository validations = new InMemoryBackupValidationRepository();
    private final InMemoryListingDeletionRepository deletions = new InMemoryListingDeletionRepository();
    private final InMemoryListingRepublicationRepository republications = new InMemoryListingRepublicationRepository();
    private final InMemoryActionJournal journal = new InMemoryActionJournal();
    private final VintedDeleter deleter = new VintedDeleter();
    private final FakeWardrobeReader wardrobe = new FakeWardrobeReader();
    private final FakeListingPublisher publisher = new FakeListingPublisher(wardrobe);
    private final FakeListingContentReader contents = new FakeListingContentReader();
    private final VintedSession session = new VintedSession();
    private final AdjustableClock clock = new AdjustableClock(NOW);
    private final RepublicationDelay delay = new RepublicationDelay(Duration.ofMinutes(5));
    private final ClockWaiting waiting = new ClockWaiting();
    private DeletionMode deletionMode = DeletionMode.REAL;
    private LaunchAutomaticRun launchAutomaticRun;

    @Test
    void deletes_waits_and_republishes_every_validated_listing() {
        // Étant donné deux annonces validées 1 et 2, en mode réel, délai 5 minutes, intervalle de cadence 2 minutes
        aValidatedListing(ROBE, "Robe bleue", "12.00");
        aValidatedListing(PULL, "Pull rayé", "15.00");
        publisher.showingOncePublished(aListing(11, "Robe bleue", "12.00", ACTIVE),
                aListing(12, "Pull rayé", "15.00", ACTIVE));
        // Quand le membre clique sur « Tout lancer »
        launch();
        // Alors l'annonce 1 est supprimée, puis l'annonce 2 deux minutes plus tard
        assertThat(deletions.findLatest(ROBE)).map(ListingDeletion::attemptedAt).contains(NOW);
        assertThat(deletions.findLatest(PULL)).map(ListingDeletion::attemptedAt).contains(NOW.plusSeconds(120));
        // Et la série attend la date de republication de l'annonce 1, la republie, puis republie l'annonce 2
        assertThat(waiting.untils()).containsExactly(NOW.plusSeconds(120), NOW.plusSeconds(300),
                NOW.plusSeconds(420));
        assertThat(republications.findLatest(ROBE)).map(ListingRepublication::outcome)
                .contains(RepublicationOutcome.PUBLISHED);
        assertThat(republications.findLatest(PULL)).map(ListingRepublication::outcome)
                .contains(RepublicationOutcome.PUBLISHED);
        // Et la série est terminée avec 4 relances et aucune annonce écartée
        AutomaticRun run = launchAutomaticRun.overview();
        assertThat(run.status()).isEqualTo(RunStatus.FINISHED);
        assertThat(run.relaunchCount()).isEqualTo(4);
        assertThat(run.skippedListings()).isEmpty();
        assertThat(run.startedAt()).contains(NOW);
        assertThat(run.endedAt()).contains(NOW.plusSeconds(420));
        assertThat(session.overview().currentAction()).isEmpty();
    }

    @Test
    void checks_before_writing() {
        // Étant donné une annonce « Republication à vérifier » dont la nouvelle annonce est dans le dressing
        ListingBackup robe = aDeletedListing(ROBE, "Robe bleue", "12.00");
        republications.save(ListingRepublication.attempted(robe, NOW.minusSeconds(600)));
        wardrobe.holding(aListing(11, "Robe bleue", "12.00", ACTIVE));
        contents.showing(new ListingReading(sameAs(robe.content(), 11), List.of(aPhoto("front"))));
        // Et une annonce « Prête à supprimer »
        aValidatedListing(PULL, "Pull rayé", "15.00");
        List<Optional<RepublicationOutcome>> robeWhenDeletingPull = new ArrayList<>();
        deleter.beforeDeleting(() -> robeWhenDeletingPull.add(
                republications.findLatest(ROBE).map(ListingRepublication::outcome)));
        // Quand la série tourne
        launch();
        // Alors la republication est vérifiée avant la suppression de l'autre annonce
        assertThat(robeWhenDeletingPull).containsExactly(Optional.of(RepublicationOutcome.PUBLISHED));
        assertThat(deletions.findLatest(PULL)).map(ListingDeletion::outcome).contains(DeletionOutcome.CONFIRMED);
        assertThat(launchAutomaticRun.overview().status()).isEqualTo(RunStatus.FINISHED);
    }

    @Test
    void dry_run_goes_through_each_listing_once() {
        // Étant donné deux annonces « Prête à supprimer », suppression en dry-run
        deletionMode = DeletionMode.DRY_RUN;
        aValidatedListing(ROBE, "Robe bleue", "12.00");
        aValidatedListing(PULL, "Pull rayé", "15.00");
        // Quand la série tourne
        launch();
        // Alors chaque annonce passe une fois en dry-run, puis est écartée
        assertThat(deleter.opened()).containsExactly(ROBE, PULL);
        AutomaticRun run = launchAutomaticRun.overview();
        assertThat(run.relaunchCount()).isEqualTo(2);
        assertThat(run.skippedListings()).containsExactly(ROBE, PULL);
        // Et la série est terminée, rien n'est supprimé
        assertThat(run.status()).isEqualTo(RunStatus.FINISHED);
        assertThat(deletions.findAll()).isEmpty();
    }

    @Test
    void listing_still_to_check_is_skipped() {
        // Étant donné une annonce « Republication à vérifier » dont la nouvelle annonce n'est pas dans le dressing
        ListingBackup robe = aDeletedListing(ROBE, "Robe bleue", "12.00");
        republications.save(ListingRepublication.attempted(robe, NOW.minusSeconds(600)));
        // Quand la série la vérifie et qu'elle reste « à vérifier »
        launch();
        // Alors elle est écartée et la série se termine
        AutomaticRun run = launchAutomaticRun.overview();
        assertThat(run.skippedListings()).containsExactly(ROBE);
        assertThat(run.relaunchCount()).isEqualTo(1);
        assertThat(run.status()).isEqualTo(RunStatus.FINISHED);
    }

    @Test
    void listing_refused_for_itself_is_skipped() {
        // Étant donné une annonce « Prête à supprimer » modifiée sur Vinted depuis sa sauvegarde, et une autre prête
        aValidatedListing(ROBE, "Robe bleue", "12.00");
        deleter.onVinted(aListing(1, "Robe bleue", "20.00", ACTIVE));
        aValidatedListing(PULL, "Pull rayé", "15.00");
        publisher.showingOncePublished(aListing(12, "Pull rayé", "15.00", ACTIVE));
        // Quand la série tourne
        launch();
        // Alors la première est écartée (refus du cas d'usage), la seconde est supprimée
        assertThat(deletions.findLatest(ROBE)).isEmpty();
        assertThat(deletions.findLatest(PULL)).map(ListingDeletion::outcome).contains(DeletionOutcome.CONFIRMED);
        assertThat(launchAutomaticRun.overview().skippedListings()).containsExactly(ROBE);
    }

    @Test
    void waits_for_the_cadence_limit() {
        // Étant donné une écriture sur Vinted il y a une minute, intervalle 2 minutes, et une annonce « Prête à supprimer »
        deletions.save(ListingDeletion.attempted(aBackup(9, SAVED_AT), NOW.minusSeconds(60)));
        aValidatedListing(ROBE, "Robe bleue", "12.00");
        // Quand la série tourne
        launch();
        // Alors elle attend une minute, puis supprime l'annonce
        assertThat(waiting.untils()).first().isEqualTo(NOW.plusSeconds(60));
        assertThat(deletions.findLatest(ROBE)).map(ListingDeletion::attemptedAt).contains(NOW.plusSeconds(60));
    }

    @Test
    void stops_on_a_lost_session() {
        // Étant donné deux annonces prêtes et une session Vinted perdue
        aValidatedListing(ROBE, "Robe bleue", "12.00");
        aValidatedListing(PULL, "Pull rayé", "15.00");
        deleter.disconnected();
        // Quand la série tourne
        launch();
        // Alors elle s'arrête à la première, raison SESSION_DISCONNECTED, la seconde n'est pas touchée
        AutomaticRun run = launchAutomaticRun.overview();
        assertThat(run.status()).isEqualTo(RunStatus.STOPPED);
        assertThat(run.stopReason()).contains(RunStopReason.SESSION_DISCONNECTED);
        assertThat(run.endedAt()).contains(NOW);
        assertThat(deleter.opened()).containsExactly(ROBE);
    }

    @Test
    void a_second_launch_is_ignored_while_running() {
        // Étant donné une série en cours (en attente de la cadence)
        deletions.save(ListingDeletion.attempted(aBackup(9, SAVED_AT), NOW.minusSeconds(60)));
        aValidatedListing(ROBE, "Robe bleue", "12.00");
        List<AutomaticRun> seenWhileWaiting = new ArrayList<>();
        waiting.during(() -> {
            seenWhileWaiting.add(launchAutomaticRun.overview());
            // Quand le membre clique de nouveau sur « Tout lancer »
            clock.advance(Duration.ofSeconds(10));
            launchAutomaticRun.launch();
        });
        launch();
        // Alors le second lancement est ignoré : une seule série, partie à l'heure du premier clic
        assertThat(seenWhileWaiting).first().satisfies(run -> {
            assertThat(run.status()).isEqualTo(RunStatus.WAITING);
            assertThat(run.waitingUntil()).contains(NOW.plusSeconds(60));
        });
        assertThat(launchAutomaticRun.overview().startedAt()).contains(NOW);
        assertThat(deleter.opened()).containsExactly(ROBE);
    }

    @Test
    void listing_back_at_a_stage_already_relaunched_is_skipped() {
        // Étant donné une annonce « Prête à supprimer » que Vinted montre encore après la confirmation
        aValidatedListing(ROBE, "Robe bleue", "12.00");
        deleter.staysOnceConfirmed(ROBE);
        // Quand la série la supprime (suppression non confirmée), puis la revérifie (non faite : de nouveau prête)
        launch();
        // Alors elle n'est pas supprimée une seconde fois : elle est écartée et la série se termine
        assertThat(deletions.findAll()).hasSize(1);
        assertThat(deletions.findLatest(ROBE)).map(ListingDeletion::outcome).contains(DeletionOutcome.NOT_DONE);
        AutomaticRun run = launchAutomaticRun.overview();
        assertThat(run.relaunchCount()).isEqualTo(2);
        assertThat(run.skippedListings()).containsExactly(ROBE);
        assertThat(run.status()).isEqualTo(RunStatus.FINISHED);
    }

    @Test
    void waits_for_a_busy_browser() {
        // Étant donné le navigateur pris par une action du membre, et une annonce « Prête à supprimer »
        aValidatedListing(ROBE, "Robe bleue", "12.00");
        session.start(BrowserAction.SCANNING);
        waiting.during(() -> session.overview().currentAction().ifPresent(action -> session.finish()));
        // Quand la série tourne
        launch();
        // Alors elle attend 30 secondes, puis supprime l'annonce
        assertThat(waiting.untils()).first().isEqualTo(NOW.plusSeconds(30));
        assertThat(deletions.findLatest(ROBE)).map(ListingDeletion::attemptedAt).contains(NOW.plusSeconds(30));
    }

    @Test
    void pauses_after_the_current_relaunch_then_resumes() {
        // Étant donné une série en cours sur deux annonces prêtes
        aValidatedListing(ROBE, "Robe bleue", "12.00");
        aValidatedListing(PULL, "Pull rayé", "15.00");
        // Quand le membre demande une pause pendant la suppression de la première
        deleter.beforeDeleting(() -> {
            if (deleter.opened().size() == 1) {
                launchAutomaticRun.pause();
            }
        });
        List<AutomaticRun> seenWhilePaused = new ArrayList<>();
        List<Optional<ListingDeletion>> pullWhilePaused = new ArrayList<>();
        waiting.during(() -> {
            AutomaticRun run = launchAutomaticRun.overview();
            if (run.status() == RunStatus.PAUSED) {
                seenWhilePaused.add(run);
                pullWhilePaused.add(deletions.findLatest(PULL));
                // Quand il reprend
                launchAutomaticRun.resume();
            }
        });
        launch();
        // Alors la première suppression se termine, la série passe en pause et la seconde n'est pas touchée
        assertThat(seenWhilePaused).hasSize(1);
        assertThat(seenWhilePaused.getFirst().pauseRequested()).isFalse();
        assertThat(pullWhilePaused).containsExactly(Optional.empty());
        assertThat(deletions.findLatest(ROBE)).map(ListingDeletion::outcome).contains(DeletionOutcome.CONFIRMED);
        // Alors la seconde est supprimée et la série continue
        assertThat(deletions.findLatest(PULL)).map(ListingDeletion::outcome).contains(DeletionOutcome.CONFIRMED);
        assertThat(launchAutomaticRun.overview().pauseRequested()).isFalse();
        assertThat(launchAutomaticRun.overview().status()).isEqualTo(RunStatus.FINISHED);
    }

    @Test
    void stop_wins_over_a_pause_asked_for_during_the_same_relaunch() {
        // Étant donné une série en cours ; Quand le membre demande une pause puis l'arrêt pendant une suppression
        aValidatedListing(ROBE, "Robe bleue", "12.00");
        aValidatedListing(PULL, "Pull rayé", "15.00");
        deleter.beforeDeleting(() -> {
            launchAutomaticRun.pause();
            launchAutomaticRun.stop();
        });
        launch();
        // Alors l'arrêt l'emporte : la série s'arrête sans passer par la pause
        assertThat(waiting.untils()).isEmpty();
        assertThat(deleter.opened()).containsExactly(ROBE);
        assertThat(launchAutomaticRun.overview().stopReason()).contains(RunStopReason.STOPPED_BY_MEMBER);
    }

    @Test
    void a_pause_asked_for_again_while_paused_is_ignored() {
        // Étant donné une série en pause
        aValidatedListing(ROBE, "Robe bleue", "12.00");
        deleter.beforeDeleting(() -> launchAutomaticRun.pause());
        List<AutomaticRun> afterSecondPause = new ArrayList<>();
        waiting.during(() -> {
            if (launchAutomaticRun.overview().status() == RunStatus.PAUSED && afterSecondPause.isEmpty()) {
                // Quand le membre redemande une pause
                launchAutomaticRun.pause();
                afterSecondPause.add(launchAutomaticRun.overview());
                launchAutomaticRun.stop();
            }
        });
        launch();
        // Alors rien ne change : la série reste en pause, sans nouvelle demande
        assertThat(afterSecondPause).singleElement().satisfies(run -> {
            assertThat(run.status()).isEqualTo(RunStatus.PAUSED);
            assertThat(run.pauseRequested()).isFalse();
        });
    }

    @Test
    void stops_after_the_current_relaunch() {
        // Étant donné une série en cours sur deux annonces prêtes
        aValidatedListing(ROBE, "Robe bleue", "12.00");
        aValidatedListing(PULL, "Pull rayé", "15.00");
        // Quand le membre demande l'arrêt pendant la suppression de la première
        deleter.beforeDeleting(() -> {
            launchAutomaticRun.stop();
            assertThat(launchAutomaticRun.overview().stopRequested()).isTrue();
        });
        launch();
        // Alors la première suppression se termine, la seconde n'est pas touchée
        assertThat(deletions.findLatest(ROBE)).map(ListingDeletion::outcome).contains(DeletionOutcome.CONFIRMED);
        assertThat(deleter.opened()).containsExactly(ROBE);
        // Et la série est arrêtée, raison STOPPED_BY_MEMBER
        AutomaticRun run = launchAutomaticRun.overview();
        assertThat(run.status()).isEqualTo(RunStatus.STOPPED);
        assertThat(run.stopReason()).contains(RunStopReason.STOPPED_BY_MEMBER);
        assertThat(run.stopRequested()).isFalse();
    }

    @Test
    void stop_interrupts_a_wait() {
        // Étant donné une série qui attend la fin d'une limite de cadence
        deletions.save(ListingDeletion.attempted(aBackup(9, SAVED_AT), NOW.minusSeconds(60)));
        aValidatedListing(ROBE, "Robe bleue", "12.00");
        // Quand le membre demande l'arrêt
        waiting.during(() -> launchAutomaticRun.stop());
        launch();
        // Alors l'attente est interrompue, rien n'est supprimé, la série est arrêtée (STOPPED_BY_MEMBER)
        assertThat(clock.instant()).isEqualTo(NOW);
        assertThat(deletions.findLatest(ROBE)).isEmpty();
        assertThat(launchAutomaticRun.overview().stopReason()).contains(RunStopReason.STOPPED_BY_MEMBER);
        assertThat(launchAutomaticRun.overview().endedAt()).contains(NOW);
    }

    @Test
    void pause_interrupts_a_wait() {
        // Étant donné une série qui attend la fin d'une limite de cadence
        deletions.save(ListingDeletion.attempted(aBackup(9, SAVED_AT), NOW.minusSeconds(60)));
        aValidatedListing(ROBE, "Robe bleue", "12.00");
        List<RunStatus> seen = new ArrayList<>();
        waiting.during(() -> {
            seen.add(launchAutomaticRun.overview().status());
            switch (seen.size()) {
                // Quand le membre demande une pause
                case 1 -> launchAutomaticRun.pause();
                // puis reprend
                case 2 -> {
                    assertThat(deletions.findLatest(ROBE)).isEmpty();
                    launchAutomaticRun.resume();
                }
                default -> {
                }
            }
        });
        launch();
        // Alors la série passe en pause sans rien supprimer, puis supprime l'annonce à la reprise (après la limite)
        assertThat(seen).startsWith(RunStatus.WAITING, RunStatus.PAUSED, RunStatus.WAITING);
        assertThat(deletions.findLatest(ROBE)).map(ListingDeletion::attemptedAt).contains(NOW.plusSeconds(60));
    }

    @Test
    void requests_without_effect_are_ignored() {
        // Étant donné aucune série en cours
        launchAutomaticRun = newLaunchAutomaticRun();
        // Quand le membre demande pause, reprise ou arrêt
        launchAutomaticRun.pause();
        launchAutomaticRun.resume();
        launchAutomaticRun.stop();
        // Alors rien ne change
        assertThat(launchAutomaticRun.overview()).isEqualTo(AutomaticRun.idle());
        // Étant donné une série en pause
        aValidatedListing(ROBE, "Robe bleue", "12.00");
        List<AutomaticRun> afterLaunchWhilePaused = new ArrayList<>();
        deleter.beforeDeleting(() -> launchAutomaticRun.pause());
        waiting.during(() -> {
            if (launchAutomaticRun.overview().status() == RunStatus.PAUSED) {
                // Quand il clique sur « Tout lancer » pendant la pause
                clock.advance(Duration.ofSeconds(5));
                launchAutomaticRun.launch();
                afterLaunchWhilePaused.add(launchAutomaticRun.overview());
                launchAutomaticRun.stop();
            }
        });
        launchAutomaticRun.launch();
        // Alors rien ne change : même série, toujours en pause
        assertThat(afterLaunchWhilePaused).singleElement().satisfies(run -> {
            assertThat(run.status()).isEqualTo(RunStatus.PAUSED);
            assertThat(run.startedAt()).contains(NOW);
        });
        // Et une reprise sans pause ne change rien
        launchAutomaticRun.resume();
        assertThat(launchAutomaticRun.overview().status()).isEqualTo(RunStatus.STOPPED);
        assertThat(launchAutomaticRun.overview().stopReason()).contains(RunStopReason.STOPPED_BY_MEMBER);
    }

    @Test
    void nothing_to_do_finishes_at_once() {
        // Étant donné aucune annonce prête
        // Quand le membre clique sur « Tout lancer »
        assertThat(newLaunchAutomaticRun().overview().status()).isEqualTo(RunStatus.IDLE);
        launch();
        // Alors la série est terminée sans rien relancer
        AutomaticRun run = launchAutomaticRun.overview();
        assertThat(run.status()).isEqualTo(RunStatus.FINISHED);
        assertThat(run.relaunchCount()).isZero();
    }

    private void launch() {
        launchAutomaticRun = newLaunchAutomaticRun();
        launchAutomaticRun.launch();
    }

    /** The run goes on within {@code launch()}: the background executor runs it right away. */
    private LaunchAutomaticRun newLaunchAutomaticRun() {
        JournalWriter journalWriter = new JournalWriter(journal, clock);
        CadenceCheck cadence = new CadenceCheck(deletions, republications,
                new WriteCadence(Duration.ofMinutes(2), 20, 20), clock);
        ShowListingRepublication showRepublication = new ShowListingRepublication(new ShowListingDeletion(listings,
                listingBackups, photos, validations, deletions, deletionMode, delay), republications, listings,
                RepublicationMode.REAL);
        NewListingSearch newListingSearch = new NewListingSearch(wardrobe, noPause(), listings);
        InMemoryListingStatisticsRepository statistics = new InMemoryListingStatisticsRepository();
        RelaunchListing relaunch = new RelaunchListing(
                new ShowListingDetail(listings, statistics, listingBackups, validations, deletions, republications,
                        delay, clock, journal),
                deletions, delay,
                new DeleteListing(session, listings, listingBackups, photos, validations, deletions, deleter,
                        deletionMode, delay, cadence, clock, journalWriter),
                new RecheckListingDeletion(session, listings, listingBackups, photos, validations, deletions, deleter,
                        deletionMode, delay, clock, journalWriter),
                new RepublishListing(session,
                        new RepublicationChecks(listingBackups, photos, validations, deletions, republications, delay,
                                clock),
                        cadence, republications, publisher, newListingSearch, listings, RepublicationMode.REAL, clock,
                        showRepublication, journalWriter),
                new VerifyListingRepublication(session, listingBackups, republications, listings, newListingSearch,
                        contents, noBackupPause(), clock, showRepublication));
        return new LaunchAutomaticRun(new ShowListingInventory(listings, statistics, listingBackups, validations,
                deletions, republications, delay, clock, new InMemoryListingTagRepository()), relaunch, waiting, clock,
                Runnable::run);
    }

    /** Present, backed up with its photo kept, validated by the member, and shown as is on Vinted. */
    private ListingBackup aValidatedListing(VintedListingId id, String title, String price) {
        ScannedListing onVinted = aListing(id.value(), title, price, ACTIVE);
        listings.saveAll(List.of(Listing.seen(onVinted, SAVED_AT)));
        ListingReading reading = new ListingReading(aContent(id.value(), title, "Portée une fois", price),
                List.of(aPhoto("front of " + id.value())));
        reading.photos().forEach(photo -> photos.store(id, photo));
        ListingBackup backup = ListingBackup.of(reading, SAVED_AT);
        listingBackups.save(backup);
        validations.save(BackupValidation.of(backup, SAVED_AT));
        deleter.onVinted(onVinted);
        return backup;
    }

    /** Validated, then deleted on Vinted an hour ago: absent from the wardrobe. */
    private ListingBackup aDeletedListing(VintedListingId id, String title, String price) {
        ListingBackup backup = aValidatedListing(id, title, price);
        listings.saveAll(List.of(Listing.seen(aListing(id.value(), title, price, ACTIVE), SAVED_AT).absent()));
        deleter.gone(id);
        deletions.save(ListingDeletion.attempted(backup, NOW.minusSeconds(3605)).confirmed(NOW.minusSeconds(3600)));
        return backup;
    }

    private static ListingContent sameAs(ListingContent saved, long newId) {
        return new ListingContent(new VintedListingId(newId), saved.title(), saved.description(), saved.price(),
                saved.category(), saved.brand(), saved.condition(), saved.size(), saved.colors(), saved.materials(),
                saved.packageSize(), saved.details());
    }

    /** Vinted as the deleter sees it: one page per listing, gone once its deletion is confirmed. */
    private static final class VintedDeleter implements ListingDeleter {

        private final Map<VintedListingId, ScannedListing> shown = new HashMap<>();
        private final List<VintedListingId> opened = new ArrayList<>();
        private final List<VintedListingId> staying = new ArrayList<>();
        private Runnable beforeDeleting = () -> {
        };
        private boolean disconnected;

        void staysOnceConfirmed(VintedListingId id) {
            staying.add(id);
        }

        void beforeDeleting(Runnable action) {
            beforeDeleting = action;
        }

        void onVinted(ScannedListing listing) {
            shown.put(listing.id(), listing);
        }

        void gone(VintedListingId id) {
            shown.remove(id);
        }

        void disconnected() {
            disconnected = true;
        }

        List<VintedListingId> opened() {
            return opened;
        }

        @Override
        public Optional<ScannedListing> delete(ListingLink listing, DeletionVisit visit) {
            opened.add(listing.id());
            if (disconnected) {
                throw new SessionDisconnected("redirected to login");
            }
            ScannedListing read = shown.get(listing.id());
            if (!visit.agreesToDelete(read)) {
                return Optional.of(read);
            }
            beforeDeleting.run();
            visit.aboutToConfirm();
            if (staying.contains(listing.id())) {
                return Optional.of(read);
            }
            shown.remove(listing.id());
            return Optional.empty();
        }

        @Override
        public Optional<ScannedListing> show(ListingLink listing) {
            opened.add(listing.id());
            return Optional.ofNullable(shown.get(listing.id()));
        }
    }

    /** Waits by moving the clock forward, so a run that waits five minutes takes no time. */
    private final class ClockWaiting implements RunWaiting {

        private final List<Instant> untils = new ArrayList<>();
        private Runnable during = () -> {
        };

        void during(Runnable action) {
            during = action;
        }

        List<Instant> untils() {
            return untils;
        }

        @Override
        public void until(Instant instant, BooleanSupplier interrupted) {
            untils.add(instant);
            during.run();
            if (interrupted.getAsBoolean()) {
                return;
            }
            if (instant.isAfter(clock.instant())) {
                clock.set(instant);
            }
        }
    }
}
