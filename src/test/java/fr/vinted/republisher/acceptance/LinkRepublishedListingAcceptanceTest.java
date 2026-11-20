package fr.vinted.republisher.acceptance;

import fr.vinted.republisher.application.deletion.ShowListingDeletion;
import fr.vinted.republisher.application.inventory.ShowFailedListings;
import fr.vinted.republisher.application.inventory.ShowListingInventory;
import fr.vinted.republisher.application.journal.JournalWriter;
import fr.vinted.republisher.application.republication.NewListingSearch;
import fr.vinted.republisher.application.republication.ShowListingRepublication;
import fr.vinted.republisher.application.republication.ValidateListingRepublication;
import fr.vinted.republisher.application.republication.VerifyListingRepublication;
import fr.vinted.republisher.domain.backup.BackupValidation;
import fr.vinted.republisher.domain.backup.ListingBackup;
import fr.vinted.republisher.domain.backup.ListingContent;
import fr.vinted.republisher.domain.backup.ListingReading;
import fr.vinted.republisher.domain.backup.ListingUnreadable;
import fr.vinted.republisher.domain.deletion.DeletionMode;
import fr.vinted.republisher.domain.deletion.ListingDeletion;
import fr.vinted.republisher.domain.inventory.FailedListing;
import fr.vinted.republisher.domain.inventory.FailureKind;
import fr.vinted.republisher.domain.journal.ActionOutcome;
import fr.vinted.republisher.domain.journal.ActionRecord;
import fr.vinted.republisher.domain.journal.JournaledAction;
import fr.vinted.republisher.domain.republication.ListingRepublication;
import fr.vinted.republisher.domain.republication.NewListingLinkInvalid;
import fr.vinted.republisher.domain.republication.NewListingMismatch;
import fr.vinted.republisher.domain.republication.NewListingSearchResult;
import fr.vinted.republisher.domain.republication.NoRepublicationToVerify;
import fr.vinted.republisher.domain.republication.RepublicationDelay;
import fr.vinted.republisher.domain.republication.RepublicationMode;
import fr.vinted.republisher.domain.republication.RepublicationOutcome;
import fr.vinted.republisher.domain.republication.RepublicationReport;
import fr.vinted.republisher.domain.session.BrowserAction;
import fr.vinted.republisher.domain.session.BrowserBusy;
import fr.vinted.republisher.domain.session.VintedSession;
import fr.vinted.republisher.domain.session.VintedUnreachable;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.ScannedListing;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import fr.vinted.republisher.fakes.AdjustableClock;
import fr.vinted.republisher.fakes.FakeListingContentReader;
import fr.vinted.republisher.fakes.FakeWardrobeReader;
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

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static fr.vinted.republisher.domain.backup.BackupFixtures.aContent;
import static fr.vinted.republisher.domain.backup.BackupFixtures.aPhoto;
import static fr.vinted.republisher.domain.backup.BackupFixtures.noBackupPause;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.ACTIVE;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.aListing;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.noPause;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/** US-48 : relier une annonce supprimée, jamais republiée par l'outil, à sa nouvelle annonce déjà en ligne. */
class LinkRepublishedListingAcceptanceTest {

    private static final VintedListingId LISTING = new VintedListingId(1);
    private static final VintedListingId NEW_LISTING = new VintedListingId(2);
    private static final String NEW_LISTING_LINK = "https://www.vinted.fr/items/2-robe-bleue?referrer=catalog";
    private static final Instant SAVED_AT = Instant.parse("2026-11-10T10:00:00Z");
    private static final Instant DELETED_AT = Instant.parse("2026-11-11T09:00:00Z");
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

