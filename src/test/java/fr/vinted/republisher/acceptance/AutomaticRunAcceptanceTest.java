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
import fr.vinted.republisher.domain.cadence.WriteOrigin;
import fr.vinted.republisher.domain.deletion.DeletionMode;
import fr.vinted.republisher.domain.deletion.DeletionOutcome;
import fr.vinted.republisher.domain.deletion.DeletionVisit;
import fr.vinted.republisher.domain.deletion.ListingDeleter;
import fr.vinted.republisher.domain.deletion.ListingDeletion;
import fr.vinted.republisher.domain.journal.ActionRecord;
import fr.vinted.republisher.domain.relaunch.RelaunchAction;
import fr.vinted.republisher.domain.republication.ListingRepublication;
import fr.vinted.republisher.domain.republication.RepublicationDelay;
import fr.vinted.republisher.domain.republication.RepublicationMode;
import fr.vinted.republisher.domain.republication.RepublicationOutcome;
import fr.vinted.republisher.domain.run.AutomaticRun;
import fr.vinted.republisher.domain.run.ParallelismMode;
import fr.vinted.republisher.domain.run.RunStatus;
import fr.vinted.republisher.domain.run.RunStopReason;
import fr.vinted.republisher.domain.run.RunWaiting;
import fr.vinted.republisher.domain.run.RunWaitingReason;
import fr.vinted.republisher.domain.run.RunWorker;
import fr.vinted.republisher.domain.run.RunWorkerKind;
import fr.vinted.republisher.domain.run.RunWorkerState;
import fr.vinted.republisher.domain.sale.ListingSale;
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
import fr.vinted.republisher.fakes.InMemoryBackupVerificationRepository;
import fr.vinted.republisher.fakes.InMemoryListingBackupRepository;
import fr.vinted.republisher.fakes.InMemoryListingDeletionRepository;
import fr.vinted.republisher.fakes.InMemoryListingRepository;
import fr.vinted.republisher.fakes.InMemoryListingRepublicationRepository;
import fr.vinted.republisher.fakes.InMemoryListingSaleRepository;
import fr.vinted.republisher.fakes.InMemoryListingStatisticsRepository;
import fr.vinted.republisher.fakes.InMemoryListingTagRepository;
import fr.vinted.republisher.fakes.InMemoryPhotoStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static fr.vinted.republisher.domain.backup.BackupFixtures.aBackup;
import static fr.vinted.republisher.domain.backup.BackupFixtures.aContent;
import static fr.vinted.republisher.domain.backup.BackupFixtures.aPhoto;
import static fr.vinted.republisher.domain.backup.BackupFixtures.noBackupPause;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.ACTIVE;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.INACTIVE;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.aListing;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.noPause;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/** US-19: one click relaunches every ready listing in turn, waiting when it must and stopping on a problem. */
class AutomaticRunAcceptanceTest {

    private static final VintedListingId ROBE = new VintedListingId(1);
    private static final VintedListingId PULL = new VintedListingId(2);
    private static final VintedListingId JUPE = new VintedListingId(3);
    private static final Instant SAVED_AT = Instant.parse("2026-11-10T10:00:00Z");
    private static final Instant NOW = Instant.parse("2026-11-11T10:00:00Z");

    private final InMemoryListingRepository listings = new InMemoryListingRepository();
    private final InMemoryListingBackupRepository listingBackups = new InMemoryListingBackupRepository();
    private final InMemoryPhotoStorage photos = new InMemoryPhotoStorage();
    private final InMemoryBackupValidationRepository validations = new InMemoryBackupValidationRepository();
    private final InMemoryListingDeletionRepository deletions = new InMemoryListingDeletionRepository();
    private final InMemoryListingRepublicationRepository republications = new InMemoryListingRepublicationRepository();
    private final InMemoryActionJournal journal = new InMemoryActionJournal();
    private final InMemoryListingSaleRepository sales = new InMemoryListingSaleRepository();
    private final VintedDeleter deleter = new VintedDeleter();
    private final FakeWardrobeReader wardrobe = new FakeWardrobeReader();
    private final FakeListingPublisher publisher = new FakeListingPublisher(wardrobe);
    private final FakeListingContentReader contents = new FakeListingContentReader();
    private final AdjustableClock clock = new AdjustableClock(NOW);
    private final RepublicationDelay delay = new RepublicationDelay(Duration.ofMinutes(5));
    private final ClockWaiting waiting = new ClockWaiting();
    private VintedSession session = new VintedSession();
    private ParallelismMode mode = ParallelismMode.SEQUENTIAL;
    private ExecutorService relaunchers;
    private DeletionMode deletionMode = DeletionMode.REAL;
    private LaunchAutomaticRun launchAutomaticRun;

