package fr.vinted.republisher.acceptance;

import fr.vinted.republisher.application.cadence.CadenceCheck;
import fr.vinted.republisher.domain.cadence.CadenceLimitReached;
import fr.vinted.republisher.domain.cadence.VintedWrite;
import fr.vinted.republisher.domain.cadence.WriteCadence;
import fr.vinted.republisher.application.deletion.ShowListingDeletion;
import fr.vinted.republisher.application.journal.JournalWriter;
import fr.vinted.republisher.application.republication.NewListingSearch;
import fr.vinted.republisher.application.republication.RepublicationChecks;
import fr.vinted.republisher.application.republication.RepublishListing;
import fr.vinted.republisher.application.republication.ShowListingRepublication;
import fr.vinted.republisher.domain.backup.BackupNotVerified;
import fr.vinted.republisher.domain.backup.BackupReplaced;
import fr.vinted.republisher.domain.backup.BackupValidation;
import fr.vinted.republisher.domain.backup.ListingBackup;
import fr.vinted.republisher.domain.backup.ListingContent;
import fr.vinted.republisher.domain.backup.ListingReading;
import fr.vinted.republisher.domain.backup.VerificationFailure.PhotoAltered;
import fr.vinted.republisher.domain.backup.VerificationFailure.PhotoMissing;
import fr.vinted.republisher.domain.deletion.BackupNotValidated;
import fr.vinted.republisher.domain.deletion.DeletionMode;
import fr.vinted.republisher.domain.deletion.ListingDeletion;
import fr.vinted.republisher.domain.journal.ActionOutcome;
import fr.vinted.republisher.domain.journal.ActionRecord;
import fr.vinted.republisher.domain.journal.JournaledAction;
import fr.vinted.republisher.domain.republication.FieldNotFillable;
import fr.vinted.republisher.domain.republication.ListingAlreadyRepublished;
import fr.vinted.republisher.domain.republication.ListingFormField;
import fr.vinted.republisher.domain.republication.ListingFormUnreadable;
import fr.vinted.republisher.domain.republication.ListingNotDeleted;
import fr.vinted.republisher.domain.republication.ListingRepublication;
import fr.vinted.republisher.domain.republication.RepublicationDelay;
import fr.vinted.republisher.domain.republication.RepublicationInError;
import fr.vinted.republisher.domain.republication.RepublicationMode;
import fr.vinted.republisher.domain.republication.RepublicationOutcome;
import fr.vinted.republisher.domain.republication.RepublicationReport;
import fr.vinted.republisher.domain.republication.RepublicationTooEarly;
import fr.vinted.republisher.domain.session.BrowserAction;
import fr.vinted.republisher.domain.session.BrowserBusy;
import fr.vinted.republisher.domain.session.BrowserUnavailable;
import fr.vinted.republisher.domain.session.SessionDisconnected;
import fr.vinted.republisher.domain.session.VintedSession;
import fr.vinted.republisher.domain.session.VintedUnreachable;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.ScannedListing;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import fr.vinted.republisher.fakes.AdjustableClock;
import fr.vinted.republisher.fakes.FakeListingPublisher;
import fr.vinted.republisher.fakes.FakeWardrobeReader;
import fr.vinted.republisher.fakes.InMemoryActionJournal;
import fr.vinted.republisher.fakes.InMemoryBackupValidationRepository;
import fr.vinted.republisher.fakes.InMemoryListingBackupRepository;
import fr.vinted.republisher.fakes.InMemoryListingDeletionRepository;
import fr.vinted.republisher.fakes.InMemoryListingRepository;
import fr.vinted.republisher.fakes.InMemoryListingRepublicationRepository;
import fr.vinted.republisher.fakes.InMemoryPhotoStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static fr.vinted.republisher.domain.backup.BackupFixtures.aBackup;
import static fr.vinted.republisher.domain.backup.BackupFixtures.aContent;
import static fr.vinted.republisher.domain.backup.BackupFixtures.aPhoto;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.ACTIVE;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.aListing;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.anActiveListing;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.noPause;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.tuple;

class RepublishListingAcceptanceTest {

