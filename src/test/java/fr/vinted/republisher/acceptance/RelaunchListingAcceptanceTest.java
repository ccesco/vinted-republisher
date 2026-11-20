package fr.vinted.republisher.acceptance;

import fr.vinted.republisher.application.cadence.CadenceCheck;
import fr.vinted.republisher.application.deletion.DeleteListing;
import fr.vinted.republisher.application.deletion.RecheckListingDeletion;
import fr.vinted.republisher.application.deletion.ShowListingDeletion;
import fr.vinted.republisher.application.inventory.ShowListingDetail;
import fr.vinted.republisher.application.journal.JournalWriter;
import fr.vinted.republisher.application.relaunch.RelaunchListing;
import fr.vinted.republisher.application.republication.NewListingSearch;
import fr.vinted.republisher.application.republication.RepublicationChecks;
import fr.vinted.republisher.application.republication.RepublishListing;
import fr.vinted.republisher.application.republication.ShowListingRepublication;
import fr.vinted.republisher.application.republication.VerifyListingRepublication;
import fr.vinted.republisher.domain.backup.BackupValidation;
import fr.vinted.republisher.domain.backup.ListingBackup;
import fr.vinted.republisher.domain.backup.ListingContent;
import fr.vinted.republisher.domain.backup.ListingReading;
import fr.vinted.republisher.domain.cadence.CadenceLimitReached;
import fr.vinted.republisher.domain.cadence.WriteCadence;
import fr.vinted.republisher.domain.deletion.DeletionMode;
import fr.vinted.republisher.domain.deletion.DeletionOutcome;
import fr.vinted.republisher.domain.deletion.ListingDeletion;
import fr.vinted.republisher.domain.inventory.ListingNotFound;
import fr.vinted.republisher.domain.inventory.ListingStage;
import fr.vinted.republisher.domain.journal.ActionOutcome;
import fr.vinted.republisher.domain.journal.ActionRecord;
import fr.vinted.republisher.domain.journal.JournaledAction;
import fr.vinted.republisher.domain.relaunch.NotRelaunchable;
import fr.vinted.republisher.domain.sale.ListingSale;
import fr.vinted.republisher.domain.relaunch.RelaunchAction;
import fr.vinted.republisher.domain.relaunch.RelaunchReport;
import fr.vinted.republisher.domain.relaunch.StageChanged;
import fr.vinted.republisher.domain.republication.ListingRepublication;
import fr.vinted.republisher.domain.republication.RepublicationDelay;
import fr.vinted.republisher.domain.republication.RepublicationMode;
import fr.vinted.republisher.domain.republication.RepublicationOutcome;
import fr.vinted.republisher.domain.session.VintedSession;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.ScannedListing;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import fr.vinted.republisher.fakes.AdjustableClock;
import fr.vinted.republisher.fakes.InMemoryListingSaleRepository;
import fr.vinted.republisher.fakes.FakeListingContentReader;
import fr.vinted.republisher.fakes.FakeListingDeleter;
import fr.vinted.republisher.fakes.FakeListingPublisher;
import fr.vinted.republisher.fakes.FakeWardrobeReader;
import fr.vinted.republisher.fakes.InMemoryActionJournal;
import fr.vinted.republisher.fakes.InMemoryBackupValidationRepository;
import fr.vinted.republisher.fakes.InMemoryBackupVerificationRepository;
import fr.vinted.republisher.fakes.InMemoryListingBackupRepository;
import fr.vinted.republisher.fakes.InMemoryListingDeletionRepository;
import fr.vinted.republisher.fakes.InMemoryListingRepository;
import fr.vinted.republisher.fakes.InMemoryListingRepublicationRepository;
import fr.vinted.republisher.fakes.InMemoryListingStatisticsRepository;
import fr.vinted.republisher.fakes.InMemoryPhotoStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static fr.vinted.republisher.domain.backup.BackupFixtures.aBackup;
import static fr.vinted.republisher.domain.backup.BackupFixtures.aContent;
import static fr.vinted.republisher.domain.backup.BackupFixtures.aPhoto;
import static fr.vinted.republisher.domain.backup.BackupFixtures.noBackupPause;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.ACTIVE;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.aListing;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.noPause;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.tuple;

