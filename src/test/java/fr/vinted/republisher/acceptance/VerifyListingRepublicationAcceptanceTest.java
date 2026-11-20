package fr.vinted.republisher.acceptance;

import fr.vinted.republisher.application.cadence.CadenceCheck;
import fr.vinted.republisher.application.deletion.ShowListingDeletion;
import fr.vinted.republisher.application.journal.JournalWriter;
import fr.vinted.republisher.application.republication.NewListingSearch;
import fr.vinted.republisher.application.republication.RepublicationChecks;
import fr.vinted.republisher.application.republication.RepublishListing;
import fr.vinted.republisher.application.republication.ShowListingRepublication;
import fr.vinted.republisher.application.republication.VerifyListingRepublication;
import fr.vinted.republisher.domain.backup.BackupValidation;
import fr.vinted.republisher.domain.backup.CategoryDetails;
import fr.vinted.republisher.domain.backup.FormOption;
import fr.vinted.republisher.domain.backup.FormOptions;
import fr.vinted.republisher.domain.backup.ListingBackup;
import fr.vinted.republisher.domain.backup.ListingContent;
import fr.vinted.republisher.domain.backup.ListingFieldDifference;
import fr.vinted.republisher.domain.backup.ListingFormField;
import fr.vinted.republisher.domain.backup.ListingNotBackedUp;
import fr.vinted.republisher.domain.backup.ListingReading;
import fr.vinted.republisher.domain.backup.ListingUnreadable;
import fr.vinted.republisher.domain.cadence.WriteCadence;
import fr.vinted.republisher.domain.cadence.WriteOrigin;
import fr.vinted.republisher.domain.deletion.DeletionMode;
import fr.vinted.republisher.domain.deletion.ListingDeletion;
import fr.vinted.republisher.domain.journal.ActionOutcome;
import fr.vinted.republisher.domain.journal.ActionRecord;
import fr.vinted.republisher.domain.journal.JournaledAction;
import fr.vinted.republisher.domain.republication.ListingRepublication;
import fr.vinted.republisher.domain.republication.NoRepublicationToVerify;
import fr.vinted.republisher.domain.republication.RepublicationDelay;
import fr.vinted.republisher.domain.republication.RepublicationMode;
import fr.vinted.republisher.domain.republication.RepublicationOutcome;
import fr.vinted.republisher.domain.republication.RepublicationReport;
import fr.vinted.republisher.domain.session.BrowserAction;
import fr.vinted.republisher.domain.session.BrowserBusy;
import fr.vinted.republisher.domain.session.BrowserUnavailable;
import fr.vinted.republisher.domain.session.VintedSession;
import fr.vinted.republisher.domain.session.VintedUnreachable;
import fr.vinted.republisher.domain.wardrobe.Listing;
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
import fr.vinted.republisher.fakes.InMemoryPhotoStorage;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static fr.vinted.republisher.domain.backup.BackupFixtures.aPhoto;
import static fr.vinted.republisher.domain.backup.BackupFixtures.aContent;
import static fr.vinted.republisher.domain.backup.BackupFixtures.noBackupPause;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.ACTIVE;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.aListing;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.anActiveListing;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.noPause;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class VerifyListingRepublicationAcceptanceTest {

    private static final VintedListingId LISTING = new VintedListingId(1);
    private static final VintedListingId NEW_LISTING = new VintedListingId(2);
    private static final Instant SAVED_AT = Instant.parse("2026-11-10T10:00:00Z");
    private static final Instant CLICKED_AT = Instant.parse("2026-11-11T10:06:00Z");
    private static final Instant NOW = Instant.parse("2026-11-12T09:00:00Z");
    private static final ListingContent ROBE_BLEUE = aContent(1, "Robe bleue", "Portée une fois", "12.00");
    private static final ScannedListing REPUBLISHED = aListing(2, "Robe bleue", "12.00", ACTIVE);

    private final InMemoryListingRepository listings = new InMemoryListingRepository();
    private final InMemoryListingBackupRepository listingBackups = new InMemoryListingBackupRepository();
    private final InMemoryPhotoStorage photos = new InMemoryPhotoStorage();
    private final InMemoryBackupValidationRepository validations = new InMemoryBackupValidationRepository();
    private final InMemoryListingDeletionRepository deletions = new InMemoryListingDeletionRepository();
    private final InMemoryListingRepublicationRepository republications = new InMemoryListingRepublicationRepository();
    private final FakeWardrobeReader wardrobe = new FakeWardrobeReader();
    private final FakeListingContentReader contents = new FakeListingContentReader();
    private final VintedSession session = new VintedSession();
    private final AdjustableClock clock = new AdjustableClock(NOW);
    private final InMemoryActionJournal journal = new InMemoryActionJournal();
    private JournalWriter journalWriter = new JournalWriter(journal, clock);

    @Test
    void failed_republication_found_in_the_wardrobe_becomes_published() {
        // Étant donné une annonce « Robe bleue » à 12,00 € en erreur de republication
        ListingRepublication failed = aFailedRepublication();
        // Et le dressing montre une seule annonce inconnue « Robe bleue » à 12,00 €
        wardrobe.holding(REPUBLISHED, anActiveListing(3));
        listings.saveAll(List.of(Listing.seen(anActiveListing(3), SAVED_AT)));
        contents.showing(new ListingReading(sameContentAs(NEW_LISTING), List.of(aPhoto("a"), aPhoto("b"))));
        // Quand le membre clique sur « Vérifier sur Vinted »
        RepublicationReport report = verify();
        // Alors la republication est réussie, avec le nouvel identifiant et la date de la vérification
        ListingRepublication republication = report.lastRepublication().orElseThrow();
        assertThat(republication.id()).isEqualTo(failed.id());
        assertThat(republication.outcome()).isEqualTo(RepublicationOutcome.PUBLISHED);
        assertThat(republication.newListingId()).contains(NEW_LISTING);
        assertThat(republication.publishedAt()).contains(NOW);
        assertThat(wardrobe.opened()).containsExactly(NEW_LISTING);
        // Et la nouvelle annonce est présente et reliée à l'ancienne
        assertThat(listings.find(NEW_LISTING)).map(Listing::present).contains(true);
        assertThat(report.newListing()).map(Listing::id).contains(NEW_LISTING);
        // Et la nouvelle annonce est comparée à la sauvegarde
        assertThat(contents.opened()).containsExactly(NEW_LISTING);
        assertThat(report.comparison()).contains(List.of());
        assertThat(session.overview().currentAction()).isEmpty();
    }

    @Test
    void new_listing_shown_on_opening_is_found_without_going_through_the_wardrobe() {
        // Étant donné une republication en erreur
        aFailedRepublication();
        // Et la nouvelle annonce parmi les annonces que le dressing affiche à son ouverture
        wardrobe.holding(REPUBLISHED, aListing(3, "Pull gris", "8.00", ACTIVE)).showingOnOpening(1);
        contents.showing(new ListingReading(sameContentAs(NEW_LISTING), List.of(aPhoto("a"), aPhoto("b"))));
        // Quand le membre clique sur « Vérifier sur Vinted »
        RepublicationReport report = verify();
        // Alors la republication est réussie, sans que le dressing soit parcouru en entier
        assertThat(report.lastRepublication()).map(ListingRepublication::outcome)
                .contains(RepublicationOutcome.PUBLISHED);
        assertThat(wardrobe.reads()).isZero();
    }

    @Test
    void failed_republication_missing_from_the_newest_listings_stays_failed() {
        // Étant donné une annonce en erreur de republication
        ListingRepublication failed = aFailedRepublication();
        // Et les annonces affichées à l'ouverture du dressing n'ont pas son titre et son prix
        wardrobe.holding(aListing(3, "Robe bleue", "15.00", ACTIVE), REPUBLISHED).showingOnOpening(1);
        // Quand le membre clique sur « Vérifier sur Vinted »
        RepublicationReport report = verify();
        // Alors la republication reste en erreur, sans que le dressing soit parcouru en entier
        assertThat(report.lastRepublication()).contains(failed);
        assertThat(republications.findAll()).containsExactly(failed);
        assertThat(wardrobe.reads()).isZero();
        assertThat(report.comparison()).isEmpty();
        assertThat(contents.opened()).isEmpty();
    }

    @Test
    void several_candidates_leave_the_republication_failed() {
        // Étant donné une annonce en erreur de republication
        ListingRepublication failed = aFailedRepublication();
        // Et le dressing montre deux annonces avec son titre et son prix
        wardrobe.holding(REPUBLISHED, aListing(3, "Robe bleue", "12.00", ACTIVE));
        // Quand le membre clique sur « Vérifier sur Vinted »
        RepublicationReport report = verify();
        // Alors la republication reste en erreur
        assertThat(report.lastRepublication()).contains(failed);
        assertThat(republications.findAll()).containsExactly(failed);
        assertThat(listings.find(NEW_LISTING)).isEmpty();
        assertThat(contents.opened()).isEmpty();
        assertThat(session.overview().currentAction()).isEmpty();
    }

    @Test
    void new_listing_already_scanned_is_still_found() {
        // Étant donné une annonce en erreur de republication
        aFailedRepublication();
        // Et un scan a enregistré entre-temps la nouvelle annonce, même titre et même prix
        listings.saveAll(List.of(Listing.seen(REPUBLISHED, CLICKED_AT.plusSeconds(3600))));
        wardrobe.holding(REPUBLISHED, anActiveListing(3), anActiveListing(4));
        listings.saveAll(List.of(Listing.seen(anActiveListing(3), SAVED_AT)));
        contents.showing(new ListingReading(sameContentAs(NEW_LISTING), List.of(aPhoto("a"), aPhoto("b"))));
        // Quand le membre clique sur « Vérifier sur Vinted »
        RepublicationReport report = verify();
        // Alors la republication est réussie avec cette annonce
        assertThat(report.lastRepublication()).map(ListingRepublication::newListingId).contains(Optional.of(NEW_LISTING));
        assertThat(wardrobe.opened()).containsExactlyInAnyOrder(NEW_LISTING, new VintedListingId(4));
    }

    @Test
    void new_listing_of_another_republication_is_never_a_candidate() {
        aFailedRepublication();
        ListingRepublication other = ListingRepublication.attempted(ListingBackup.of(new ListingReading(
                aContent(5, "Robe bleue", "Autre", "12.00"), List.of(aPhoto("x"))), SAVED_AT), CLICKED_AT,
                        WriteOrigin.RUN)
                .published(NEW_LISTING, CLICKED_AT);
        republications.save(other);
        listings.saveAll(List.of(Listing.seen(REPUBLISHED, CLICKED_AT)));
        wardrobe.holding(REPUBLISHED);

        RepublicationReport report = verify();

        assertThat(report.lastRepublication()).map(ListingRepublication::outcome)
                .contains(RepublicationOutcome.FAILED);
        assertThat(wardrobe.opened()).isEmpty();
    }

    @Test
    void identical_new_listing_shows_no_difference() {
        // Étant donné une republication réussie dont la nouvelle annonce a le même contenu et le même nombre de photos
        ListingRepublication published = aPublishedRepublication();
        contents.showing(new ListingReading(sameContentAs(NEW_LISTING), List.of(aPhoto("a"), aPhoto("b"))));
        // Quand le membre clique sur « Vérifier sur Vinted »
        RepublicationReport report = verify();
        // Alors la réponse dit « identique à la sauvegarde »
        assertThat(report.comparison()).contains(List.of());
        // Et rien n'est enregistré
        assertThat(republications.findAll()).containsExactly(published);
        assertThat(wardrobe.reads()).isZero();
        assertThat(session.overview().currentAction()).isEmpty();
    }

    @Test
    void different_new_listing_lists_the_differences() {
        // Étant donné une republication réussie dont la nouvelle annonce a la taille 614 au lieu de 613,
        // une couleur en plus et une photo de moins
        ListingRepublication published = aPublishedRepublication();
        ListingContent same = sameContentAs(NEW_LISTING);
        contents.showing(new ListingReading(new ListingContent(NEW_LISTING, same.title(), same.description(),
                same.price(), same.category(), same.brand(), same.condition(),
                Optional.of(new FormOption(614, "3-6 mois")), Optional.of(new FormOptions(List.of(12L, 1L),
                "Blanc, Noir")), same.materials(), same.packageSize(), CategoryDetails.none()),
                List.of(aPhoto("a"))));
        // Quand le membre clique sur « Vérifier sur Vinted »
        RepublicationReport report = verify();
        // Alors la réponse liste les différences de taille, de couleurs et de photos
        assertThat(report.comparison()).contains(List.of(
                new ListingFieldDifference(ListingFormField.PHOTOS, "2", "1"),
                new ListingFieldDifference(ListingFormField.SIZE, "588", "3-6 mois (614)"),
                new ListingFieldDifference(ListingFormField.COLORS, "Blanc (12)", "Blanc, Noir (1, 12)")));
        // Et rien n'est corrigé sur Vinted ni enregistré
        assertThat(republications.findAll()).containsExactly(published);
    }

    @Test
    void not_published_republication_allows_a_new_attempt() {
        // Étant donné une republication « non publiée »
        ListingRepublication notPublished = aFailedRepublication().notPublished(NOW);
        republications.save(notPublished);
        FakeListingPublisher vinted = new FakeListingPublisher(wardrobe);
        vinted.showingOncePublished(REPUBLISHED);
        // Quand le membre republie l'annonce
        RepublicationReport report = republishListing(vinted).republish(LISTING, SAVED_AT, WriteOrigin.RUN);
        // Alors une nouvelle tentative est enregistrée et l'ancienne reste en base
        assertThat(report.lastRepublication()).map(ListingRepublication::outcome)
                .contains(RepublicationOutcome.PUBLISHED);
        assertThat(republications.findAll()).hasSize(2).contains(notPublished);
    }

    @Test
    void nothing_to_verify_is_refused_without_opening_chrome() {
        // Étant donné une annonce sans suppression confirmée (sinon elle est à relier, US-48), sans republication ou
        // « non publiée »
        aListingWithAnUnconfirmedDeletion();
        // Quand une vérification est demandée
        // Alors elle est refusée (NO_REPUBLICATION_TO_VERIFY) et Chrome n'est pas ouvert
        assertThatThrownBy(this::verify).isInstanceOf(NoRepublicationToVerify.class);
        republications.save(ListingRepublication.attempted(listingBackups.find(LISTING).orElseThrow(), CLICKED_AT,
                WriteOrigin.RUN)
                .notPublished(CLICKED_AT));
        assertThatThrownBy(this::verify).isInstanceOf(NoRepublicationToVerify.class);
        assertThat(wardrobe.reads()).isZero();
        assertThat(contents.tabs()).isZero();
        assertThat(session.overview().currentAction()).isEmpty();
    }

    @Test
    void listing_without_backup_is_refused() {
        assertThatThrownBy(this::verify).isInstanceOf(ListingNotBackedUp.class);
    }

    @Test
    void busy_browser_refuses_the_verification() {
        // Étant donné une autre action navigateur en cours
        ListingRepublication failed = aFailedRepublication();
        session.start(BrowserAction.SCANNING);
        // Quand une vérification est demandée
        // Alors elle est refusée (BROWSER_BUSY)
        assertThatThrownBy(this::verify).isInstanceOf(BrowserBusy.class);
        assertThat(wardrobe.reads()).isZero();
        assertThat(republications.findAll()).containsExactly(failed);
    }

    @Test
    void wardrobe_failure_leaves_the_republication_as_it_was() {
        // Étant donné une annonce en erreur de republication
        ListingRepublication failed = aFailedRepublication();
        // Et Vinted inaccessible
        wardrobe.whereVintedRefusesTheWardrobe();
        // Quand le membre clique sur « Vérifier sur Vinted »
        // Alors la vérification est refusée avec la raison, la republication reste en erreur et le verrou est libéré
        assertThatThrownBy(this::verify).isInstanceOf(VintedUnreachable.class);
        assertThat(republications.findAll()).containsExactly(failed);
        assertThat(session.overview().currentAction()).isEmpty();
    }

    @Test
    void browser_unavailable_leaves_the_republication_as_it_was() {
        ListingRepublication failed = aFailedRepublication();
        wardrobe.whereBrowserCannotStart();

        assertThatThrownBy(this::verify).isInstanceOf(BrowserUnavailable.class);
        assertThat(republications.findAll()).containsExactly(failed);
        assertThat(session.overview().currentAction()).isEmpty();
    }

    @Test
    void unreadable_new_listing_keeps_the_republication() {
        // Étant donné une republication en erreur dont la nouvelle annonce est retrouvée
        aFailedRepublication();
        wardrobe.holding(REPUBLISHED);
        // Et la page de la nouvelle annonce illisible
        contents.whereFormIsUnreadable(2, "no title");
        // Quand le membre clique sur « Vérifier sur Vinted »
        // Alors la vérification est refusée (LISTING_UNREADABLE) et la republication reste réussie
        assertThatThrownBy(this::verify).isInstanceOf(ListingUnreadable.class);
        assertThat(republications.findLatest(LISTING)).map(ListingRepublication::outcome)
                .contains(RepublicationOutcome.PUBLISHED);
        assertThat(session.overview().currentAction()).isEmpty();
    }

    @Test
    void vinted_unreachable_during_the_comparison_keeps_the_proven_republication() {
        aFailedRepublication();
        wardrobe.holding(REPUBLISHED);
        contents.whereVintedRefuses(2);

        assertThatThrownBy(this::verify).isInstanceOf(VintedUnreachable.class);
        assertThat(republications.findLatest(LISTING)).map(ListingRepublication::outcome)
                .contains(RepublicationOutcome.PUBLISHED);
        assertThat(listings.find(NEW_LISTING)).map(Listing::present).contains(true);
        assertThat(session.overview().currentAction()).isEmpty();
    }

    @Test
    void verification_that_settles_the_error_is_written() {
        // US-44 AT-44-01. Étant donné une republication en erreur et une seule nouvelle annonce correspondante
        aFailedRepublication();
        wardrobe.holding(REPUBLISHED);
        contents.showing(new ListingReading(sameContentAs(NEW_LISTING), List.of(aPhoto("a"), aPhoto("b"))));
        // Quand le membre clique sur « Vérifier sur Vinted »
        verify();
        // Alors le journal contient « Vérification de la republication : réussie »
        assertThat(journal.records()).extracting(ActionRecord::action, ActionRecord::outcome, ActionRecord::listingId,
                ActionRecord::reason).containsExactly(tuple(JournaledAction.REPUBLICATION_VERIFICATION,
                ActionOutcome.SUCCEEDED, Optional.of(LISTING), Optional.empty()));
    }

    @Test
    void verification_still_in_error_and_comparison_are_written() {
        // US-44 AT-44-02. Étant donné une republication en erreur sans nouvelle annonce correspondante
        aFailedRepublication();
        wardrobe.holding(aListing(3, "Robe bleue", "15.00", ACTIVE));
        // Quand le membre la vérifie
        verify();
        // Alors le journal contient « Vérification de la republication : dans le doute »
        assertThat(journal.records()).extracting(ActionRecord::action, ActionRecord::outcome, ActionRecord::reason)
                .containsExactly(tuple(JournaledAction.REPUBLICATION_VERIFICATION, ActionOutcome.IN_DOUBT,
                        Optional.empty()));
        // Étant donné une republication réussie, quand le membre la vérifie, alors « réussie »
        VerifyListingRepublicationAcceptanceTest published = new VerifyListingRepublicationAcceptanceTest();
        published.aPublishedRepublication();
        published.contents.showing(new ListingReading(sameContentAs(NEW_LISTING), List.of(aPhoto("a"))));
        published.verify();
        assertThat(published.journal.records()).extracting(ActionRecord::action, ActionRecord::outcome)
                .containsExactly(tuple(JournaledAction.REPUBLICATION_VERIFICATION, ActionOutcome.SUCCEEDED));
    }

    @Test
    void refused_and_failed_verifications_are_written() {
        // US-44 AT-44-03. Étant donné une annonce sans republication à vérifier (ni à relier, US-48)
        aListingWithAnUnconfirmedDeletion();
        // Quand le membre la vérifie
        assertThatThrownBy(this::verify).isInstanceOf(NoRepublicationToVerify.class);
        // Alors « refusée », raison « aucune republication à vérifier »
        assertThat(journal.records()).extracting(ActionRecord::outcome, ActionRecord::reason).containsExactly(
                tuple(ActionOutcome.REFUSED, Optional.of("NO_REPUBLICATION_TO_VERIFY")));
        // Étant donné une republication en erreur et le navigateur indisponible
        VerifyListingRepublicationAcceptanceTest unavailable = new VerifyListingRepublicationAcceptanceTest();
        unavailable.aFailedRepublication();
        unavailable.wardrobe.whereBrowserCannotStart();
        // Quand le membre la vérifie, alors « en échec », raison « navigateur indisponible »
        assertThatThrownBy(unavailable::verify).isInstanceOf(BrowserUnavailable.class);
        assertThat(unavailable.journal.records()).extracting(ActionRecord::action, ActionRecord::outcome,
                ActionRecord::reason).containsExactly(tuple(JournaledAction.REPUBLICATION_VERIFICATION,
                ActionOutcome.FAILED, Optional.of("BROWSER_UNAVAILABLE")));
        // Et une page de nouvelle annonce illisible donne la raison de l'API
        VerifyListingRepublicationAcceptanceTest unreadable = new VerifyListingRepublicationAcceptanceTest();
        unreadable.aPublishedRepublication();
        unreadable.contents.whereFormIsUnreadable(2, "no title");
        assertThatThrownBy(unreadable::verify).isInstanceOf(ListingUnreadable.class);
        assertThat(unreadable.journal.records()).extracting(ActionRecord::outcome, ActionRecord::reason)
                .containsExactly(tuple(ActionOutcome.FAILED, Optional.of("LISTING_UNREADABLE")));
    }

    @Test
    void proof_recorded_before_a_failed_comparison_is_written_succeeded() {
        // US-44 AT-44-11. Étant donné une republication en erreur dont la nouvelle annonce est retrouvée
        aFailedRepublication();
        wardrobe.holding(REPUBLISHED);
        // Et Vinted inaccessible au moment de la comparaison
        contents.whereVintedRefuses(2);
        // Quand le membre la vérifie
        // Alors la réponse reste l'échec de Vinted
        assertThatThrownBy(this::verify).isInstanceOf(VintedUnreachable.class);
        // Et le journal contient « Vérification de la republication : réussie », la preuve étant enregistrée
        assertThat(journal.records()).extracting(ActionRecord::action, ActionRecord::outcome, ActionRecord::reason)
                .containsExactly(tuple(JournaledAction.REPUBLICATION_VERIFICATION, ActionOutcome.SUCCEEDED,
                        Optional.empty()));
        // Mais une comparaison qui échoue sur une republication déjà réussie reste un échec
        VerifyListingRepublicationAcceptanceTest compared = new VerifyListingRepublicationAcceptanceTest();
        compared.aPublishedRepublication();
        compared.contents.whereVintedRefuses(2);
        assertThatThrownBy(compared::verify).isInstanceOf(VintedUnreachable.class);
        assertThat(compared.journal.records()).extracting(ActionRecord::outcome, ActionRecord::reason)
                .containsExactly(tuple(ActionOutcome.FAILED, Optional.of("VINTED_UNREACHABLE")));
    }

    @Test
    void listing_never_backed_up_writes_nothing() {
        assertThatThrownBy(this::verify).isInstanceOf(ListingNotBackedUp.class);
        assertThat(journal.records()).isEmpty();
    }

    @Test
    void journal_failure_changes_nothing() {
        // US-44 AT-44-08. Étant donné un journal impossible à écrire
        journalWriter = new JournalWriter(new InMemoryActionJournal().whereDatabaseIsDown(), clock);
        aFailedRepublication();
        wardrobe.holding(REPUBLISHED);
        contents.showing(new ListingReading(sameContentAs(NEW_LISTING), List.of(aPhoto("a"), aPhoto("b"))));
        // Quand le membre vérifie une republication en erreur qui se trouve publiée
        RepublicationReport report = verify();
        // Alors la réponse et la republication sont celles d'un journal qui marche
        assertThat(report.lastRepublication()).map(ListingRepublication::outcome)
                .contains(RepublicationOutcome.PUBLISHED);
        assertThat(report.comparison()).contains(List.of());
        assertThat(republications.findLatest(LISTING)).map(ListingRepublication::newListingId)
                .contains(Optional.of(NEW_LISTING));
    }

    private RepublicationReport verify() {
        return new VerifyListingRepublication(session, listingBackups, republications, deletions, listings,
                new NewListingSearch(wardrobe, noPause(), listings), contents, noBackupPause(), clock,
                show(), journalWriter).verify(LISTING);
    }

    private RepublishListing republishListing(FakeListingPublisher vinted) {
        RepublicationDelay delay = new RepublicationDelay(Duration.ofMinutes(5));
        return new RepublishListing(session,
                new RepublicationChecks(listingBackups, photos, validations, deletions, republications, delay, clock),
                new CadenceCheck(deletions, republications, new WriteCadence(Duration.ofMinutes(2), Duration.ofMinutes(2), 20, 20), clock),
                republications, vinted, new NewListingSearch(wardrobe, noPause(), listings), listings,
                RepublicationMode.REAL, clock, show(), new JournalWriter(new InMemoryActionJournal(), clock), (instant, interrupted) -> { }, Duration.ZERO);
    }

    private ShowListingRepublication show() {
        RepublicationDelay delay = new RepublicationDelay(Duration.ofMinutes(5));
        return new ShowListingRepublication(new ShowListingDeletion(listings, listingBackups, photos, validations,
                deletions, DeletionMode.REAL, delay), republications, listings, RepublicationMode.REAL);
    }

    private static ListingContent sameContentAs(VintedListingId id) {
        return new ListingContent(id, ROBE_BLEUE.title(), ROBE_BLEUE.description(), ROBE_BLEUE.price(),
                ROBE_BLEUE.category(), ROBE_BLEUE.brand(), ROBE_BLEUE.condition(), ROBE_BLEUE.size(),
                ROBE_BLEUE.colors(), ROBE_BLEUE.materials(), ROBE_BLEUE.packageSize(), ROBE_BLEUE.details());
    }

    /** Backed up with two photos, validated, deleted on Vinted a day ago: absent from the wardrobe. */
    private ListingBackup aDeletedListing() {
        listings.saveAll(List.of(Listing.seen(aListing(1, "Robe bleue", "12.00", ACTIVE), SAVED_AT).absent()));
        ListingReading reading = new ListingReading(ROBE_BLEUE, List.of(aPhoto("front"), aPhoto("back")));
        reading.photos().forEach(photo -> photos.store(LISTING, photo));
        ListingBackup backup = ListingBackup.of(reading, SAVED_AT);
        listingBackups.save(backup);
        validations.save(BackupValidation.of(backup, SAVED_AT.plusSeconds(60)));
        deletions.save(ListingDeletion.attempted(backup, CLICKED_AT.minusSeconds(3600), WriteOrigin.RUN)
                .confirmed(CLICKED_AT.minusSeconds(3595)));
        return backup;
    }

    /** Its last deletion waits to be confirmed: it is neither republished nor to link. */
    private void aListingWithAnUnconfirmedDeletion() {
        deletions.save(ListingDeletion.attempted(aDeletedListing(), CLICKED_AT.minusSeconds(60), WriteOrigin.RUN));
    }

    private ListingRepublication aFailedRepublication() {
        ListingRepublication failed = ListingRepublication.attempted(aDeletedListing(), CLICKED_AT, WriteOrigin.RUN);
        republications.save(failed);
        return failed;
    }

    private ListingRepublication aPublishedRepublication() {
        ListingRepublication published = aFailedRepublication().published(NEW_LISTING, CLICKED_AT.plusSeconds(5));
        republications.save(published);
        listings.saveAll(List.of(Listing.seen(REPUBLISHED, CLICKED_AT.plusSeconds(5))));
        return published;
    }
}