    @Test
    void single_candidate_links_the_new_listing() {
        // AT-48-01. Étant donné une annonce supprimée, refusée à la republication (FIELD_NOT_FILLABLE), sans
        // republication enregistrée
        ListingBackup backup = aRefusedListingToLink();
        // Et une seule annonce au titre et au prix de sa sauvegarde affichée à l'ouverture du dressing
        wardrobe.holding(REPUBLISHED, aListing(3, "Pull gris", "8.00", ACTIVE)).showingOnOpening(2);
        contents.showing(new ListingReading(sameContentAs(NEW_LISTING), List.of(aPhoto("a"), aPhoto("b"))));
        // Quand le membre demande « Vérifier sur Vinted »
        RepublicationReport report = verify();
        // Alors une republication réussie est enregistrée, datée de la vérification, avec la sauvegarde actuelle et
        // l'identifiant de la nouvelle annonce
        ListingRepublication linked = report.lastRepublication().orElseThrow();
        assertThat(linked.outcome()).isEqualTo(RepublicationOutcome.PUBLISHED);
        assertThat(linked.attemptedAt()).isEqualTo(NOW);
        assertThat(linked.publishedAt()).contains(NOW);
        assertThat(linked.backupSavedAt()).isEqualTo(backup.savedAt());
        assertThat(linked.newListingId()).contains(NEW_LISTING);
        // Et la nouvelle annonce est présente et reliée à l'ancienne, et comparée à la sauvegarde
        assertThat(listings.find(NEW_LISTING)).map(Listing::present).contains(true);
        assertThat(report.newListing()).map(Listing::id).contains(NEW_LISTING);
        assertThat(report.comparison()).contains(List.of());
        assertThat(report.newListingSearch()).isEmpty();
        // Et le journal a une ligne REPUBLICATION_VERIFICATION SUCCEEDED
        assertThat(lastRecord()).extracting(ActionRecord::action, ActionRecord::outcome, ActionRecord::reason)
                .containsExactly(JournaledAction.REPUBLICATION_VERIFICATION, ActionOutcome.SUCCEEDED, Optional.empty());
        // Et l'annonce quitte la page « Échecs »
        assertThat(showFailedListings().show()).isEmpty();
        assertThat(session.overview().currentAction()).isEmpty();
    }

    @Test
    void no_candidate_changes_nothing() {
        // AT-48-02. Étant donné une annonce à relier, refusée à la republication (FIELD_NOT_FILLABLE)
        aRefusedListingToLink();
        // Et aucune annonce à son titre et son prix à l'ouverture du dressing
        wardrobe.holding(aListing(3, "Pull gris", "8.00", ACTIVE)).showingOnOpening(1);
        // Quand le membre demande « Vérifier sur Vinted »
        RepublicationReport report = verify();
        // Alors aucune republication n'est enregistrée et l'annonce garde son étape
        assertThat(republications.findAll()).isEmpty();
        assertThat(report.lastRepublication()).isEmpty();
        assertThat(contents.opened()).isEmpty();
        // Et la réponse porte newListingSearch NOT_FOUND
        assertThat(report.newListingSearch()).contains(NewListingSearchResult.NOT_FOUND);
        // Et le journal a une ligne REPUBLICATION_VERIFICATION NOT_DONE (NEW_LISTING_NOT_FOUND)
        assertThat(lastRecord()).extracting(ActionRecord::action, ActionRecord::outcome, ActionRecord::reason)
                .containsExactly(JournaledAction.REPUBLICATION_VERIFICATION, ActionOutcome.NOT_DONE,
                        Optional.of("NEW_LISTING_NOT_FOUND"));
        // Et la page « Échecs » la montre toujours dans « Refusées », avec la cause FIELD_NOT_FILLABLE
        assertThat(showFailedListings().show()).extracting(FailedListing::kind,
                failed -> failed.failure().record().reason()).containsExactly(
                tuple(FailureKind.REFUSED, Optional.of("FIELD_NOT_FILLABLE")));
        assertThat(session.overview().currentAction()).isEmpty();
    }