    @AfterEach
    void stopRelaunchers() {
        if (relaunchers != null) {
            relaunchers.shutdownNow();
        }
    }

    /** US-46: three workers, each relaunching on its own thread as in its own tab. */
    private void withWorkers() {
        mode = ParallelismMode.WORKERS;
        session = new VintedSession(mode.listingActions());
        relaunchers = Executors.newFixedThreadPool(mode.listingActions());
    }

    private final AtomicReference<AutomaticRun> whenTheyMet = new AtomicReference<>();

    /**
     * Holds the calling action until the given workers relaunch at the same time, once, keeping the run as it was
     * then; afterwards, lets every action through.
     */
    private void meet(RunWorkerKind... kinds) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (whenTheyMet.get() == null) {
            AutomaticRun run = launchAutomaticRun.overview();
            if (Arrays.stream(kinds).allMatch(kind -> run.worker(kind).state() == RunWorkerState.WORKING)) {
                whenTheyMet.compareAndSet(null, run);
                return;
            }
            if (System.nanoTime() > deadline) {
                throw new IllegalStateException("the workers did not relaunch at the same time: " + run.workers());
            }
            Thread.yield();
        }
    }

    private ListingBackup aRepublicationToCheck(VintedListingId id, String title, String price, long newId) {
        ListingBackup backup = aDeletedListing(id, title, price);
        republications.save(ListingRepublication.attempted(backup, NOW.minusSeconds(600), WriteOrigin.RUN));
        wardrobe.holding(aListing(newId, title, price, ACTIVE));
        contents.showing(new ListingReading(sameAs(backup.content(), newId), List.of(aPhoto("front"))));
        return backup;
    }

    /** Its deletion was tried and is done on Vinted: a recheck confirms it. */
    private void aDeletionToCheck(VintedListingId id, String title, String price) {
        deletions.save(ListingDeletion.attempted(aValidatedListing(id, title, price), NOW.minusSeconds(600),
                WriteOrigin.RUN));
        deleter.gone(id);
    }

    @Nested
    class Workers {

        @Test
        void each_worker_relaunches_its_own_steps_at_the_same_time() {
            // AT-46-02
            // Étant donné une annonce « À republier », une « Prête à supprimer » validée et une « Republication à
            // vérifier », en mode réel
            withWorkers();
            aDeletedListing(PULL, "Pull rayé", "15.00");
            publisher.showingOncePublished(aListing(12, "Pull rayé", "15.00", ACTIVE));
            aValidatedListing(JUPE, "Jupe plissée", "9.00");
            aRepublicationToCheck(ROBE, "Robe bleue", "12.00", 11);
            Runnable allThree = () -> meet(RunWorkerKind.REPUBLICATION, RunWorkerKind.DELETION,
                    RunWorkerKind.VERIFICATION);
            deleter.onOpening(allThree);
            wardrobe.during(allThree);
            // Quand la série tourne
            launch();
            // Alors la republication, la suppression et la vérification sont en cours en même temps, chacune par son
            // worker et dans son onglet
            // Et l'aperçu montre chaque worker en action avec son annonce et son action
            assertThat(whenTheyMet.get().workers()).extracting(RunWorker::kind, RunWorker::listing, RunWorker::action)
                    .containsExactly(
                            tuple(RunWorkerKind.REPUBLICATION, Optional.of(PULL),
                                    Optional.of(RelaunchAction.REPUBLICATION)),
                            tuple(RunWorkerKind.DELETION, Optional.of(JUPE), Optional.of(RelaunchAction.DELETION)),
                            tuple(RunWorkerKind.VERIFICATION, Optional.of(ROBE),
                                    Optional.of(RelaunchAction.REPUBLICATION_VERIFICATION)));
            assertThat(launchAutomaticRun.overview().status()).isEqualTo(RunStatus.FINISHED);
        }

        @Test
        void a_slow_action_does_not_hold_the_other_workers() {
            // AT-46-03
            // Étant donné une vérification de republication lente et deux annonces « Prêtes à supprimer » en dry-run
            withWorkers();
            deletionMode = DeletionMode.DRY_RUN;
            aRepublicationToCheck(ROBE, "Robe bleue", "12.00", 11);
            aValidatedListing(PULL, "Pull rayé", "15.00");
            aValidatedListing(JUPE, "Jupe plissée", "9.00");
            CountDownLatch bothDeletionsOpened = new CountDownLatch(2);
            deleter.onOpening(bothDeletionsOpened::countDown);
            List<Boolean> verificationSawBothDeletions = Collections.synchronizedList(new ArrayList<>());
            wardrobe.during(() -> verificationSawBothDeletions.add(awaits(bothDeletionsOpened)));
            // Quand la série tourne
            launch();
            // Alors le worker suppression relance les deux annonces l'une après l'autre pendant que la vérification
            // est encore en cours
            assertThat(deleter.opened()).containsExactly(PULL, JUPE);
            assertThat(verificationSawBothDeletions).first().isEqualTo(true);
            assertThat(launchAutomaticRun.overview().status()).isEqualTo(RunStatus.FINISHED);
        }

        @Test
        void the_verification_worker_helps_with_deletion_rechecks() {
            // AT-46-04
            // Étant donné deux annonces « Suppression à vérifier » et aucune republication à vérifier
            withWorkers();
            aDeletionToCheck(ROBE, "Robe bleue", "12.00");
            aDeletionToCheck(PULL, "Pull rayé", "15.00");
            deleter.onOpening(() -> meet(RunWorkerKind.DELETION, RunWorkerKind.VERIFICATION));
            // Quand la série tourne
            launch();
            // Alors le worker suppression et le worker vérification revérifient chacun une des deux annonces, jamais
            // la même
            AutomaticRun met = whenTheyMet.get();
            assertThat(List.of(met.worker(RunWorkerKind.DELETION).listing().orElseThrow(),
                    met.worker(RunWorkerKind.VERIFICATION).listing().orElseThrow()))
                    .containsExactlyInAnyOrder(ROBE, PULL);
            assertThat(met.workers()).extracting(RunWorker::action).filteredOn(Optional::isPresent)
                    .containsOnly(Optional.of(RelaunchAction.DELETION_RECHECK));
        }

        @Test
        void writes_stay_with_their_own_worker() {
            // AT-46-04
            // Étant donné une annonce « Prête à supprimer » et un worker republication sans annonce
            withWorkers();
            deletionMode = DeletionMode.DRY_RUN;
            aValidatedListing(ROBE, "Robe bleue", "12.00");
            List<AutomaticRun> whileDeleting = Collections.synchronizedList(new ArrayList<>());
            deleter.onOpening(() -> whileDeleting.add(launchAutomaticRun.overview()));
            // Quand la série tourne
            launch();
            // Alors seul le worker suppression la supprime
            assertThat(whileDeleting).singleElement().satisfies(run -> {
                assertThat(run.worker(RunWorkerKind.DELETION).worksOn(ROBE)).isTrue();
                assertThat(run.worker(RunWorkerKind.REPUBLICATION).state()).isNotEqualTo(RunWorkerState.WORKING);
                assertThat(run.worker(RunWorkerKind.VERIFICATION).state()).isNotEqualTo(RunWorkerState.WORKING);
            });
            assertThat(launchAutomaticRun.overview().worker(RunWorkerKind.DELETION).relaunchCount()).isEqualTo(1);
        }

        @Test
        void a_wait_only_holds_its_own_worker() {
            // AT-46-06
            // Étant donné deux annonces « Prêtes à supprimer » validées, en mode réel, et une annonce « À republier »
            withWorkers();
            aValidatedListing(ROBE, "Robe bleue", "12.00");
            aValidatedListing(PULL, "Pull rayé", "15.00");
            aDeletedListing(JUPE, "Jupe plissée", "9.00");
            publisher.showingOncePublished(aListing(13, "Jupe plissée", "9.00", ACTIVE),
                    aListing(11, "Robe bleue", "12.00", ACTIVE));
            CountDownLatch republished = new CountDownLatch(1);
            wardrobe.during(republished::countDown);
            List<RunWorker> deletionWorkerWaiting = Collections.synchronizedList(new ArrayList<>());
            List<Boolean> republishedDuringTheWait = Collections.synchronizedList(new ArrayList<>());
            waiting.during(() -> {
                RunWorker deletion = launchAutomaticRun.overview().worker(RunWorkerKind.DELETION);
                if (deletion.state() == RunWorkerState.WAITING && deletionWorkerWaiting.isEmpty()) {
                    deletionWorkerWaiting.add(deletion);
                    republishedDuringTheWait.add(awaits(republished));
                }
            });
            // Quand la série tourne
            launch();
            // Alors le worker suppression supprime la première puis attend 2 minutes pour la seconde
            assertThat(deletions.findLatest(ROBE)).map(ListingDeletion::attemptedAt).contains(NOW);
            assertThat(deletions.findLatest(PULL)).map(ListingDeletion::attemptedAt).get()
                    .satisfies(attemptedAt -> assertThat(attemptedAt).isAfterOrEqualTo(NOW.plusSeconds(120)));
            // Et pendant cette attente le worker republication remet en vente l'annonce « À republier »
            assertThat(republishedDuringTheWait).containsExactly(true);
            assertThat(republications.findLatest(JUPE)).map(ListingRepublication::outcome)
                    .contains(RepublicationOutcome.PUBLISHED);
            // Et l'aperçu montre le worker suppression en attente (cadence) avec sa date
            assertThat(deletionWorkerWaiting).singleElement().satisfies(worker -> {
                assertThat(worker.waitingUntil()).contains(NOW.plusSeconds(120));
                assertThat(worker.waitingReason()).contains(RunWaitingReason.CADENCE);
            });
            assertNoCadenceRefusal();
        }

        @Test
        void an_idle_worker_picks_up_the_work_another_one_left() {
            // AT-46-07
            // Étant donné une annonce « Prête à supprimer » validée et aucune autre candidate
            withWorkers();
            aValidatedListing(ROBE, "Robe bleue", "12.00");
            publisher.showingOncePublished(aListing(11, "Robe bleue", "12.00", ACTIVE));
            // Quand la série tourne et que la suppression aboutit (annonce « En attente »)
            launch();
            // Alors le worker republication, jusque-là inactif, la remet en vente une fois le délai passé
            AutomaticRun run = launchAutomaticRun.overview();
            assertThat(run.worker(RunWorkerKind.DELETION).relaunchCount()).isEqualTo(1);
            assertThat(run.worker(RunWorkerKind.REPUBLICATION).relaunchCount()).isEqualTo(1);
            assertThat(republications.findLatest(ROBE)).map(ListingRepublication::outcome)
                    .contains(RepublicationOutcome.PUBLISHED);
            // Et la série est terminée quand les trois workers sont inactifs
            assertThat(run.status()).isEqualTo(RunStatus.FINISHED);
            assertThat(run.relaunchCount()).isEqualTo(2);
        }

        @Test
        void never_two_workers_on_the_same_listing() {
            // AT-46-08
            // Étant donné une seule annonce « Suppression à vérifier »
            withWorkers();
            aDeletionToCheck(ROBE, "Robe bleue", "12.00");
            List<AutomaticRun> whileRechecking = Collections.synchronizedList(new ArrayList<>());
            deleter.onOpening(() -> whileRechecking.add(launchAutomaticRun.overview()));
            // Quand la série tourne
            launch();
            // Alors un seul worker la revérifie, l'autre reste inactif
            assertThat(deleter.opened()).containsExactly(ROBE);
            assertThat(whileRechecking).singleElement().satisfies(run ->
                    assertThat(run.workers()).filteredOn(worker -> worker.worksOn(ROBE)).hasSize(1));
        }

        @Test
        void pause_lets_every_action_in_progress_finish() {
            // AT-46-09
            // Étant donné deux workers en action, et une annonce « Prête à supprimer » en dry-run après eux
            withWorkers();
            deletionMode = DeletionMode.DRY_RUN;
            aDeletionToCheck(ROBE, "Robe bleue", "12.00");
            aDeletionToCheck(PULL, "Pull rayé", "15.00");
            aValidatedListing(JUPE, "Jupe plissée", "9.00");
            deleter.onOpening(() -> {
                meet(RunWorkerKind.DELETION, RunWorkerKind.VERIFICATION);
                // Quand le membre demande une pause
                launchAutomaticRun.pause();
            });
            List<Integer> openedWhilePaused = Collections.synchronizedList(new ArrayList<>());
            waiting.during(() -> {
                if (launchAutomaticRun.overview().status() == RunStatus.PAUSED) {
                    openedWhilePaused.add(deleter.opened().size());
                    // Quand il reprend
                    launchAutomaticRun.resume();
                }
            });
            launch();
            // Alors chaque worker finit son action, n'en prend pas d'autre, et la série est en pause quand tous ont
            // fini
            assertThat(openedWhilePaused).first().isEqualTo(2);
            // Alors les workers reprennent
            assertThat(deleter.opened()).containsExactlyInAnyOrder(ROBE, PULL, JUPE);
            assertThat(launchAutomaticRun.overview().status()).isEqualTo(RunStatus.FINISHED);
        }

        @Test
        void stop_cuts_a_wait_short() {
            // AT-46-09
            // Étant donné deux annonces « Prêtes à supprimer » : le worker suppression attend la cadence pour la
            // seconde
            withWorkers();
            aValidatedListing(ROBE, "Robe bleue", "12.00");
            aValidatedListing(PULL, "Pull rayé", "15.00");
            // Quand le membre demande l'arrêt pendant qu'un worker attend
            waiting.during(() -> launchAutomaticRun.stop());
            launch();
            // Alors l'attente est interrompue et la série s'arrête quand plus aucun worker n'est en action
            AutomaticRun run = launchAutomaticRun.overview();
            assertThat(run.status()).isEqualTo(RunStatus.STOPPED);
            assertThat(run.stopReason()).contains(RunStopReason.STOPPED_BY_MEMBER);
            assertThat(deletions.findLatest(PULL)).isEmpty();
            assertThat(run.workers()).extracting(RunWorker::state).containsOnly(RunWorkerState.STOPPED);
        }

        @Test
        void an_account_problem_stops_every_worker() {
            // AT-46-10
            // Étant donné deux workers en action, et une annonce « Prête à supprimer » après eux
            withWorkers();
            aDeletionToCheck(ROBE, "Robe bleue", "12.00");
            aDeletionToCheck(PULL, "Pull rayé", "15.00");
            aValidatedListing(JUPE, "Jupe plissée", "9.00");
            deleter.onOpening(() -> meet(RunWorkerKind.DELETION, RunWorkerKind.VERIFICATION));
            // Quand le worker suppression trouve la session Vinted déconnectée
            deleter.disconnected();
            launch();
            // Alors les deux autres finissent leur action et n'en prennent plus
            assertThat(deleter.opened()).containsExactlyInAnyOrder(ROBE, PULL);
            // Et la série s'arrête avec la raison SESSION_DISCONNECTED
            AutomaticRun run = launchAutomaticRun.overview();
            assertThat(run.status()).isEqualTo(RunStatus.STOPPED);
            assertThat(run.stopReason()).contains(RunStopReason.SESSION_DISCONNECTED);
        }

        @Test
        void a_manual_relaunch_holding_a_listing_makes_its_worker_wait() {
            // AT-46-11
            // Étant donné une relance manuelle en cours sur une annonce « Prête à supprimer »
            withWorkers();
            deletionMode = DeletionMode.DRY_RUN;
            aValidatedListing(ROBE, "Robe bleue", "12.00");
            session.start(BrowserAction.DELETING, ROBE);
            List<RunWorker> deletionWorkerWaiting = Collections.synchronizedList(new ArrayList<>());
            waiting.during(() -> {
                deletionWorkerWaiting.add(launchAutomaticRun.overview().worker(RunWorkerKind.DELETION));
                session.finish(ROBE);
            });
            // Quand la série tourne
            launch();
            // Alors le worker qui en avait besoin attend 30 secondes (navigateur occupé), puis la relance
            assertThat(deletionWorkerWaiting).first().satisfies(worker -> {
                assertThat(worker.waitingUntil()).contains(NOW.plusSeconds(30));
                assertThat(worker.waitingReason()).contains(RunWaitingReason.BROWSER_BUSY);
            });
            assertThat(deleter.opened()).containsExactly(ROBE);
        }
    }

    private static boolean awaits(CountDownLatch latch) {
        try {
            return latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

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
        republications.save(ListingRepublication.attempted(robe, NOW.minusSeconds(600), WriteOrigin.RUN));
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
    void the_run_leaves_sold_listings_alone() {
        // AT-50-02 – Étant donné l'annonce 1 « Vendue » et l'annonce 2 « À supprimer »
        aValidatedListing(ROBE, "Robe bleue", "12.00");
        sales.addAll(List.of(new ListingSale(ROBE, NOW.minusSeconds(60))));
        aValidatedListing(PULL, "Pull rayé", "15.00");
        publisher.showingOncePublished(aListing(12, "Pull rayé", "15.00", ACTIVE));
        // Quand je lance la série
        launch();
        // Alors seule l'annonce 2 est supprimée
        assertThat(deletions.findLatest(ROBE)).isEmpty();
        assertThat(deletions.findLatest(PULL)).map(ListingDeletion::outcome).contains(DeletionOutcome.CONFIRMED);
        // Et l'annonce 1 n'est jamais ouverte
        assertThat(deleter.opened()).doesNotContain(ROBE);
        assertThat(launchAutomaticRun.overview().status()).isEqualTo(RunStatus.FINISHED);
    }

    @Test
    void a_listing_sold_meanwhile_is_set_aside_and_the_run_goes_on() {
        // AT-50-04 – Étant donné l'annonce 1 « À supprimer », que l'outil ne sait pas encore vendue
        aValidatedListing(ROBE, "Robe bleue", "12.00");
        // Et sa page sur Vinted la montre vendue
        deleter.onVinted(aListing(1, "Robe bleue", "12.00", INACTIVE));
        // Et l'annonce 2 « À supprimer »
        aValidatedListing(PULL, "Pull rayé", "15.00");
        publisher.showingOncePublished(aListing(12, "Pull rayé", "15.00", ACTIVE));
        // Quand je lance la série
        launch();
        // Alors la suppression de l'annonce 1 est refusée, sans aucun clic, et l'annonce 1 est écartée de la série
        assertThat(deletions.findLatest(ROBE)).isEmpty();
        assertThat(launchAutomaticRun.overview().skippedListings()).containsExactly(ROBE);
        // Et l'annonce 2 est supprimée
        assertThat(deletions.findLatest(PULL)).map(ListingDeletion::outcome).contains(DeletionOutcome.CONFIRMED);
        assertThat(launchAutomaticRun.overview().status()).isEqualTo(RunStatus.FINISHED);
    }

    @Test
    void listing_still_to_check_is_skipped() {
        // Étant donné une annonce « Republication à vérifier » dont la nouvelle annonce n'est pas dans le dressing
        ListingBackup robe = aDeletedListing(ROBE, "Robe bleue", "12.00");
        republications.save(ListingRepublication.attempted(robe, NOW.minusSeconds(600), WriteOrigin.RUN));
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
        deletions.save(ListingDeletion.attempted(aBackup(9, SAVED_AT), NOW.minusSeconds(60), WriteOrigin.RUN));
        aValidatedListing(ROBE, "Robe bleue", "12.00");
        // Quand la série tourne
        launch();
        // Alors elle attend une minute sans tenter la suppression, puis supprime l'annonce
        assertThat(waiting.untils()).first().isEqualTo(NOW.plusSeconds(60));
        assertThat(deletions.findLatest(ROBE)).map(ListingDeletion::attemptedAt).contains(NOW.plusSeconds(60));
        // Et le journal n'a aucun refus « limite de cadence atteinte »
        assertNoCadenceRefusal();
    }

    @Test
    void manual_writes_do_not_count_in_the_run_cadence() {
        // Étant donné une suppression manuelle faite il y a 10 secondes (AT-49-04)
        deletions.save(ListingDeletion.attempted(aBackup(9, SAVED_AT), NOW.minusSeconds(10), WriteOrigin.MANUAL));
        aValidatedListing(ROBE, "Robe bleue", "12.00");
        // Quand la série cherche à supprimer une annonce
        launch();
        // Alors elle la supprime sans attendre (sa seule attente est ensuite la date de republication)
        assertThat(waiting.untils()).first().isEqualTo(NOW.plus(Duration.ofMinutes(5)));
        assertThat(deletions.findLatest(ROBE)).map(ListingDeletion::attemptedAt).contains(NOW);
        assertThat(deletions.findLatest(ROBE)).map(ListingDeletion::origin).contains(WriteOrigin.RUN);
    }

    @Test
    void waits_for_the_cadence_limit_before_republishing() {
        // Étant donné une republication sur Vinted il y a une minute, intervalle 2 minutes, et une annonce à republier
        republications.save(ListingRepublication.attempted(aBackup(9, SAVED_AT), NOW.minusSeconds(60),
                WriteOrigin.RUN));
        aDeletedListing(ROBE, "Robe bleue", "12.00");
        // Quand la série tourne
        launch();
        // Alors elle attend une minute sans tenter la republication, puis republie l'annonce
        assertThat(waiting.untils()).first().isEqualTo(NOW.plusSeconds(60));
        assertThat(republications.findLatest(ROBE)).map(ListingRepublication::attemptedAt)
                .contains(NOW.plusSeconds(60));
        assertNoCadenceRefusal();
    }

    @Test
    void dry_run_does_not_wait_for_the_cadence() {
        // Étant donné une écriture sur Vinted il y a une minute, et une annonce « Prête à supprimer » en dry-run
        deletionMode = DeletionMode.DRY_RUN;
        deletions.save(ListingDeletion.attempted(aBackup(9, SAVED_AT), NOW.minusSeconds(60), WriteOrigin.RUN));
        aValidatedListing(ROBE, "Robe bleue", "12.00");
        // Quand la série tourne
        launch();
        // Alors elle passe l'annonce en dry-run sans attendre
        assertThat(deleter.opened()).containsExactly(ROBE);
        assertThat(waiting.untils()).isEmpty();
    }

    private void assertNoCadenceRefusal() {
        assertThat(journal.records()).extracting(ActionRecord::reason)
                .doesNotContain(Optional.of("CADENCE_LIMIT_REACHED"));
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
        deletions.save(ListingDeletion.attempted(aBackup(9, SAVED_AT), NOW.minusSeconds(60), WriteOrigin.RUN));
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
    void a_pause_asked_for_right_after_a_resume_takes_effect_at_once() {
        // Étant donné une série en pause, deux annonces « Prêtes à supprimer » en dry-run
        deletionMode = DeletionMode.DRY_RUN;
        aValidatedListing(ROBE, "Robe bleue", "12.00");
        aValidatedListing(PULL, "Pull rayé", "15.00");
        deleter.onOpening(() -> {
            if (deleter.opened().size() == 1) {
                launchAutomaticRun.pause();
            }
        });
        AtomicInteger resumes = new AtomicInteger();
        waiting.during(() -> {
            if (launchAutomaticRun.overview().status() == RunStatus.PAUSED) {
                launchAutomaticRun.resume();
                // Quand le membre reprend puis demande aussitôt une nouvelle pause
                if (resumes.incrementAndGet() == 1) {
                    launchAutomaticRun.pause();
                }
            }
        });
        launch();
        // Alors la série se remet en pause tout de suite, sans attendre la fin du pas de pause
        assertThat(waiting.untils()).containsExactly(NOW.plus(Duration.ofHours(1)), NOW.plus(Duration.ofHours(1)));
        assertThat(launchAutomaticRun.overview().status()).isEqualTo(RunStatus.FINISHED);
    }

    @Test
    void a_failure_to_start_the_workers_stops_the_run() {
        // Étant donné le mode WORKERS et des fils de workers qui refusent de démarrer
        withWorkers();
        relaunchers.shutdownNow();
        aValidatedListing(ROBE, "Robe bleue", "12.00");
        // Quand la série est lancée
        launch();
        // Alors elle s'arrête en échec, au lieu de rester « en cours »
        AutomaticRun run = launchAutomaticRun.overview();
        assertThat(run.status()).isEqualTo(RunStatus.STOPPED);
        assertThat(run.stopReason()).contains(RunStopReason.RUN_FAILED);
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
        deletions.save(ListingDeletion.attempted(aBackup(9, SAVED_AT), NOW.minusSeconds(60), WriteOrigin.RUN));
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
        deletions.save(ListingDeletion.attempted(aBackup(9, SAVED_AT), NOW.minusSeconds(60), WriteOrigin.RUN));
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
                new WriteCadence(Duration.ofMinutes(2), Duration.ofMinutes(2), 20, 20), clock);
        ShowListingRepublication showRepublication = new ShowListingRepublication(new ShowListingDeletion(listings,
                listingBackups, photos, validations, deletions, deletionMode, delay), republications, listings,
                RepublicationMode.REAL);
        NewListingSearch newListingSearch = new NewListingSearch(wardrobe, noPause(), listings);
        InMemoryListingStatisticsRepository statistics = new InMemoryListingStatisticsRepository();
        RelaunchListing relaunch = new RelaunchListing(
                new ShowListingDetail(listings, statistics, listingBackups, validations, deletions, republications,
                        delay, clock, journal, new InMemoryBackupVerificationRepository(), sales),
                deletions, delay,
                new DeleteListing(session, listings, listingBackups, photos, validations, deletions, deleter,
                        deletionMode, delay, cadence, clock, journalWriter),
                new RecheckListingDeletion(session, listings, listingBackups, photos, validations, deletions, deleter,
                        deletionMode, delay, clock, journalWriter),
                new RepublishListing(session,
                        new RepublicationChecks(listingBackups, photos, validations, deletions, republications, delay,
                                clock),
                        cadence, republications, publisher, newListingSearch, listings, RepublicationMode.REAL, clock,
                        showRepublication, journalWriter, (instant, interrupted) -> { }, Duration.ZERO),
                new VerifyListingRepublication(session, listingBackups, republications, deletions, listings, newListingSearch,
                        contents, noBackupPause(), clock, showRepublication, journalWriter));
        return new LaunchAutomaticRun(new ShowListingInventory(listings, statistics, listingBackups, validations,
                deletions, republications, delay, clock, new InMemoryListingTagRepository(),
                new InMemoryBackupVerificationRepository(), sales), relaunch, waiting, clock,
                Runnable::run, mode, relaunchers == null ? Runnable::run : relaunchers);
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
        deletions.save(ListingDeletion.attempted(backup, NOW.minusSeconds(3605),
                WriteOrigin.RUN).confirmed(NOW.minusSeconds(3600)));
        return backup;
    }

    private static ListingContent sameAs(ListingContent saved, long newId) {
        return new ListingContent(new VintedListingId(newId), saved.title(), saved.description(), saved.price(),
                saved.category(), saved.brand(), saved.condition(), saved.size(), saved.colors(), saved.materials(),
                saved.packageSize(), saved.details());
    }

    /** Vinted as the deleter sees it: one page per listing, gone once its deletion is confirmed. */
    private static final class VintedDeleter implements ListingDeleter {

        private final Map<VintedListingId, ScannedListing> shown = new ConcurrentHashMap<>();
        private final List<VintedListingId> opened = Collections.synchronizedList(new ArrayList<>());
        private final List<VintedListingId> staying = new ArrayList<>();
        private Runnable beforeDeleting = () -> {
        };
        private Runnable onOpening = () -> {
        };

        void onOpening(Runnable action) {
            onOpening = action;
        }
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
            onOpening.run();
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
            onOpening.run();
            if (disconnected) {
                throw new SessionDisconnected("redirected to login");
            }
            return Optional.ofNullable(shown.get(listing.id()));
        }
    }

    /** Waits by moving the clock forward, so a run that waits five minutes takes no time. */
    private final class ClockWaiting implements RunWaiting {

        private final List<Instant> untils = Collections.synchronizedList(new ArrayList<>());
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