/** US-20: the member relaunches a listing from the list, and it moves on by one stage. */
class RelaunchListingAcceptanceTest {

    private static final VintedListingId LISTING = new VintedListingId(1);
    private static final VintedListingId NEW_LISTING = new VintedListingId(2);
    private static final Instant SAVED_AT = Instant.parse("2026-11-10T10:00:00Z");
    private static final Instant VALIDATED_AT = Instant.parse("2026-11-10T11:00:00Z");
    private static final Instant NOW = Instant.parse("2026-11-11T10:00:00Z");
    private static final ListingContent ROBE_BLEUE = aContent(1, "Robe bleue", "Portée une fois", "12.00");
    private static final ScannedListing ON_VINTED = aListing(1, "Robe bleue", "12.00", ACTIVE);
    private static final ScannedListing REPUBLISHED = aListing(2, "Robe bleue", "12.00", ACTIVE);

    private final InMemoryListingRepository listings = new InMemoryListingRepository();
    private final InMemoryListingBackupRepository listingBackups = new InMemoryListingBackupRepository();
    private final InMemoryPhotoStorage photos = new InMemoryPhotoStorage();
    private final InMemoryBackupValidationRepository validations = new InMemoryBackupValidationRepository();
    private final InMemoryListingDeletionRepository deletions = new InMemoryListingDeletionRepository();
    private final InMemoryListingRepublicationRepository republications = new InMemoryListingRepublicationRepository();
    private final InMemoryActionJournal journal = new InMemoryActionJournal();
    private final InMemoryListingSaleRepository sales = new InMemoryListingSaleRepository();
    private final FakeListingDeleter deleter = new FakeListingDeleter();
    private final FakeWardrobeReader wardrobe = new FakeWardrobeReader();
    private final FakeListingPublisher publisher = new FakeListingPublisher(wardrobe);
    private final FakeListingContentReader contents = new FakeListingContentReader();
    private final VintedSession session = new VintedSession();
    private final AdjustableClock clock = new AdjustableClock(NOW);
    private final RepublicationDelay delay = new RepublicationDelay(Duration.ofMinutes(5));

    @Test
    void relaunching_a_listing_to_delete_deletes_it() {
        // Étant donné une annonce présente dont la sauvegarde actuelle est validée, en mode réel
        aValidatedListing();
        deleter.showing(ON_VINTED);
        // Quand le membre la relance en ayant vu « Prête à supprimer »
        RelaunchReport report = relaunch(ListingStage.TO_DELETE);
        // Alors l'annonce est supprimée sur Vinted, pour sa sauvegarde actuelle
        assertThat(deleter.confirmationClicks()).isEqualTo(1);
        assertThat(deletions.findLatest(LISTING)).map(ListingDeletion::outcome).contains(DeletionOutcome.CONFIRMED);
        assertThat(deletions.findLatest(LISTING)).map(ListingDeletion::backupSavedAt).contains(SAVED_AT);
        // Et la réponse donne l'action « suppression » et l'étape « En attente avant republication »
        assertThat(report.action()).isEqualTo(RelaunchAction.DELETION);
        assertThat(report.dryRun()).isFalse();
        assertThat(report.entry().stage()).isEqualTo(ListingStage.WAITING);
    }

    @Test
    void relaunching_a_deletion_to_check_rechecks_it() {
        // Étant donné une annonce dont la suppression est non confirmée
        ListingBackup backup = aValidatedListing();
        deletions.save(ListingDeletion.attempted(backup, NOW.minusSeconds(60)));
        // Et Vinted ne montre plus l'annonce
        deleter.showingNothing();
        // Quand le membre la relance en ayant vu « Suppression à vérifier »
        RelaunchReport report = relaunch(ListingStage.DELETION_TO_CHECK);
        // Alors la suppression est revérifiée et confirmée
        assertThat(deletions.findLatest(LISTING)).map(ListingDeletion::outcome).contains(DeletionOutcome.CONFIRMED);
        // Et la réponse donne l'action « revérification » et l'étape « En attente avant republication »
        assertThat(report.action()).isEqualTo(RelaunchAction.DELETION_RECHECK);
        assertThat(report.entry().stage()).isEqualTo(ListingStage.WAITING);
    }