    private final InMemoryActionJournal journal = new InMemoryActionJournal();
    private static final VintedListingId LISTING = new VintedListingId(1);
    private static final VintedListingId NEW_LISTING = new VintedListingId(2);
    private static final Instant SAVED_AT = Instant.parse("2026-11-10T10:00:00Z");
    private static final Instant VALIDATED_AT = Instant.parse("2026-11-10T11:00:00Z");
    private static final Instant NOW = Instant.parse("2026-11-11T10:00:00Z");
    private static final ListingContent ROBE_BLEUE = aContent(1, "Robe bleue", "Portée une fois", "12.00");
    private static final ScannedListing REPUBLISHED = aListing(2, "Robe bleue", "12.00", ACTIVE);

    private final InMemoryListingRepository listings = new InMemoryListingRepository();
    private final InMemoryListingBackupRepository listingBackups = new InMemoryListingBackupRepository();
    private final InMemoryPhotoStorage photos = new InMemoryPhotoStorage();
    private final InMemoryBackupValidationRepository validations = new InMemoryBackupValidationRepository();
    private final InMemoryListingDeletionRepository deletions = new InMemoryListingDeletionRepository();
    private final InMemoryListingRepublicationRepository republications = new InMemoryListingRepublicationRepository();
    private final FakeWardrobeReader wardrobe = new FakeWardrobeReader();
    private final FakeListingPublisher vinted = new FakeListingPublisher(wardrobe);
    private final VintedSession session = new VintedSession();
    private final AdjustableClock clock = new AdjustableClock(NOW);

    @Test
    void republishesConfirmedDeletion() {
        // Étant donné une annonce « Robe bleue » à 12,00 €, sauvegarde validée, suppression confirmée il y a 6 minutes
        // Et les republications réelles activées
        ListingBackup backup = aDeletedListing(Duration.ofMinutes(6));
        vinted.showingOncePublished(REPUBLISHED, anActiveListing(3));
        listings.saveAll(List.of(Listing.seen(anActiveListing(3), NOW)));
        // Quand le membre confirme « Republier maintenant »
        RepublicationReport report = republishListing(RepublicationMode.REAL).republish(LISTING, SAVED_AT);
        // Alors le formulaire est rempli avec tout le contenu et les photos dans l'ordre, puis publié
        assertThat(vinted.filledContent()).contains(ROBE_BLEUE);
        assertThat(vinted.sentPhotos()).containsExactly(aPhoto("front"), aPhoto("back"));
        assertThat(vinted.publishClicks()).isEqualTo(1);
        // Et le dressing montre une seule nouvelle annonce « Robe bleue » à 12,00 €
        assertThat(wardrobe.opened()).containsExactly(NEW_LISTING);
        // Et la republication est réussie, avec son nouvel identifiant
        ListingRepublication republication = report.lastRepublication().orElseThrow();
        assertThat(republication.outcome()).isEqualTo(RepublicationOutcome.PUBLISHED);
        assertThat(republication.newListingId()).contains(NEW_LISTING);
        assertThat(republication.attemptedAt()).isEqualTo(NOW);
        assertThat(republication.publishedAt()).contains(NOW);
        assertThat(republication.backupSavedAt()).isEqualTo(SAVED_AT);
        assertThat(republications.findAll()).containsExactly(republication);
        // Et la nouvelle annonce est présente, reliée à l'ancienne, sans sauvegarde
        assertThat(listings.find(NEW_LISTING)).map(Listing::present).contains(true);
        assertThat(report.newListing()).map(Listing::url).contains(REPUBLISHED.url());
        assertThat(listingBackups.find(NEW_LISTING)).isEmpty();
        // L'ancienne annonce reste absente avec sa sauvegarde, ses photos, sa validation et sa suppression
        assertThat(listings.find(LISTING)).map(Listing::present).contains(false);
        assertThat(listingBackups.find(LISTING)).contains(backup);
        assertThat(photos.files(1)).hasSize(2);
        assertThat(report.deletion().backup().validatedAt()).contains(VALIDATED_AT);
        assertThat(report.deletion().lastDeletion()).isPresent();
        assertThat(session.overview().currentAction()).isEmpty();
    }

