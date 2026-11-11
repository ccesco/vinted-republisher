package fr.vinted.republisher.acceptance;

import fr.vinted.republisher.application.backup.ShowListingBackup;
import fr.vinted.republisher.application.deletion.DeleteListing;
import fr.vinted.republisher.application.deletion.RecheckListingDeletion;
import fr.vinted.republisher.application.deletion.ShowListingDeletion;
import fr.vinted.republisher.domain.backup.BackupNotVerified;
import fr.vinted.republisher.domain.backup.BackupReplaced;
import fr.vinted.republisher.domain.backup.BackupValidation;
import fr.vinted.republisher.domain.backup.VerificationFailure.PhotoMissing;
import fr.vinted.republisher.domain.deletion.BackupNotValidated;
import fr.vinted.republisher.domain.deletion.ListingAlreadyDeleted;
import fr.vinted.republisher.domain.deletion.ListingChanged;
import fr.vinted.republisher.domain.deletion.ListingDifference;
import fr.vinted.republisher.domain.deletion.DeletionUnconfirmed;
import fr.vinted.republisher.domain.deletion.ListingPageUnreadable;
import fr.vinted.republisher.domain.deletion.NoUnconfirmedDeletion;
import fr.vinted.republisher.domain.session.BrowserAction;
import fr.vinted.republisher.domain.session.BrowserBusy;
import fr.vinted.republisher.domain.session.BrowserUnavailable;
import fr.vinted.republisher.domain.session.SessionDisconnected;
import fr.vinted.republisher.domain.session.VintedUnreachable;
import fr.vinted.republisher.domain.wardrobe.Price;
import fr.vinted.republisher.domain.backup.ListingBackup;
import fr.vinted.republisher.domain.backup.ListingReading;
import fr.vinted.republisher.domain.deletion.DeletionMode;
import fr.vinted.republisher.domain.deletion.DeletionOutcome;
import fr.vinted.republisher.domain.deletion.DeletionReport;
import fr.vinted.republisher.domain.deletion.ListingDeletion;
import fr.vinted.republisher.domain.session.VintedSession;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import fr.vinted.republisher.fakes.AdjustableClock;
import fr.vinted.republisher.fakes.FakeListingDeleter;
import fr.vinted.republisher.fakes.InMemoryBackupValidationRepository;
import fr.vinted.republisher.fakes.InMemoryListingBackupRepository;
import fr.vinted.republisher.fakes.InMemoryListingDeletionRepository;
import fr.vinted.republisher.fakes.InMemoryListingRepository;
import fr.vinted.republisher.fakes.InMemoryPhotoStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static fr.vinted.republisher.domain.backup.BackupFixtures.aReading;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.ACTIVE;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.aListing;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.anActiveListing;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.anInactiveListing;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class DeleteListingAcceptanceTest {

    private static final VintedListingId LISTING = new VintedListingId(1);
    private static final Instant SCANNED_AT = Instant.parse("2026-11-10T09:00:00Z");
    private static final Instant SAVED_AT = Instant.parse("2026-11-10T10:00:00Z");
    private static final Instant VALIDATED_AT = Instant.parse("2026-11-10T11:00:00Z");
    private static final Instant NOW = Instant.parse("2026-11-11T10:00:00Z");

    private final InMemoryListingRepository listings = new InMemoryListingRepository();
    private final InMemoryListingBackupRepository listingBackups = new InMemoryListingBackupRepository();
    private final InMemoryPhotoStorage photos = new InMemoryPhotoStorage();
    private final InMemoryBackupValidationRepository validations = new InMemoryBackupValidationRepository();
    private final InMemoryListingDeletionRepository deletions = new InMemoryListingDeletionRepository();
    private final FakeListingDeleter vinted = new FakeListingDeleter();
    private final VintedSession session = new VintedSession();
    private final AdjustableClock clock = new AdjustableClock(NOW);
    private final ShowListingBackup showListingBackup =
            new ShowListingBackup(listings, listingBackups, photos, validations);

    @Test
    void deletes_a_listing_whose_backup_is_validated() {
        // Étant donné une annonce présente, active sur Vinted, dont la sauvegarde actuelle est validée et vérifiée
        // Et le dry-run désactivé
        ListingBackup backup = aValidatedListing();
        vinted.showing(anActiveListing(1));
        // Quand je confirme « Supprimer définitivement » sur la version affichée
        DeletionReport report = deleteListing(DeletionMode.REAL).delete(LISTING, SAVED_AT);
        // Alors l'outil relit l'annonce, clique sur la suppression puis confirme dans Vinted
        // Et Vinted ne montre plus l'annonce
        // Et la suppression est confirmée, avec sa date et la version de la sauvegarde
        // Et l'annonce est absente
        // Et sa sauvegarde, ses photos et sa validation sont intactes
        assertThat(vinted.confirmationClicks()).isEqualTo(1);
        ListingDeletion deletion = report.lastDeletion().orElseThrow();
        assertThat(deletion.outcome()).isEqualTo(DeletionOutcome.CONFIRMED);
        assertThat(deletion.attemptedAt()).isEqualTo(NOW);
        assertThat(deletion.backupSavedAt()).isEqualTo(SAVED_AT);
        assertThat(deletions.findAll()).containsExactly(deletion);
        assertThat(listings.find(LISTING)).map(Listing::present).contains(false);
        assertThat(listingBackups.find(LISTING)).contains(backup);
        assertThat(photos.files(1)).hasSize(1);
        assertThat(report.backup().validatedAt()).contains(VALIDATED_AT);
        assertThat(session.overview().currentAction()).isEmpty();
    }

    @Test
    void refuses_a_listing_whose_backup_is_not_validated() {
        // Étant donné une annonce dont la sauvegarde actuelle n'est pas validée
        aValidatedListing();
        validations.save(new BackupValidation(LISTING, SAVED_AT.minusSeconds(3600), VALIDATED_AT));
        vinted.showing(anActiveListing(1));
        // Quand je demande sa suppression
        Throwable refusal = catchThrowable(() -> deleteListing(DeletionMode.REAL).delete(LISTING, SAVED_AT));
        // Alors elle est refusée (« sauvegarde non validée »)
        // Et Chrome n'est pas ouvert
        assertThat(refusal).isInstanceOf(BackupNotValidated.class);
        assertNothingHappened();
    }

    @Test
    void refuses_a_deletion_from_a_stale_preview() {
        // Étant donné l'aperçu ouvert sur la sauvegarde validée du 10/11
        // Et une nouvelle sauvegarde de l'annonce enregistrée le 11/11 depuis
        aValidatedListing();
        listingBackups.save(ListingBackup.of(aReading(1), NOW));
        vinted.showing(anActiveListing(1));
        // Quand je confirme la suppression
        Throwable refusal = catchThrowable(() -> deleteListing(DeletionMode.REAL).delete(LISTING, SAVED_AT));
        // Alors elle est refusée (« sauvegarde remplacée ») et Chrome n'est pas ouvert
        assertThat(refusal).isInstanceOf(BackupReplaced.class);
        assertNothingHappened();
    }

    @Test
    void refuses_a_listing_whose_verification_fails() {
        // Étant donné une annonce validée dont un fichier photo a disparu depuis
        ListingBackup backup = aValidatedListing();
        photos.lose(1, backup.photos().getFirst());
        vinted.showing(anActiveListing(1));
        // Quand je demande sa suppression
        Throwable refusal = catchThrowable(() -> deleteListing(DeletionMode.REAL).delete(LISTING, SAVED_AT));
        // Alors elle est refusée avec l'échec de vérification « photo N manquante »
        // Et Chrome n'est pas ouvert
        assertThat(refusal).isInstanceOfSatisfying(BackupNotVerified.class, notVerified ->
                assertThat(notVerified.verification().failures()).containsExactly(new PhotoMissing(0)));
        assertNothingHappened();
    }

    @Test
    void refuses_a_listing_changed_on_vinted() {
        // Étant donné une annonce validée sauvegardée au prix 12,00 €
        // Et Vinted l'affiche maintenant à 10,00 €
        aValidatedListing();
        vinted.showing(aListing(1, "Pull rayé 1", "10.00", ACTIVE));
        // Quand je demande sa suppression
        Throwable refusal = catchThrowable(() -> deleteListing(DeletionMode.REAL).delete(LISTING, SAVED_AT));
        // Alors elle est refusée avec « prix changé : 12,00 € sauvegardé, 10,00 € lu »
        // Et aucun clic n'a été fait sur Vinted
        assertThat(refusal).isInstanceOfSatisfying(ListingChanged.class, changed ->
                assertThat(changed.differences()).containsExactly(new ListingDifference.PriceChanged(
                        new Price(new BigDecimal("12.50"), "EUR"), new Price(new BigDecimal("10.00"), "EUR"))));
        assertNoClickAndNoDeletion();
    }

    @Test
    void refuses_a_listing_no_longer_active() {
        // Étant donné une annonce validée que Vinted ne montre plus comme active
        aValidatedListing();
        vinted.showing(anInactiveListing(1));
        // Quand je demande sa suppression
        Throwable refusal = catchThrowable(() -> deleteListing(DeletionMode.REAL).delete(LISTING, SAVED_AT));
        // Alors elle est refusée avec « annonce plus active sur Vinted »
        // Et aucun clic n'a été fait sur Vinted
        assertThat(refusal).isInstanceOfSatisfying(ListingChanged.class, changed ->
                assertThat(changed.differences()).containsExactly(new ListingDifference.NotActive()));
        assertNoClickAndNoDeletion();
    }

    @Test
    void refuses_a_listing_already_deleted() {
        // Étant donné une annonce dont la suppression est confirmée
        aValidatedListing();
        vinted.showing(anActiveListing(1));
        deleteListing(DeletionMode.REAL).delete(LISTING, SAVED_AT);
        int pagesOpened = vinted.pagesOpened();
        // Quand je demande de nouveau sa suppression
        Throwable refusal = catchThrowable(() -> deleteListing(DeletionMode.REAL).delete(LISTING, SAVED_AT));
        // Alors elle est refusée (« déjà supprimée ») et Chrome n'est pas ouvert
        assertThat(refusal).isInstanceOf(ListingAlreadyDeleted.class);
        assertThat(vinted.pagesOpened()).isEqualTo(pagesOpened);
    }

    @Test
    void refuses_while_another_browser_action_runs() {
        // Étant donné un scan en cours
        aValidatedListing();
        vinted.showing(anActiveListing(1));
        session.start(BrowserAction.SCANNING);
        // Quand je demande la suppression d'une annonce validée
        Throwable refusal = catchThrowable(() -> deleteListing(DeletionMode.REAL).delete(LISTING, SAVED_AT));
        // Alors elle est refusée (« une action est en cours ») et le scan continue
        assertThat(refusal).isInstanceOf(BrowserBusy.class);
        assertThat(session.overview().currentAction()).contains(BrowserAction.SCANNING);
        assertThat(vinted.pagesOpened()).isZero();
    }

    static Stream<Arguments> failures_before_the_confirmation_click() {
        return Stream.of(
                Arguments.of("la session n'est pas connectée",
                        (Consumer<DeleteListingAcceptanceTest>) test -> test.vinted.whereSessionIsDisconnected(),
                        SessionDisconnected.class),
                Arguments.of("Chrome ne répond pas",
                        (Consumer<DeleteListingAcceptanceTest>) test -> test.vinted.whereBrowserCannotStart(),
                        BrowserUnavailable.class),
                Arguments.of("Vinted répond par une erreur HTTP",
                        (Consumer<DeleteListingAcceptanceTest>) test -> test.vinted.whereVintedRefuses(),
                        VintedUnreachable.class),
                Arguments.of("le bouton de suppression est introuvable",
                        (Consumer<DeleteListingAcceptanceTest>) test -> test.vinted.whereTheDeleteButtonIsMissing(),
                        ListingPageUnreadable.class),
                Arguments.of("la confirmation de Vinted n'apparaît pas",
                        (Consumer<DeleteListingAcceptanceTest>) test -> test.vinted.whereTheConfirmationNeverShows(),
                        ListingPageUnreadable.class),
                Arguments.of("la base est indisponible",
                        (Consumer<DeleteListingAcceptanceTest>) test -> test.deletions.whereDatabaseIsDown(),
                        IllegalStateException.class));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("failures_before_the_confirmation_click")
    void failure_before_the_confirmation_click_touches_nothing(String situation,
                                                                Consumer<DeleteListingAcceptanceTest> failure,
                                                                Class<? extends RuntimeException> problem) {
        // Étant donné une annonce validée
        // Et <cas>
        aValidatedListing();
        vinted.showing(anActiveListing(1));
        failure.accept(this);
        // Quand je demande sa suppression
        Throwable refusal = catchThrowable(() -> deleteListing(DeletionMode.REAL).delete(LISTING, SAVED_AT));
        // Alors elle est refusée avec <problème>
        // Et aucun clic n'a été fait sur Vinted, aucune suppression n'est enregistrée
        // Et le verrou est libéré
        assertThat(refusal).isInstanceOf(problem);
        assertNoClickAndNoDeletion();
    }

    @Test
    void listing_still_shown_leaves_the_deletion_unconfirmed() {
        // Étant donné une annonce validée
        // Et Vinted la montre encore après le clic de confirmation
        aValidatedListing();
        vinted.showing(anActiveListing(1)).whereTheListingStaysOnceConfirmed();
        // Quand je demande sa suppression
        DeletionReport report = deleteListing(DeletionMode.REAL).delete(LISTING, SAVED_AT);
        // Alors aucun nouvel essai n'est fait
        // Et la suppression est enregistrée « non confirmée »
        // Et l'aperçu affiche « suppression non confirmée, vérifier sur Vinted »
        // Et une nouvelle demande de suppression est refusée
        assertThat(vinted.confirmationClicks()).isEqualTo(1);
        assertThat(report.lastDeletion()).map(ListingDeletion::outcome).contains(DeletionOutcome.UNCONFIRMED);
        assertThat(listings.find(LISTING)).map(Listing::present).contains(true);
        assertThat(catchThrowable(() -> deleteListing(DeletionMode.REAL).delete(LISTING, SAVED_AT)))
                .isInstanceOf(DeletionUnconfirmed.class);
        assertThat(vinted.confirmationClicks()).isEqualTo(1);
    }

    @Test
    void failure_after_the_confirmation_click_leaves_the_deletion_unconfirmed() {
        // Étant donné une annonce validée
        // Et Chrome cesse de répondre juste après le clic de confirmation dans Vinted
        aValidatedListing();
        vinted.showing(anActiveListing(1)).whereBrowserStopsAfterTheConfirmation();
        // Quand je demande sa suppression
        DeletionReport report = deleteListing(DeletionMode.REAL).delete(LISTING, SAVED_AT);
        // Alors la suppression est enregistrée « non confirmée », sans nouvel essai
        // Et le verrou est libéré
        assertThat(report.lastDeletion()).map(ListingDeletion::outcome).contains(DeletionOutcome.UNCONFIRMED);
        assertThat(vinted.confirmationClicks()).isEqualTo(1);
        assertThat(session.overview().currentAction()).isEmpty();
    }

    @Test
    void database_down_when_saving_the_proof_leaves_the_deletion_unconfirmed() {
        aValidatedListing();
        vinted.showing(anActiveListing(1));
        deletions.whereDatabaseGoesDownAfter(1);

        DeletionReport report = deleteListing(DeletionMode.REAL).delete(LISTING, SAVED_AT);

        assertThat(vinted.confirmationClicks()).isEqualTo(1);
        assertThat(report.lastDeletion()).map(ListingDeletion::outcome).contains(DeletionOutcome.UNCONFIRMED);
        assertThat(listings.find(LISTING)).map(Listing::present).contains(true);
        assertThat(session.overview().currentAction()).isEmpty();
    }

    @Test
    void dry_run_checks_everything_and_deletes_nothing() {
        // Étant donné le dry-run actif
        // Et une annonce présente, active sur Vinted, dont la sauvegarde actuelle est validée et vérifiée
        aValidatedListing();
        vinted.showing(anActiveListing(1));
        // Quand je confirme « Supprimer définitivement »
        DeletionReport report = deleteListing(DeletionMode.DRY_RUN).delete(LISTING, SAVED_AT);
        // Alors l'outil relit l'annonce et trouve le bouton de suppression
        // Et aucun clic de suppression n'est fait
        // Et la réponse indique « dry-run réussi, rien supprimé »
        // Et aucune suppression n'est enregistrée, l'annonce reste présente et validée
        assertThat(vinted.pagesOpened()).isEqualTo(1);
        assertThat(report.dryRunPassed()).isTrue();
        assertThat(report.mode()).isEqualTo(DeletionMode.DRY_RUN);
        assertThat(report.lastDeletion()).isEmpty();
        assertThat(report.backup().validatedAt()).contains(VALIDATED_AT);
        assertNoClickAndNoDeletion();
    }

    @Test
    void dry_run_refuses_like_a_real_deletion() {
        // Étant donné le dry-run actif
        // Et une annonce validée dont le prix a changé sur Vinted
        aValidatedListing();
        vinted.showing(aListing(1, "Pull rayé 1", "10.00", ACTIVE));
        // Quand je confirme « Supprimer définitivement »
        Throwable refusal = catchThrowable(() -> deleteListing(DeletionMode.DRY_RUN).delete(LISTING, SAVED_AT));
        // Alors la demande est refusée avec « prix changé », comme une vraie suppression
        // Et aucune suppression n'est enregistrée
        assertThat(refusal).isInstanceOf(ListingChanged.class);
        assertNoClickAndNoDeletion();
    }

    @Test
    void recheck_confirms_a_deletion_vinted_no_longer_shows() {
        // Étant donné une suppression non confirmée
        // Et Vinted ne montre plus l'annonce
        anUnconfirmedDeletion();
        vinted.showingNothing();
        // Quand je clique sur « Revérifier sur Vinted »
        DeletionReport report = recheckListingDeletion().recheck(LISTING);
        // Alors aucun clic n'est fait sur la page de l'annonce
        // Et la suppression est confirmée et l'annonce est absente
        assertThat(vinted.confirmationClicks()).isEqualTo(1);
        assertThat(report.lastDeletion()).map(ListingDeletion::outcome).contains(DeletionOutcome.CONFIRMED);
        assertThat(report.lastDeletion()).flatMap(ListingDeletion::settledAt).contains(NOW);
        assertThat(listings.find(LISTING)).map(Listing::present).contains(false);
        assertThat(session.overview().currentAction()).isEmpty();
    }

    @Test
    void recheck_finds_the_listing_still_there() {
        // Étant donné une suppression non confirmée
        // Et Vinted montre toujours l'annonce, active
        anUnconfirmedDeletion();
        // Quand je clique sur « Revérifier sur Vinted »
        DeletionReport report = recheckListingDeletion().recheck(LISTING);
        // Alors la suppression est enregistrée « non faite »
        // Et une nouvelle demande de suppression redevient possible, avec tous les contrôles
        assertThat(report.lastDeletion()).map(ListingDeletion::outcome).contains(DeletionOutcome.NOT_DONE);
        vinted.whereTheListingGoesOnceConfirmed();
        clock.advance(Duration.ofMinutes(1));
        assertThat(deleteListing(DeletionMode.REAL).delete(LISTING, SAVED_AT).lastDeletion())
                .map(ListingDeletion::outcome).contains(DeletionOutcome.CONFIRMED);
    }

    @Test
    void recheck_without_answer_keeps_the_doubt() {
        // Étant donné une suppression non confirmée
        // Et Vinted est inaccessible
        anUnconfirmedDeletion();
        vinted.whereVintedRefuses();
        // Quand je clique sur « Revérifier sur Vinted »
        Throwable problem = catchThrowable(() -> recheckListingDeletion().recheck(LISTING));
        // Alors la suppression reste non confirmée et le problème est affiché
        assertThat(problem).isInstanceOf(VintedUnreachable.class);
        assertThat(deletions.findLatest(LISTING)).map(ListingDeletion::outcome)
                .contains(DeletionOutcome.UNCONFIRMED);
        assertThat(session.overview().currentAction()).isEmpty();
    }

    @Test
    void recheck_keeps_the_doubt_when_the_listing_shows_but_is_no_longer_active() {
        anUnconfirmedDeletion();
        vinted.showing(anInactiveListing(1));

        DeletionReport report = recheckListingDeletion().recheck(LISTING);

        assertThat(report.lastDeletion()).map(ListingDeletion::outcome).contains(DeletionOutcome.UNCONFIRMED);
        assertThat(listings.find(LISTING)).map(Listing::present).contains(true);
    }

    @Test
    void recheck_refuses_a_listing_without_unconfirmed_deletion() {
        aValidatedListing();
        vinted.showing(anActiveListing(1));

        assertThat(catchThrowable(() -> recheckListingDeletion().recheck(LISTING)))
                .isInstanceOf(NoUnconfirmedDeletion.class);
        assertThat(vinted.pagesOpened()).isZero();
    }

    @Test
    void scan_does_not_settle_an_unconfirmed_deletion() {
        // Étant donné une suppression non confirmée
        anUnconfirmedDeletion();
        // Quand un scan ne trouve plus l'annonce
        listings.saveAll(List.of(listings.find(LISTING).orElseThrow().absent()));
        // Alors l'annonce est marquée absente
        // Et la suppression reste non confirmée
        assertThat(new ShowListingDeletion(listings, listingBackups, photos, validations, deletions,
                DeletionMode.REAL).show(LISTING).lastDeletion()).map(ListingDeletion::outcome)
                .contains(DeletionOutcome.UNCONFIRMED);
        assertThat(catchThrowable(() -> deleteListing(DeletionMode.REAL).delete(LISTING, SAVED_AT)))
                .isInstanceOf(DeletionUnconfirmed.class);
    }

    private void anUnconfirmedDeletion() {
        aValidatedListing();
        vinted.showing(anActiveListing(1)).whereTheListingStaysOnceConfirmed();
        deleteListing(DeletionMode.REAL).delete(LISTING, SAVED_AT);
    }

    private void assertNothingHappened() {
        assertThat(vinted.pagesOpened()).isZero();
        assertThat(deletions.findAll()).isEmpty();
        assertThat(session.overview().currentAction()).isEmpty();
    }

    private void assertNoClickAndNoDeletion() {
        assertThat(vinted.confirmationClicks()).isZero();
        assertThat(deletions.findAll()).isEmpty();
        assertThat(listings.find(LISTING)).map(Listing::present).contains(true);
        assertThat(session.overview().currentAction()).isEmpty();
    }

    private RecheckListingDeletion recheckListingDeletion() {
        return new RecheckListingDeletion(session, listings, listingBackups, photos, validations, deletions, vinted,
                DeletionMode.REAL, clock);
    }

    private DeleteListing deleteListing(DeletionMode mode) {
        return new DeleteListing(session, listings, listingBackups, photos, validations, deletions, vinted, mode,
                clock);
    }

    /** Present, scanned, backed up with its photo kept, and validated by the member. */
    private ListingBackup aValidatedListing() {
        listings.saveAll(List.of(Listing.seen(anActiveListing(1), SCANNED_AT)));
        ListingReading reading = aReading(1);
        reading.photos().forEach(photo -> photos.store(LISTING, photo));
        ListingBackup backup = ListingBackup.of(reading, SAVED_AT);
        listingBackups.save(backup);
        validations.save(BackupValidation.of(backup, VALIDATED_AT));
        return backup;
    }
}