    @Test
    void relaunching_a_listing_to_republish_republishes_it() {
        // Étant donné une annonce supprimée dont la date de republication est atteinte, en mode réel
        aDeletedListing(Duration.ofMinutes(10));
        publisher.showingOncePublished(REPUBLISHED);
        // Quand le membre la relance en ayant vu « À republier »
        RelaunchReport report = relaunch(ListingStage.TO_REPUBLISH);
        // Alors elle est republiée depuis sa sauvegarde actuelle
        assertThat(publisher.publishClicks()).isEqualTo(1);
        assertThat(publisher.filledContent()).contains(ROBE_BLEUE);
        // Et la réponse donne l'action « republication » et l'étape « Republiée »
        assertThat(report.action()).isEqualTo(RelaunchAction.REPUBLICATION);
        assertThat(report.entry().stage()).isEqualTo(ListingStage.REPUBLISHED);
    }

    @Test
    void relaunching_a_republication_to_check_verifies_it() {
        // Étant donné une annonce dont la dernière republication est en erreur
        ListingBackup backup = aDeletedListing(Duration.ofHours(1));
        republications.save(ListingRepublication.attempted(backup, NOW.minusSeconds(600)));
        // Et la nouvelle annonce est dans le dressing
        wardrobe.holding(REPUBLISHED);
        contents.showing(new ListingReading(sameContentAs(NEW_LISTING), List.of(aPhoto("front"), aPhoto("back"))));
        // Quand le membre la relance en ayant vu « Republication à vérifier »
        RelaunchReport report = relaunch(ListingStage.REPUBLICATION_TO_CHECK);
        // Alors la republication est vérifiée et trouvée
        assertThat(republications.findLatest(LISTING)).map(ListingRepublication::outcome)
                .contains(RepublicationOutcome.PUBLISHED);
        // Et la réponse donne l'action « vérification » et l'étape « Republiée »
        assertThat(report.action()).isEqualTo(RelaunchAction.REPUBLICATION_VERIFICATION);
        assertThat(report.entry().stage()).isEqualTo(ListingStage.REPUBLISHED);
        // US-44 AT-44-06. Et le journal contient « Vérification de la republication : réussie »
        assertThat(journal.records()).extracting(ActionRecord::action, ActionRecord::outcome)
                .containsExactly(tuple(JournaledAction.REPUBLICATION_VERIFICATION, ActionOutcome.SUCCEEDED));
    }

    @Test
    void dry_run_changes_nothing() {
        // Étant donné une annonce « Prête à supprimer », suppression en dry-run
        aValidatedListing();
        deleter.showing(ON_VINTED);
        // Quand le membre la relance
        RelaunchReport report = relaunchListing(DeletionMode.DRY_RUN).relaunch(LISTING, ListingStage.TO_DELETE);
        // Alors rien n'est supprimé ni enregistré
        assertThat(deleter.confirmationClicks()).isZero();
        assertThat(deletions.findAll()).isEmpty();
        // Et la réponse indique un dry-run, l'étape reste « Prête à supprimer »
        assertThat(report.dryRun()).isTrue();
        assertThat(report.entry().stage()).isEqualTo(ListingStage.TO_DELETE);
    }