    @Test
    void refusesWithoutConfirmedDeletion() {
        // Étant donné une annonce sans suppression, une autre dont la suppression est non confirmée, une troisième non faite
        aValidatedListing();
        assertRefusedBeforeChrome(ListingNotDeleted.class);
        ListingDeletion unconfirmed = ListingDeletion.attempted(listingBackups.find(LISTING).orElseThrow(),
                NOW.minus(Duration.ofHours(1)));
        deletions.save(unconfirmed);
        // Quand le membre demande leur republication
        // Alors chacune est refusée (NOT_DELETED) et Chrome n'est pas ouvert
        assertRefusedBeforeChrome(ListingNotDeleted.class);
        deletions.save(unconfirmed.notDone(NOW.minus(Duration.ofMinutes(30))));
        assertRefusedBeforeChrome(ListingNotDeleted.class);
        // Une suppression confirmée d'une autre version de la sauvegarde ne compte pas
        deletions.save(ListingDeletion.attempted(ListingBackup.of(new ListingReading(ROBE_BLEUE, List.of(
                aPhoto("old"))), SAVED_AT.minusSeconds(3600)), NOW.minus(Duration.ofMinutes(20)))
                .confirmed(NOW.minus(Duration.ofMinutes(20))));
        assertRefusedBeforeChrome(ListingNotDeleted.class);
    }

    @Test
    void refusesBeforeRepublicationDate() {
        // Étant donné une suppression prouvée il y a 2 minutes
        aDeletedListing(Duration.ofMinutes(2));
        // Quand le membre demande la republication
        Throwable refusal = catchThrowable(() -> republishListing(RepublicationMode.REAL).republish(LISTING, SAVED_AT));
        // Alors elle est refusée (TOO_EARLY) avec la date de republication, et Chrome n'est pas ouvert
        assertThat(refusal).isInstanceOfSatisfying(RepublicationTooEarly.class, tooEarly ->
                assertThat(tooEarly.republishableAt()).isEqualTo(NOW.plus(Duration.ofMinutes(3))));
        assertNothingHappened();
    }

    @Test
    void refusesReplacedOrUnvalidatedBackup() {
        // Étant donné une sauvegarde remplacée depuis l'aperçu, ou dont la validation ne compte plus
        aDeletedListing(Duration.ofMinutes(6));
        // Quand le membre demande la republication
        // Alors elle est refusée (BACKUP_REPLACED ou NOT_VALIDATED) et Chrome n'est pas ouvert
        Throwable replaced = catchThrowable(() -> republishListing(RepublicationMode.REAL)
                .republish(LISTING, SAVED_AT.minusSeconds(60)));
        assertThat(replaced).isInstanceOf(BackupReplaced.class);
        validations.save(new BackupValidation(LISTING, SAVED_AT.minusSeconds(3600), VALIDATED_AT));
        assertRefusedBeforeChrome(BackupNotValidated.class);
    }

    @Test
    void refusesMissingOrAlteredPhotos() {
        // Étant donné une sauvegarde dont une photo manque sur disque, ou dont l'empreinte a changé
        ListingBackup backup = aDeletedListing(Duration.ofMinutes(6));
        photos.lose(1, backup.photos().get(1));
        // Quand le membre demande la republication
        Throwable missing = catchThrowable(() -> republishListing(RepublicationMode.REAL).republish(LISTING, SAVED_AT));
        // Alors elle est refusée (PHOTOS_NOT_VERIFIED) avec la photo en cause, et Chrome n'est pas ouvert
        // Et l'absence de l'annonce dans le dressing n'est pas un motif de refus
        assertThat(missing).isInstanceOfSatisfying(BackupNotVerified.class, notVerified ->
                assertThat(notVerified.verification().failures()).containsExactly(new PhotoMissing(1)));
        photos.overwrite(1, backup.photos().get(1), "retouchée".getBytes(StandardCharsets.UTF_8));
        Throwable altered = catchThrowable(() -> republishListing(RepublicationMode.REAL).republish(LISTING, SAVED_AT));
        assertThat(altered).isInstanceOfSatisfying(BackupNotVerified.class, notVerified ->
                assertThat(notVerified.verification().failures()).containsExactly(new PhotoAltered(1)));
        assertNothingHappened();
    }