    @Test
    void several_candidates_change_nothing() {
        // AT-48-03. Étant donné une annonce à relier
        aRefusedListingToLink();
        // Et deux annonces à son titre et son prix à l'ouverture du dressing
        wardrobe.holding(REPUBLISHED, aListing(3, "Robe bleue", "12.00", ACTIVE)).showingOnOpening(2);
        // Quand le membre demande « Vérifier sur Vinted »
        RepublicationReport report = verify();
        // Alors aucune republication n'est enregistrée
        assertThat(republications.findAll()).isEmpty();
        // Et la réponse porte newListingSearch SEVERAL
        assertThat(report.newListingSearch()).contains(NewListingSearchResult.SEVERAL);
        // Et le journal a une ligne REPUBLICATION_VERIFICATION NOT_DONE (SEVERAL_NEW_LISTINGS)
        assertThat(lastRecord()).extracting(ActionRecord::outcome, ActionRecord::reason)
                .containsExactly(ActionOutcome.NOT_DONE, Optional.of("SEVERAL_NEW_LISTINGS"));
    }

    @Test
    void published_link_links_the_new_listing() {
        // AT-48-04. Étant donné une annonce à relier
        ListingBackup backup = aRefusedListingToLink();
        contents.showing(new ListingReading(sameContentAs(NEW_LISTING), List.of(aPhoto("a"), aPhoto("b"))));
        // Quand le membre valide « Publiée » avec le lien d'une annonce au titre et au prix de la sauvegarde
        RepublicationReport report = validate().published(LISTING, Optional.of(NEW_LISTING_LINK));
        // Alors une republication réussie est enregistrée, datée de la validation, reliée et comparée
        ListingRepublication linked = report.lastRepublication().orElseThrow();
        assertThat(linked.outcome()).isEqualTo(RepublicationOutcome.PUBLISHED);
        assertThat(linked.attemptedAt()).isEqualTo(NOW);
        assertThat(linked.publishedAt()).contains(NOW);
        assertThat(linked.backupSavedAt()).isEqualTo(backup.savedAt());
        assertThat(linked.newListingId()).contains(NEW_LISTING);
        assertThat(contents.opened()).containsExactly(NEW_LISTING);
        assertThat(listings.find(NEW_LISTING)).map(Listing::present).contains(true);
        assertThat(report.comparison()).contains(List.of());
        // Et le journal a une ligne REPUBLICATION_VALIDATION SUCCEEDED
        assertThat(lastRecord()).extracting(ActionRecord::action, ActionRecord::outcome)
                .containsExactly(JournaledAction.REPUBLICATION_VALIDATION, ActionOutcome.SUCCEEDED);
        assertThat(showFailedListings().show()).isEmpty();
    }

    @Test
    void mismatching_or_invalid_link_is_refused() {
        // AT-48-05. Étant donné une annonce à relier
        aRefusedListingToLink();
        // Quand le membre valide « Publiée » avec le lien d'une annonce d'un autre titre ou prix
        contents.showing(new ListingReading(aContent(2, "Robe bleue", "Portée une fois", "15.00"),
                List.of(aPhoto("a"))));
        // Alors elle est refusée (NEW_LISTING_MISMATCH) et rien n'est enregistré
        assertThatThrownBy(() -> validate().published(LISTING, Optional.of(NEW_LISTING_LINK)))
                .isInstanceOf(NewListingMismatch.class);
        assertThat(republications.findAll()).isEmpty();
        assertThat(listings.find(NEW_LISTING)).isEmpty();
        // Et un lien sans identifiant d'annonce, ou celui de l'ancienne annonce, est refusé
        // (NEW_LISTING_LINK_INVALID) sans ouvrir Chrome
        assertThatThrownBy(() -> validate().published(LISTING, Optional.of("https://www.vinted.fr/member/42")))
                .isInstanceOf(NewListingLinkInvalid.class);
        assertThatThrownBy(() -> validate().published(LISTING, Optional.of("https://www.vinted.fr/items/1-robe")))
                .isInstanceOf(NewListingLinkInvalid.class);
        assertThat(contents.opened()).containsExactly(NEW_LISTING);
        assertThat(republications.findAll()).isEmpty();
    }