    @Test
    void relaunch_is_refused_when_the_stage_changed() {
        // Étant donné une annonce « Suppression à vérifier »
        ListingBackup backup = aValidatedListing();
        deletions.save(ListingDeletion.attempted(backup, NOW.minusSeconds(60)));
        // Quand le membre la relance en ayant vu « Prête à supprimer »
        Throwable refusal = catchThrowable(() -> relaunch(ListingStage.TO_DELETE));
        // Alors la relance est refusée (STAGE_CHANGED, étape actuelle « Suppression à vérifier ») et rien n'est lancé
        assertThat(refusal).isInstanceOfSatisfying(StageChanged.class,
                changed -> assertThat(changed.current()).isEqualTo(ListingStage.DELETION_TO_CHECK));
        assertNothingLaunched();
    }

    @ParameterizedTest
    @EnumSource(value = ListingStage.class, names = {"TO_BACK_UP", "TO_VALIDATE", "GONE", "REPUBLISHED", "SOLD"})
    void stages_without_next_action_are_not_relaunchable(ListingStage stage) {
        // Étant donné une annonce « À sauvegarder », « À valider », « Absente de Vinted », « Republiée » ou
        // « Vendue » (AT-50-03)
        aListingAt(stage);
        // Quand le membre la relance en ayant vu cette étape
        Throwable refusal = catchThrowable(() -> relaunch(stage));
        // Alors la relance est refusée (NOT_RELAUNCHABLE) et rien n'est lancé
        assertThat(refusal).isInstanceOfSatisfying(NotRelaunchable.class, notRelaunchable -> {
            assertThat(notRelaunchable.stage()).isEqualTo(stage);
            assertThat(notRelaunchable.allowedAt()).isEmpty();
        });
        assertNothingLaunched();
    }

    @Test
    void waiting_listing_gives_its_republication_date() {
        // Étant donné une annonce supprimée à 10:00, délai de 5 minutes, il est 10:02
        aDeletedListing(Duration.ofMinutes(2));
        // Quand le membre la relance en ayant vu « En attente avant republication »
        Throwable refusal = catchThrowable(() -> relaunch(ListingStage.WAITING));
        // Alors la relance est refusée (NOT_RELAUNCHABLE, à partir de 10:05) et rien n'est lancé
        assertThat(refusal).isInstanceOfSatisfying(NotRelaunchable.class,
                notRelaunchable -> assertThat(notRelaunchable.allowedAt()).contains(NOW.plus(Duration.ofMinutes(3))));
        assertNothingLaunched();
    }

    @Test
    void cadence_limit_applies() {
        // Étant donné une annonce « Prête à supprimer » et une écriture sur Vinted il y a une minute
        aValidatedListing();
        deleter.showing(ON_VINTED);
        deletions.save(ListingDeletion.attempted(aBackup(9, SAVED_AT), NOW.minus(Duration.ofMinutes(1))));
        // Quand le membre la relance
        Throwable refusal = catchThrowable(() -> relaunch(ListingStage.TO_DELETE));
        // Alors la relance est refusée (CADENCE_LIMIT_REACHED, avec la date), rien n'est touché sur Vinted
        assertThat(refusal).isInstanceOfSatisfying(CadenceLimitReached.class,
                limit -> assertThat(limit.allowedAt()).isEqualTo(NOW.plus(Duration.ofMinutes(1))));
        assertThat(deleter.pagesOpened()).isZero();
        assertThat(deletions.findLatest(LISTING)).isEmpty();
        // Et le journal garde le refus de la suppression, comme depuis la fiche
        assertThat(journal.records()).extracting(ActionRecord::action, ActionRecord::outcome, ActionRecord::reason)
                .containsExactly(tuple(JournaledAction.DELETION, ActionOutcome.REFUSED,
                        Optional.of("CADENCE_LIMIT_REACHED")));
    }

    @Test
    void unknown_listing_is_not_found() {
        // Étant donné un identifiant inconnu de l'outil
        // Quand le membre la relance
        Throwable refusal = catchThrowable(() -> relaunch(ListingStage.TO_DELETE));
        // Alors elle est introuvable
        assertThat(refusal).isInstanceOf(ListingNotFound.class);
        assertNothingLaunched();
    }

    private RelaunchReport relaunch(ListingStage seen) {
        return relaunchListing(DeletionMode.REAL).relaunch(LISTING, seen);
    }