    @Test
    void refusesSecondRepublication() {
        // Étant donné une annonce déjà republiée, une autre en erreur de republication
        aDeletedListing(Duration.ofMinutes(6));
        vinted.showingOncePublished(REPUBLISHED);
        republishListing(RepublicationMode.REAL).republish(LISTING, SAVED_AT);
        int formsOpened = vinted.formsOpened();
        // Quand le membre demande leur republication
        // Alors elles sont refusées (ALREADY_REPUBLISHED, REPUBLICATION_IN_ERROR) et Chrome n'est pas ouvert
        Throwable republished = catchThrowable(() ->
                republishListing(RepublicationMode.REAL).republish(LISTING, SAVED_AT));
        assertThat(republished).isInstanceOf(ListingAlreadyRepublished.class);
        assertThat(vinted.formsOpened()).isEqualTo(formsOpened);
    }

    @Test
    void refusesRepublicationInError() {
        aDeletedListing(Duration.ofMinutes(6));
        republications.save(ListingRepublication.attempted(listingBackups.find(LISTING).orElseThrow(), NOW));
        Throwable inError = catchThrowable(() -> republishListing(RepublicationMode.REAL).republish(LISTING, SAVED_AT));
        assertThat(inError).isInstanceOf(RepublicationInError.class);
        assertThat(vinted.formsOpened()).isZero();
    }

    @Test
    void abandonsWhenFieldNotFillable() {
        // Étant donné une sauvegarde dont la taille 588 est introuvable dans le formulaire
        aDeletedListing(Duration.ofMinutes(6));
        vinted.whereSizeCannotBeFilled();
        // Quand le membre demande la republication
        Throwable refusal = catchThrowable(() -> republishListing(RepublicationMode.REAL).republish(LISTING, SAVED_AT));
        // Alors elle est refusée (FIELD_NOT_FILLABLE, « Taille »), « Publier » n'est pas cliqué
        // Et aucune republication n'est enregistrée ; le membre peut relancer
        assertThat(refusal).isInstanceOfSatisfying(FieldNotFillable.class, notFillable -> {
            assertThat(notFillable.field()).isEqualTo(ListingFormField.SIZE);
            assertThat(notFillable.optionId()).contains(588L);
        });
        assertNoPublishingAndNoRepublication();
    }

