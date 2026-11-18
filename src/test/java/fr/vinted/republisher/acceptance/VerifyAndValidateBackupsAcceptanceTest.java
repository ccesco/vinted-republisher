package fr.vinted.republisher.acceptance;

import fr.vinted.republisher.application.backup.VerifyAndValidateBackups;
import fr.vinted.republisher.application.inventory.ShowListingInventory;
import fr.vinted.republisher.application.journal.JournalWriter;
import fr.vinted.republisher.domain.backup.BackupPace;
import fr.vinted.republisher.domain.backup.BackupValidation;
import fr.vinted.republisher.domain.backup.BackupVerificationProgress;
import fr.vinted.republisher.domain.backup.BackupVerificationStatus;
import fr.vinted.republisher.domain.backup.BackupVerificationStopReason;
import fr.vinted.republisher.domain.backup.DownloadedPhoto;
import fr.vinted.republisher.domain.backup.FormOption;
import fr.vinted.republisher.domain.backup.ListingBackup;
import fr.vinted.republisher.domain.backup.ListingContent;
import fr.vinted.republisher.domain.backup.ListingFieldDifference;
import fr.vinted.republisher.domain.backup.ListingFormField;
import fr.vinted.republisher.domain.backup.ListingReading;
import fr.vinted.republisher.domain.backup.VerificationFailure.FieldChanged;
import fr.vinted.republisher.domain.backup.VerificationFailure.ListingAbsent;
import fr.vinted.republisher.domain.backup.VerificationFailure.PhotoMissing;
import fr.vinted.republisher.domain.backup.VerificationResult;
import fr.vinted.republisher.domain.inventory.BackupFilter;
import fr.vinted.republisher.domain.inventory.InventoryCriteria;
import fr.vinted.republisher.domain.inventory.InventoryEntry;
import fr.vinted.republisher.domain.inventory.InventorySort;
import fr.vinted.republisher.domain.inventory.ListingStage;
import fr.vinted.republisher.domain.inventory.PresenceFilter;
import fr.vinted.republisher.domain.inventory.SortDirection;
import fr.vinted.republisher.domain.journal.ActionOutcome;
import fr.vinted.republisher.domain.journal.ActionRecord;
import fr.vinted.republisher.domain.journal.JournaledAction;
import fr.vinted.republisher.domain.republication.RepublicationDelay;
import fr.vinted.republisher.domain.session.BrowserAction;
import fr.vinted.republisher.domain.session.BrowserBusy;
import fr.vinted.republisher.domain.session.SessionStatus;
import fr.vinted.republisher.domain.session.VintedSession;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.PauseRange;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import fr.vinted.republisher.fakes.AdjustableClock;
import fr.vinted.republisher.fakes.FakeListingContentReader;
import fr.vinted.republisher.fakes.InMemoryActionJournal;
import fr.vinted.republisher.fakes.InMemoryBackupValidationRepository;
import fr.vinted.republisher.fakes.InMemoryBackupVerificationRepository;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static fr.vinted.republisher.domain.backup.BackupFixtures.aContent;
import static fr.vinted.republisher.domain.backup.BackupFixtures.aPhoto;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.anActiveListing;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class VerifyAndValidateBackupsAcceptanceTest {

    private static final Instant SAVED_AT = Instant.parse("2026-11-10T10:00:00Z");
    private static final Instant VALIDATED_AT = Instant.parse("2026-11-11T10:00:00Z");
    private static final Instant NOW = Instant.parse("2026-11-18T10:00:00Z");
    private static final Executor RIGHT_AWAY = Runnable::run;
    private static final InventoryCriteria EVERY_LISTING = new InventoryCriteria(PresenceFilter.ALL,
            BackupFilter.ALL, "", InventorySort.RECENCY, SortDirection.DESC);

    private final AdjustableClock clock = new AdjustableClock(NOW);
    private final VintedSession session = new VintedSession();
    private final InMemoryListingRepository listings = new InMemoryListingRepository();
    private final InMemoryListingBackupRepository listingBackups = new InMemoryListingBackupRepository();
    private final InMemoryPhotoStorage photos = new InMemoryPhotoStorage();
    private final InMemoryBackupValidationRepository validations = new InMemoryBackupValidationRepository();
    private final InMemoryBackupVerificationRepository verifications =
            new InMemoryBackupVerificationRepository(validations);
    private final FakeListingContentReader reader = new FakeListingContentReader();
    private final InMemoryActionJournal journal = new InMemoryActionJournal();
    private final AtomicBoolean automaticRunInProgress = new AtomicBoolean();
    private final ShowListingInventory inventory = new ShowListingInventory(listings,
            new InMemoryListingStatisticsRepository(), listingBackups, validations,
            new InMemoryListingDeletionRepository(), new InMemoryListingRepublicationRepository(),
            new RepublicationDelay(Duration.ofMinutes(5)), clock, new InMemoryListingTagRepository(), verifications);
    private final BackupPace pace = new BackupPace(
            new PauseRange(Duration.ofSeconds(5), Duration.ofSeconds(10)), new Random(42));
    private final VerifyAndValidateBackups verifyAndValidate = new VerifyAndValidateBackups(session,
            automaticRunInProgress::get, inventory, reader, listingBackups, photos, validations, verifications, pace,
            clock, RIGHT_AWAY, new JournalWriter(journal, clock));

    @Test
    void identical_backups_are_validated() {
        // Étant donné les annonces 101 et 102, présentes, sauvegardées, non validées, aux photos intactes
        aKeptBackupOf(101);
        aKeptBackupOf(102);
        // Et Vinted affiche pour chacune exactement le contenu sauvegardé
        reader.showing(aContent(101), aContent(102));
        // Quand je lance « Vérifier et valider »
        verifyAndValidate.launch();
        // Alors 101 et 102 sont validées et passent à « prête à supprimer »
        assertThat(validations.findAll()).extracting(BackupValidation::listingId)
                .containsExactly(new VintedListingId(101), new VintedListingId(102));
        assertThat(stages()).containsEntry(101L, ListingStage.TO_DELETE).containsEntry(102L, ListingStage.TO_DELETE);
        // Et la ligne d'état affiche « Vérification terminée : 2 validées, 0 en erreur »
        assertThat(verifyAndValidate.progress()).isEqualTo(
                new BackupVerificationProgress(BackupVerificationStatus.FINISHED, 2, 2, 2, 0, Optional.empty()));
        assertThat(session.overview().currentAction()).isEmpty();
    }

    @Test
    void changed_field_fails_the_verification() {
        // Étant donné l'annonce 101 sauvegardée avec la taille « M » et la description « Robe bleue »
        aKeptBackupOf(101);
        // Et Vinted affiche maintenant la taille « L » et la description « Robe bleue, portée une fois »
        ListingContent saved = aContent(101);
        reader.showing(new ListingContent(saved.id(), saved.title(), "Robe bleue, portée une fois", saved.price(),
                saved.category(), saved.brand(), saved.condition(),
                Optional.of(new FormOption(589, "L")), saved.colors(),
                saved.materials(), saved.packageSize(), saved.details()));
        // Quand je lance « Vérifier et valider »
        verifyAndValidate.launch();
        // Alors 101 n'est pas validée et passe à « vérification en erreur »
        assertThat(validations.findAll()).isEmpty();
        assertThat(stages()).containsEntry(101L, ListingStage.VERIFICATION_FAILED);
        // Et ses motifs indiquent la taille et la description, avec la valeur sauvegardée et la valeur lue
        assertThat(verifications.find(new VintedListingId(101)).orElseThrow().failures()).containsExactly(
                new FieldChanged(new ListingFieldDifference(ListingFormField.DESCRIPTION, "Porté deux fois",
                        "Robe bleue, portée une fois")),
                new FieldChanged(new ListingFieldDifference(ListingFormField.SIZE, "588", "L (589)")));
        assertThat(verifyAndValidate.progress()).isEqualTo(
                new BackupVerificationProgress(BackupVerificationStatus.FINISHED, 1, 1, 0, 1, Optional.empty()));
    }

    @Test
    void validated_listing_changed_on_vinted_loses_its_validation() {
        // Étant donné l'annonce 103 dont la sauvegarde est validée
        ListingBackup backup = aKeptBackupOf(103);
        validations.save(BackupValidation.of(backup, VALIDATED_AT));
        // Et dont le prix a changé sur Vinted
        reader.showing(aContent(103, "Pull rayé 103", "Porté deux fois", "9.00"));
        // Quand je lance « Vérifier et valider »
        verifyAndValidate.launch();
        // Alors sa validation est annulée et elle passe à « vérification en erreur »
        assertThat(validations.find(new VintedListingId(103))).isEmpty();
        assertThat(stages()).containsEntry(103L, ListingStage.VERIFICATION_FAILED);
        // Et la série « Tout lancer » ne la prend pas : elle ne prend une annonce en ligne qu'à TO_DELETE
        assertThat(stages().get(103L)).isNotEqualTo(ListingStage.TO_DELETE);
    }

    @Test
    void unchanged_validated_listing_keeps_its_validation() {
        // Étant donné l'annonce 103 validée le 11/11 à 10 h, inchangée sur Vinted
        ListingBackup backup = aKeptBackupOf(103);
        validations.save(BackupValidation.of(backup, VALIDATED_AT));
        reader.showing(aContent(103));
        // Quand je lance « Vérifier et valider »
        verifyAndValidate.launch();
        // Alors elle reste validée le 11/11 à 10 h
        assertThat(validations.find(new VintedListingId(103))).contains(BackupValidation.of(backup, VALIDATED_AT));
        assertThat(verifications.find(new VintedListingId(103))).contains(
                new VerificationResult(new VintedListingId(103), SAVED_AT, NOW, List.of()));
    }

    @Test
    void missing_photo_fails_the_verification() {
        // Étant donné l'annonce 101 dont le fichier de la photo de rang 1 a disparu
        ListingBackup backup = aKeptBackupOf(101, aPhoto("front"), aPhoto("back"));
        photos.lose(101, backup.photos().get(1));
        // Et Vinted affiche exactement le contenu sauvegardé
        reader.showing(aContent(101));
        // Quand je lance « Vérifier et valider »
        verifyAndValidate.launch();
        // Alors 101 passe à « vérification en erreur » avec « photo 1 manquante »
        assertThat(stages()).containsEntry(101L, ListingStage.VERIFICATION_FAILED);
        assertThat(verifications.find(new VintedListingId(101)).orElseThrow().failures())
                .containsExactly(new PhotoMissing(1));
    }

    @Test
    void listing_gone_from_vinted_fails_and_the_verification_goes_on() {
        // Étant donné les annonces 101 et 102 à valider
        aKeptBackupOf(101);
        aKeptBackupOf(102);
        reader.showing(aContent(102));
        // Et le formulaire de 101 répond 404
        reader.whereFormIsGone(101);
        // Quand je lance « Vérifier et valider »
        verifyAndValidate.launch();
        // Alors 101 passe à « vérification en erreur » avec « annonce absente »
        assertThat(verifications.find(new VintedListingId(101)).orElseThrow().failures())
                .containsExactly(new ListingAbsent());
        assertThat(stages()).containsEntry(101L, ListingStage.VERIFICATION_FAILED);
        // Et 102 est vérifiée ensuite
        assertThat(stages()).containsEntry(102L, ListingStage.TO_DELETE);
        assertThat(reader.opened()).containsExactly(new VintedListingId(101), new VintedListingId(102));
    }

    @Test
    void expired_session_stops_and_keeps_the_results() {
        // Étant donné les annonces 101, 102 et 103 à valider
        aKeptBackupOf(101);
        aKeptBackupOf(102);
        aKeptBackupOf(103);
        reader.showing(aContent(101), aContent(102), aContent(103));
        // Et la session Vinted expire à la lecture de 102
        reader.whereFormRedirectsToLogin(102);
        // Quand je lance « Vérifier et valider »
        verifyAndValidate.launch();
        // Alors 101 est validée
        assertThat(stages()).containsEntry(101L, ListingStage.TO_DELETE);
        // Et la vérification s'arrête avec « Vérification arrêtée : session Vinted expirée »
        assertThat(verifyAndValidate.progress()).isEqualTo(new BackupVerificationProgress(
                BackupVerificationStatus.STOPPED, 1, 3, 1, 0,
                Optional.of(BackupVerificationStopReason.SESSION_DISCONNECTED)));
        assertThat(session.overview().status()).contains(SessionStatus.DISCONNECTED);
        assertThat(session.overview().currentAction()).isEmpty();
        // Et 102 et 103 restent à « à valider »
        assertThat(stages()).containsEntry(102L, ListingStage.TO_VALIDATE).containsEntry(103L, ListingStage.TO_VALIDATE);
    }

    @Test
    void unreachable_vinted_or_unreadable_form_stops_the_verification() {
        aKeptBackupOf(101);
        aKeptBackupOf(102);
        reader.showing(aContent(101), aContent(102)).whereVintedRefuses(101);
        verifyAndValidate.launch();
        assertThat(verifyAndValidate.progress().stopReason()).contains(BackupVerificationStopReason.VINTED_UNREACHABLE);
        assertThat(reader.opened()).containsExactly(new VintedListingId(101));

        reader.afresh().whereFormIsUnreadable(101, "unknown attribute code");
        verifyAndValidate.launch();
        assertThat(verifyAndValidate.progress().stopReason()).contains(BackupVerificationStopReason.LISTING_UNREADABLE);
        assertThat(session.overview().currentAction()).isEmpty();
    }

    @Test
    void new_backup_brings_the_listing_back_to_validation() {
        // Étant donné l'annonce 101 en « vérification en erreur »
        aKeptBackupOf(101);
        reader.whereFormIsGone(101);
        verifyAndValidate.launch();
        assertThat(stages()).containsEntry(101L, ListingStage.VERIFICATION_FAILED);
        // Quand une nouvelle sauvegarde de 101 est enregistrée
        listingBackups.save(ListingBackup.of(new ListingReading(aContent(101), List.of(aPhoto("of 101"))),
                NOW.plusSeconds(60)));
        // Alors 101 revient à « à valider »
        assertThat(stages()).containsEntry(101L, ListingStage.TO_VALIDATE);
    }

    @Test
    void listings_in_error_are_verified_again_and_may_pass() {
        aKeptBackupOf(101);
        reader.whereFormIsGone(101);
        verifyAndValidate.launch();
        clock.advance(Duration.ofHours(1));

        reader.afresh().showing(aContent(101));
        verifyAndValidate.launch();

        assertThat(stages()).containsEntry(101L, ListingStage.TO_DELETE);
    }

    @Test
    void only_present_listings_to_validate_in_error_or_to_delete_are_read_by_increasing_identifier() {
        aKeptBackupOf(103);
        aKeptBackupOf(101);
        listings.saveAll(List.of(Listing.seen(anActiveListing(102), SAVED_AT)));
        reader.showing(aContent(101), aContent(103));

        verifyAndValidate.launch();

        assertThat(reader.opened()).containsExactly(new VintedListingId(101), new VintedListingId(103));
    }

    @Test
    void pauses_between_two_listings() {
        // Étant donné trois annonces à valider
        aKeptBackupOf(101);
        aKeptBackupOf(102);
        aKeptBackupOf(103);
        reader.showing(aContent(101), aContent(102), aContent(103));
        // Quand je lance « Vérifier et valider »
        verifyAndValidate.launch();
        // Alors chaque formulaire est ouvert une fois, avec une pause de 5 à 10 s entre deux
        assertThat(reader.opened()).hasSize(3).doesNotHaveDuplicates();
        assertThat(reader.pausesBetweenListings()).hasSize(2)
                .allSatisfy(pause -> assertThat(pause).isBetween(Duration.ofSeconds(5), Duration.ofSeconds(10)));
        assertThat(reader.tabs()).isEqualTo(1);
    }

    @Test
    void nothing_to_verify_finishes_at_once() {
        verifyAndValidate.launch();

        assertThat(verifyAndValidate.progress()).isEqualTo(
                new BackupVerificationProgress(BackupVerificationStatus.FINISHED, 0, 0, 0, 0, Optional.empty()));
        assertThat(reader.tabs()).isZero();
    }

    @Test
    void refused_while_another_browser_action_or_a_run_is_in_progress() {
        aKeptBackupOf(101);
        session.start(BrowserAction.SCANNING);

        assertThatThrownBy(verifyAndValidate::launch).isInstanceOf(BrowserBusy.class);
        session.finish();
        automaticRunInProgress.set(true);
        assertThatThrownBy(verifyAndValidate::launch).isInstanceOf(BrowserBusy.class);

        assertThat(reader.opened()).isEmpty();
        assertThat(verifyAndValidate.progress().status()).isEqualTo(BackupVerificationStatus.IDLE);
        assertThat(session.overview().currentAction()).isEmpty();
    }

    @Test
    void the_browser_is_held_during_the_verification() {
        aKeptBackupOf(101);
        reader.showing(aContent(101)).during(() -> {
            assertThat(session.overview().currentAction()).contains(BrowserAction.VERIFYING_BACKUPS);
            assertThat(verifyAndValidate.progress().status()).isEqualTo(BackupVerificationStatus.RUNNING);
            assertThat(verifyAndValidate.progress().totalCount()).isEqualTo(1);
        });

        verifyAndValidate.launch();

        assertThat(session.overview().currentAction()).isEmpty();
    }

    @Test
    void each_verification_is_journaled_with_its_outcome() {
        aKeptBackupOf(101);
        aKeptBackupOf(102);
        reader.showing(aContent(101), aContent(102, "Pull rayé 102", "Porté une fois"));

        verifyAndValidate.launch();

        assertThat(journal.records()).extracting(ActionRecord::action, ActionRecord::outcome,
                        entry -> entry.listingId().map(VintedListingId::value), ActionRecord::reason)
                .containsExactly(
                        tuple(JournaledAction.BACKUP_VERIFICATION, ActionOutcome.SUCCEEDED, Optional.of(101L),
                                Optional.empty()),
                        tuple(JournaledAction.BACKUP_VERIFICATION, ActionOutcome.FAILED, Optional.of(102L),
                                Optional.of("FIELD_CHANGED:DESCRIPTION")));
    }

    private ListingBackup aKeptBackupOf(long id) {
        return aKeptBackupOf(id, aPhoto("of " + id));
    }

    private ListingBackup aKeptBackupOf(long id, DownloadedPhoto... kept) {
        listings.saveAll(List.of(Listing.seen(anActiveListing(id), SAVED_AT)));
        ListingReading reading = new ListingReading(aContent(id), List.of(kept));
        reading.photos().forEach(photo -> photos.store(reading.id(), photo));
        ListingBackup backup = ListingBackup.of(reading, SAVED_AT);
        listingBackups.save(backup);
        return backup;
    }

    private Map<Long, ListingStage> stages() {
        return inventory.show(EVERY_LISTING).stream().collect(Collectors.toMap(
                entry -> entry.listing().id().value(), InventoryEntry::stage));
    }
}