    private void assertNothingLaunched() {
        assertThat(deleter.pagesOpened()).isZero();
        assertThat(publisher.formsOpened()).isZero();
        assertThat(wardrobe.reads()).isZero();
        assertThat(journal.records()).isEmpty();
        assertThat(session.overview().currentAction()).isEmpty();
    }

    private RelaunchListing relaunchListing(DeletionMode deletionMode) {
        JournalWriter journalWriter = new JournalWriter(journal, clock);
        CadenceCheck cadence = new CadenceCheck(deletions, republications,
                new WriteCadence(Duration.ofMinutes(2), Duration.ofMinutes(2), 20, 20), clock);
        ShowListingRepublication showRepublication = new ShowListingRepublication(new ShowListingDeletion(listings,
                listingBackups, photos, validations, deletions, deletionMode, delay), republications, listings,
                RepublicationMode.REAL);
        NewListingSearch newListingSearch = new NewListingSearch(wardrobe, noPause(), listings);
        return new RelaunchListing(
                new ShowListingDetail(listings, new InMemoryListingStatisticsRepository(), listingBackups,
                        validations, deletions, republications, delay, clock, journal,
                        new InMemoryBackupVerificationRepository(), sales),
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
    }

    /** Present, backed up with two photos kept, and validated by the member. */
    private ListingBackup aValidatedListing() {
        listings.saveAll(List.of(Listing.seen(ON_VINTED, SAVED_AT)));
        ListingReading reading = new ListingReading(ROBE_BLEUE, List.of(aPhoto("front"), aPhoto("back")));
        reading.photos().forEach(photo -> photos.store(LISTING, photo));
        ListingBackup backup = ListingBackup.of(reading, SAVED_AT);
        listingBackups.save(backup);
        validations.save(BackupValidation.of(backup, VALIDATED_AT));
        return backup;
    }

    /** Validated, then deleted on Vinted with the deletion proven {@code ago}: absent from the wardrobe. */
    private ListingBackup aDeletedListing(Duration ago) {
        ListingBackup backup = aValidatedListing();
        listings.saveAll(List.of(Listing.seen(ON_VINTED, SAVED_AT).absent()));
        Instant provenAt = NOW.minus(ago);
        deletions.save(ListingDeletion.attempted(backup, provenAt.minusSeconds(5)).confirmed(provenAt));
        return backup;
    }

    private void aListingAt(ListingStage stage) {
        switch (stage) {
            case TO_BACK_UP -> listings.saveAll(List.of(Listing.seen(ON_VINTED, SAVED_AT)));
            case TO_VALIDATE -> {
                listings.saveAll(List.of(Listing.seen(ON_VINTED, SAVED_AT)));
                listingBackups.save(ListingBackup.of(new ListingReading(ROBE_BLEUE, List.of(aPhoto("front"))),
                        SAVED_AT));
            }
            case GONE -> listings.saveAll(List.of(Listing.seen(ON_VINTED, SAVED_AT).absent()));
            case REPUBLISHED -> {
                ListingBackup backup = aDeletedListing(Duration.ofHours(1));
                republications.save(ListingRepublication.attempted(backup, NOW.minusSeconds(600))
                        .published(NEW_LISTING, NOW.minusSeconds(595)));
            }
            case SOLD -> {
                aValidatedListing();
                sales.addAll(List.of(new ListingSale(LISTING, NOW.minusSeconds(60))));
            }
            default -> throw new IllegalArgumentException(stage.name());
        }
    }

    private static ListingContent sameContentAs(VintedListingId id) {
        return new ListingContent(id, ROBE_BLEUE.title(), ROBE_BLEUE.description(), ROBE_BLEUE.price(),
                ROBE_BLEUE.category(), ROBE_BLEUE.brand(), ROBE_BLEUE.condition(), ROBE_BLEUE.size(),
                ROBE_BLEUE.colors(), ROBE_BLEUE.materials(), ROBE_BLEUE.packageSize(), ROBE_BLEUE.details());
    }
}