    static Stream<Arguments> refusesOnErrorBeforePublishClick() {
        return Stream.of(
                Arguments.of("session non connectée",
                        (Consumer<RepublishListingAcceptanceTest>) test -> test.vinted.whereSessionIsDisconnected(),
                        SessionDisconnected.class),
                Arguments.of("navigateur indisponible",
                        (Consumer<RepublishListingAcceptanceTest>) test -> test.vinted.whereBrowserCannotStart(),
                        BrowserUnavailable.class),
                Arguments.of("Vinted inaccessible",
                        (Consumer<RepublishListingAcceptanceTest>) test -> test.vinted.whereVintedRefuses(),
                        VintedUnreachable.class),
                Arguments.of("formulaire illisible",
                        (Consumer<RepublishListingAcceptanceTest>) test -> test.vinted.whereTheFormIsUnreadable(),
                        ListingFormUnreadable.class),
                Arguments.of("erreur inattendue",
                        (Consumer<RepublishListingAcceptanceTest>) test -> test.vinted.whereBrowserFailsUnexpectedly(),
                        IllegalStateException.class),
                Arguments.of("base indisponible",
                        (Consumer<RepublishListingAcceptanceTest>) test -> test.republications.whereDatabaseIsDown(),
                        IllegalStateException.class));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void refusesOnErrorBeforePublishClick(String situation, Consumer<RepublishListingAcceptanceTest> failure,
                                          Class<? extends RuntimeException> problem) {
        // Étant donné <situation>
        aDeletedListing(Duration.ofMinutes(6));
        failure.accept(this);
        // Quand le membre demande la republication
        Throwable refusal = catchThrowable(() -> republishListing(RepublicationMode.REAL).republish(LISTING, SAVED_AT));
        // Alors elle est refusée (<code>), rien n'est publié ni enregistré, et le verrou est libéré
        assertThat(refusal).isInstanceOf(problem);
        assertNoPublishingAndNoRepublication();
    }

    @Test
    void leavesErrorWhenNewListingNotFoundOrAmbiguous() {
        // Étant donné un clic sur « Publier » fait
        // Quand le dressing ne montre aucune nouvelle annonce avec ce titre et ce prix, ou en montre deux
        // Alors la republication reste « erreur de republication », sans nouvel essai
        // Et l'aperçu affiche « erreur de republication, vérifier sur Vinted »
        aDeletedListing(Duration.ofMinutes(6));
        vinted.showingOncePublished(aListing(2, "Robe bleue", "15.00", ACTIVE));
        assertLeftInError(republishListing(RepublicationMode.REAL).republish(LISTING, SAVED_AT));

        RepublishListingAcceptanceTest ambiguous = new RepublishListingAcceptanceTest();
        ambiguous.aDeletedListing(Duration.ofMinutes(6));
        ambiguous.vinted.showingOncePublished(REPUBLISHED, aListing(3, "Robe bleue", "12.00", ACTIVE));
        ambiguous.assertLeftInError(ambiguous.republishListing(RepublicationMode.REAL).republish(LISTING, SAVED_AT));
    }

    @Test
    void leavesErrorOnFailureOrCrashAfterPublishClick() {
        // Étant donné un clic sur « Publier » fait
        // Quand la lecture du dressing échoue, ou l'outil s'arrête avant de la faire
        // Alors la republication est « erreur de republication » en base, et bloque toute relance
        aDeletedListing(Duration.ofMinutes(6));
        wardrobe.during(wardrobe::whereVintedRefusesTheWardrobe);
        assertLeftInError(republishListing(RepublicationMode.REAL).republish(LISTING, SAVED_AT));

        RepublishListingAcceptanceTest crash = new RepublishListingAcceptanceTest();
        crash.aDeletedListing(Duration.ofMinutes(6));
        crash.vinted.whereBrowserStopsAfterPublishing();
        crash.assertLeftInError(crash.republishListing(RepublicationMode.REAL).republish(LISTING, SAVED_AT));
    }

    @Test
    void dryRunFillsFormWithoutPublishing() {
        // Étant donné le dry-run actif (REPUBLICATION_DRY_RUN absente ou true)
        aDeletedListing(Duration.ofMinutes(6));
        vinted.showingOncePublished(REPUBLISHED);
        // Quand le membre demande la republication d'une annonce qui passe tous les contrôles
        RepublicationReport report = republishListing(RepublicationMode.DRY_RUN).republish(LISTING, SAVED_AT);
        // Alors le formulaire est entièrement rempli, « Publier » n'est pas cliqué, rien n'est enregistré
        // Et la réponse indique un dry-run réussi
        assertThat(vinted.filledContent()).contains(ROBE_BLEUE);
        assertThat(vinted.sentPhotos()).hasSize(2);
        assertThat(report.dryRunPassed()).isTrue();
        assertThat(report.mode()).isEqualTo(RepublicationMode.DRY_RUN);
        assertThat(report.lastRepublication()).isEmpty();
        assertThat(wardrobe.reads()).isZero();
        assertNoPublishingAndNoRepublication();
    }

    @Test
    void refusesWhileBrowserBusy() {
        // Étant donné un scan en cours
        aDeletedListing(Duration.ofMinutes(6));
        session.start(BrowserAction.SCANNING);
        // Quand le membre demande une republication
        Throwable refusal = catchThrowable(() -> republishListing(RepublicationMode.REAL).republish(LISTING, SAVED_AT));
        // Alors elle est refusée (BROWSER_BUSY)
        assertThat(refusal).isInstanceOf(BrowserBusy.class);
        assertThat(session.overview().currentAction()).contains(BrowserAction.SCANNING);
        assertThat(vinted.formsOpened()).isZero();
    }

    @Test
    void republication_outcomes_are_written() {
        // US-23 AT-23-08. Étant donné l'annonce supprimée, date de republication atteinte
        aDeletedListing(Duration.ofMinutes(6));
        vinted.showingOncePublished(REPUBLISHED, anActiveListing(3));
        listings.saveAll(List.of(Listing.seen(anActiveListing(3), NOW)));
        // Quand sa republication réussit
        republishListing(RepublicationMode.REAL).republish(LISTING, SAVED_AT);
        // Alors le journal contient « Republication : réussie »
        assertThat(journal.records()).extracting(ActionRecord::action, ActionRecord::outcome, ActionRecord::reason).containsExactly(
                tuple(JournaledAction.REPUBLICATION, ActionOutcome.SUCCEEDED, Optional.empty()));

        // Et une republication trop tôt d'une autre annonce écrit « Republication : refusée », raison « trop tôt »
        RepublishListingAcceptanceTest tooEarly = new RepublishListingAcceptanceTest();
        tooEarly.aDeletedListing(Duration.ofMinutes(2));
        catchThrowable(() -> tooEarly.republishListing(RepublicationMode.REAL).republish(LISTING, SAVED_AT));
        assertThat(tooEarly.journal.records()).extracting(ActionRecord::action, ActionRecord::outcome, ActionRecord::reason).containsExactly(
                tuple(JournaledAction.REPUBLICATION, ActionOutcome.REFUSED, Optional.of("TOO_EARLY")));
    }

    @Test
    void republication_refused_on_photos_is_written_with_the_republication_code() {
        // US-23. Étant donné une sauvegarde dont une photo manque sur disque
        ListingBackup backup = aDeletedListing(Duration.ofMinutes(6));
        photos.lose(1, backup.photos().get(1));
        // Quand le membre demande la republication
        catchThrowable(() -> republishListing(RepublicationMode.REAL).republish(LISTING, SAVED_AT));
        // Alors le journal contient « Republication : refusée » avec le code de la réponse de l'API
        assertThat(journal.records()).extracting(ActionRecord::action, ActionRecord::outcome, ActionRecord::reason).containsExactly(
                tuple(JournaledAction.REPUBLICATION, ActionOutcome.REFUSED, Optional.of("PHOTOS_NOT_VERIFIED")));
    }

    @Test
    void republication_dry_run_and_error_are_written() {
        aDeletedListing(Duration.ofMinutes(6));
        vinted.showingOncePublished(REPUBLISHED);
        republishListing(RepublicationMode.DRY_RUN).republish(LISTING, SAVED_AT);
        assertThat(journal.records()).extracting(ActionRecord::action, ActionRecord::outcome, ActionRecord::reason).containsExactly(
                tuple(JournaledAction.REPUBLICATION, ActionOutcome.DRY_RUN, Optional.empty()));

        RepublishListingAcceptanceTest inError = new RepublishListingAcceptanceTest();
        inError.aDeletedListing(Duration.ofMinutes(6));
        inError.vinted.showingOncePublished(aListing(2, "Robe bleue", "15.00", ACTIVE));
        inError.republishListing(RepublicationMode.REAL).republish(LISTING, SAVED_AT);
        assertThat(inError.journal.records()).extracting(ActionRecord::action, ActionRecord::outcome, ActionRecord::reason).containsExactly(
                tuple(JournaledAction.REPUBLICATION, ActionOutcome.IN_DOUBT, Optional.empty()));
    }

    @Test
    void refusesRepublicationTooSoonAfterADeletion() {
        // Étant donné une suppression tentée à 10:00
        aDeletedListing(Duration.ofMinutes(6));
        clock.set(NOW.plus(Duration.ofMinutes(1)));
        deletions.save(ListingDeletion.attempted(aBackup(5, SAVED_AT), NOW));
        // Quand le membre lance la republication d'une autre annonce à 10:01
        Throwable refusal = catchThrowable(() -> republishListing(RepublicationMode.REAL).republish(LISTING, SAVED_AT));
        // Alors elle est refusée (CADENCE_LIMIT_REACHED, à partir de 10:02), rien n'est publié ni enregistré, et le
        // verrou est libéré
        assertThat(refusal).isInstanceOfSatisfying(CadenceLimitReached.class,
                limit -> assertThat(limit.allowedAt()).isEqualTo(NOW.plus(Duration.ofMinutes(2))));
        assertNothingHappened();
    }

    @Test
    void refusesRepublicationOverTheDailyLimitButNotDeletion() {
        // Étant donné deux republications tentées depuis moins de 24 heures, la dernière il y a plus de 2 minutes,
        // quelle que soit leur issue
        aDeletedListing(Duration.ofMinutes(6));
        republications.save(ListingRepublication.attempted(aBackup(5, SAVED_AT), NOW.minus(Duration.ofHours(10))));
        republications.save(ListingRepublication.attempted(aBackup(6, SAVED_AT), NOW.minus(Duration.ofHours(3)))
                .notPublished(NOW.minus(Duration.ofHours(2))));
        // Quand le membre lance une republication
        Throwable refusal = catchThrowable(() -> republishListing(RepublicationMode.REAL).republish(LISTING, SAVED_AT));
        // Alors elle est refusée (CADENCE_LIMIT_REACHED, 24 heures après la plus ancienne)
        assertThat(refusal).isInstanceOfSatisfying(CadenceLimitReached.class,
                limit -> assertThat(limit.allowedAt()).isEqualTo(NOW.plus(Duration.ofHours(14))));
        assertThat(vinted.formsOpened()).isZero();
        assertThat(republications.findLatest(LISTING)).isEmpty();
        assertThat(session.overview().currentAction()).isEmpty();
        // Quand il lance une suppression (une seule tentée sur 24 heures, il y a 6 minutes)
        // Alors elle est permise
        assertThatCode(() -> cadenceCheck().check(VintedWrite.DELETION)).doesNotThrowAnyException();
    }

    private void assertLeftInError(RepublicationReport report) {
        assertThat(vinted.publishClicks()).isEqualTo(1);
        assertThat(report.lastRepublication()).map(ListingRepublication::outcome)
                .contains(RepublicationOutcome.FAILED);
        assertThat(republications.findLatest(LISTING)).map(ListingRepublication::outcome)
                .contains(RepublicationOutcome.FAILED);
        assertThat(listings.find(NEW_LISTING)).isEmpty();
        assertThat(session.overview().currentAction()).isEmpty();
        assertThat(catchThrowable(() -> republishListing(RepublicationMode.REAL).republish(LISTING, SAVED_AT)))
                .isInstanceOf(RepublicationInError.class);
        assertThat(vinted.publishClicks()).isEqualTo(1);
    }

    private void assertRefusedBeforeChrome(Class<? extends RuntimeException> refusal) {
        assertThat(catchThrowable(() -> republishListing(RepublicationMode.REAL).republish(LISTING, SAVED_AT)))
                .isInstanceOf(refusal);
        assertNothingHappened();
    }

    private void assertNothingHappened() {
        assertThat(vinted.formsOpened()).isZero();
        assertThat(republications.findAll()).isEmpty();
        assertThat(session.overview().currentAction()).isEmpty();
    }

    private void assertNoPublishingAndNoRepublication() {
        assertThat(vinted.publishClicks()).isZero();
        assertThat(republications.findAll()).isEmpty();
        assertThat(listings.find(NEW_LISTING)).isEmpty();
        assertThat(session.overview().currentAction()).isEmpty();
    }

    private RepublishListing republishListing(RepublicationMode mode) {
        RepublicationDelay delay = new RepublicationDelay(Duration.ofMinutes(5));
        ShowListingRepublication show = new ShowListingRepublication(new ShowListingDeletion(listings,
                listingBackups, photos, validations, deletions, DeletionMode.REAL, delay), republications, listings,
                mode);
        return new RepublishListing(session,
                new RepublicationChecks(listingBackups, photos, validations, deletions, republications, delay, clock),
                cadenceCheck(),
                republications, vinted, new NewListingSearch(wardrobe, noPause(), listings), listings, mode, clock,
                show, new JournalWriter(journal, clock));
    }

    private CadenceCheck cadenceCheck() {
        return new CadenceCheck(deletions, republications, new WriteCadence(Duration.ofMinutes(2), 2, 2), clock);
    }

    /** Backed up with two photos kept, validated by the member, absent from the wardrobe: not deleted yet. */
    private ListingBackup aValidatedListing() {
        listings.saveAll(List.of(Listing.seen(aListing(1, "Robe bleue", "12.00", ACTIVE), SAVED_AT).absent()));
        ListingReading reading = new ListingReading(ROBE_BLEUE, List.of(aPhoto("front"), aPhoto("back")));
        reading.photos().forEach(photo -> photos.store(LISTING, photo));
        ListingBackup backup = ListingBackup.of(reading, SAVED_AT);
        listingBackups.save(backup);
        validations.save(BackupValidation.of(backup, VALIDATED_AT));
        return backup;
    }

    /** Validated, then deleted on Vinted with the deletion proven {@code ago}. */
    private ListingBackup aDeletedListing(Duration ago) {
        ListingBackup backup = aValidatedListing();
        Instant provenAt = NOW.minus(ago);
        deletions.save(ListingDeletion.attempted(backup, provenAt.minusSeconds(5)).confirmed(provenAt));
        return backup;
    }
}