    @Test
    void not_published_is_refused_for_a_listing_to_link() {
        // AT-48-06. Étant donné une annonce à relier
        aRefusedListingToLink();
        // Quand le membre valide « Non publiée »
        // Alors elle est refusée (NO_REPUBLICATION_TO_VERIFY) et Chrome n'est pas ouvert
        assertThatThrownBy(() -> validate().notPublished(LISTING)).isInstanceOf(NoRepublicationToVerify.class);
        assertThat(republications.findAll()).isEmpty();
        assertThat(contents.opened()).isEmpty();
    }

    @Test
    void listing_not_to_link_is_refused_without_opening_chrome() {
        // AT-48-07. Étant donné une annonce non supprimée
        aBackedUpListing();
        // Quand le membre demande « Vérifier sur Vinted » ou valide « Publiée »
        // Alors elle est refusée (NO_REPUBLICATION_TO_VERIFY) et Chrome n'est pas ouvert
        assertThatThrownBy(this::verify).isInstanceOf(NoRepublicationToVerify.class);
        assertThatThrownBy(() -> validate().published(LISTING, Optional.of(NEW_LISTING_LINK)))
                .isInstanceOf(NoRepublicationToVerify.class);
        // De même, une annonce disparue de Vinted sans suppression par l'outil
        listings.saveAll(List.of(Listing.seen(aListing(1, "Robe bleue", "12.00", ACTIVE), SAVED_AT).absent()));
        assertThatThrownBy(this::verify).isInstanceOf(NoRepublicationToVerify.class);
        assertThatThrownBy(() -> validate().published(LISTING, Optional.of(NEW_LISTING_LINK)))
                .isInstanceOf(NoRepublicationToVerify.class);
        // Et une annonce déjà republiée ne se valide plus (sa vérification la compare, comme US-13)
        deletions.save(ListingDeletion.attempted(listingBackups.find(LISTING).orElseThrow(), DELETED_AT)
                .confirmed(DELETED_AT.plusSeconds(5)));
        republications.save(ListingRepublication.linked(listingBackups.find(LISTING).orElseThrow(),
                new VintedListingId(3), DELETED_AT.plusSeconds(600)));
        assertThatThrownBy(() -> validate().published(LISTING, Optional.of(NEW_LISTING_LINK)))
                .isInstanceOf(NoRepublicationToVerify.class);
        assertThat(wardrobe.opened()).isEmpty();
        assertThat(wardrobe.reads()).isZero();
        assertThat(contents.opened()).isEmpty();
    }

    @Test
    void browser_errors_record_nothing() {
        // AT-48-08. Étant donné une annonce à relier
        aRefusedListingToLink();
        // Quand la vérification ou la validation échoue (Vinted inaccessible, navigateur occupé, page illisible)
        wardrobe.whereVintedRefusesTheWardrobe();
        assertThatThrownBy(this::verify).isInstanceOf(VintedUnreachable.class);
        session.start(BrowserAction.SCANNING);
        assertThatThrownBy(this::verify).isInstanceOf(BrowserBusy.class);
        assertThatThrownBy(() -> validate().published(LISTING, Optional.of(NEW_LISTING_LINK)))
                .isInstanceOf(BrowserBusy.class);
        session.finish();
        contents.whereFormIsUnreadable(2, "no title");
        assertThatThrownBy(() -> validate().published(LISTING, Optional.of(NEW_LISTING_LINK)))
                .isInstanceOf(ListingUnreadable.class);
        // Alors elle est refusée avec la raison, rien n'est enregistré dans les republications et le verrou est libéré
        assertThat(republications.findAll()).isEmpty();
        assertThat(listings.find(NEW_LISTING)).isEmpty();
        assertThat(session.overview().currentAction()).isEmpty();
        assertThat(journal.records()).extracting(ActionRecord::outcome, ActionRecord::reason).endsWith(
                tuple(ActionOutcome.FAILED, Optional.of("VINTED_UNREACHABLE")),
                tuple(ActionOutcome.REFUSED, Optional.of("BROWSER_BUSY")),
                tuple(ActionOutcome.REFUSED, Optional.of("BROWSER_BUSY")),
                tuple(ActionOutcome.FAILED, Optional.of("LISTING_UNREADABLE")));
    }

    private RepublicationReport verify() {
        return new VerifyListingRepublication(session, listingBackups, republications, deletions, listings,
                new NewListingSearch(wardrobe, noPause(), listings), contents, noBackupPause(), clock, show(),
                new JournalWriter(journal, clock)).verify(LISTING);
    }

    private ValidateListingRepublication validate() {
        return new ValidateListingRepublication(session, listingBackups, republications, deletions, listings,
                contents, noBackupPause(), clock, show(), URI.create("https://www.vinted.fr/member/42-cyril"),
                new JournalWriter(journal, clock));
    }

    private ShowListingRepublication show() {
        RepublicationDelay delay = new RepublicationDelay(Duration.ofMinutes(5));
        return new ShowListingRepublication(new ShowListingDeletion(listings, listingBackups, photos, validations,
                deletions, DeletionMode.REAL, delay), republications, listings, RepublicationMode.REAL);
    }

    private ShowFailedListings showFailedListings() {
        return new ShowFailedListings(journal, new ShowListingInventory(listings,
                new InMemoryListingStatisticsRepository(), listingBackups, validations, deletions, republications,
                new RepublicationDelay(Duration.ofMinutes(5)), clock, new InMemoryListingTagRepository(),
                new InMemoryBackupVerificationRepository()));
    }

    private ActionRecord lastRecord() {
        return journal.records().getLast();
    }

    private static ListingContent sameContentAs(VintedListingId id) {
        return new ListingContent(id, ROBE_BLEUE.title(), ROBE_BLEUE.description(), ROBE_BLEUE.price(),
                ROBE_BLEUE.category(), ROBE_BLEUE.brand(), ROBE_BLEUE.condition(), ROBE_BLEUE.size(),
                ROBE_BLEUE.colors(), ROBE_BLEUE.materials(), ROBE_BLEUE.packageSize(), ROBE_BLEUE.details());
    }

    /** Backed up with two photos and validated, still on Vinted. */
    private ListingBackup aBackedUpListing() {
        listings.saveAll(List.of(Listing.seen(aListing(1, "Robe bleue", "12.00", ACTIVE), SAVED_AT)));
        ListingReading reading = new ListingReading(ROBE_BLEUE, List.of(aPhoto("front"), aPhoto("back")));
        reading.photos().forEach(photo -> photos.store(LISTING, photo));
        ListingBackup backup = ListingBackup.of(reading, SAVED_AT);
        listingBackups.save(backup);
        validations.save(BackupValidation.of(backup, SAVED_AT.plusSeconds(60)));
        return backup;
    }

    /** Deleted from Vinted a day ago, then refused at each republication: a field could not be filled. */
    private ListingBackup aRefusedListingToLink() {
        ListingBackup backup = aBackedUpListing();
        listings.saveAll(List.of(Listing.seen(aListing(1, "Robe bleue", "12.00", ACTIVE), SAVED_AT).absent()));
        deletions.save(ListingDeletion.attempted(backup, DELETED_AT).confirmed(DELETED_AT.plusSeconds(5)));
        journal.write(new ActionRecord(DELETED_AT.plusSeconds(600), JournaledAction.REPUBLICATION,
                ActionOutcome.REFUSED, Optional.of(LISTING), Optional.of("FIELD_NOT_FILLABLE")));
        return backup;
    }
}
