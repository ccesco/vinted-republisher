package fr.vinted.republisher.acceptance;

import fr.vinted.republisher.application.deletion.ShowListingDeletion;
import fr.vinted.republisher.application.republication.ShowListingRepublication;
import fr.vinted.republisher.application.republication.ValidateListingRepublication;
import fr.vinted.republisher.domain.backup.BackupValidation;
import fr.vinted.republisher.domain.backup.ListingBackup;
import fr.vinted.republisher.domain.backup.ListingContent;
import fr.vinted.republisher.domain.backup.ListingReading;
import fr.vinted.republisher.domain.backup.ListingUnreadable;
import fr.vinted.republisher.domain.deletion.DeletionMode;
import fr.vinted.republisher.domain.deletion.ListingDeletion;
import fr.vinted.republisher.domain.republication.ListingRepublication;
import fr.vinted.republisher.domain.republication.NewListingLinkInvalid;
import fr.vinted.republisher.domain.republication.NewListingMismatch;
import fr.vinted.republisher.domain.republication.NoRepublicationToVerify;
import fr.vinted.republisher.domain.republication.RepublicationDelay;
import fr.vinted.republisher.domain.republication.RepublicationMode;
import fr.vinted.republisher.domain.republication.RepublicationOutcome;
import fr.vinted.republisher.domain.republication.RepublicationReport;
import fr.vinted.republisher.domain.session.BrowserAction;
import fr.vinted.republisher.domain.session.BrowserBusy;
import fr.vinted.republisher.domain.session.VintedSession;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import fr.vinted.republisher.fakes.AdjustableClock;
import fr.vinted.republisher.fakes.FakeListingContentReader;
import fr.vinted.republisher.fakes.InMemoryBackupValidationRepository;
import fr.vinted.republisher.fakes.InMemoryListingBackupRepository;
import fr.vinted.republisher.fakes.InMemoryListingDeletionRepository;
import fr.vinted.republisher.fakes.InMemoryListingRepository;
import fr.vinted.republisher.fakes.InMemoryListingRepublicationRepository;
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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidateListingRepublicationAcceptanceTest {

    private static final VintedListingId LISTING = new VintedListingId(1);
    private static final VintedListingId NEW_LISTING = new VintedListingId(2);
    private static final String NEW_LISTING_LINK = "https://www.vinted.fr/items/2-robe-bleue?referrer=catalog";
    private static final Instant SAVED_AT = Instant.parse("2026-11-10T10:00:00Z");
    private static final Instant CLICKED_AT = Instant.parse("2026-11-11T10:06:00Z");
    private static final Instant NOW = Instant.parse("2026-11-12T09:00:00Z");
    private static final ListingContent ROBE_BLEUE = aContent(1, "Robe bleue", "Portée une fois", "12.00");

    private final InMemoryListingRepository listings = new InMemoryListingRepository();
    private final InMemoryListingBackupRepository listingBackups = new InMemoryListingBackupRepository();
    private final InMemoryPhotoStorage photos = new InMemoryPhotoStorage();
    private final InMemoryBackupValidationRepository validations = new InMemoryBackupValidationRepository();
    private final InMemoryListingDeletionRepository deletions = new InMemoryListingDeletionRepository();
    private final InMemoryListingRepublicationRepository republications = new InMemoryListingRepublicationRepository();
    private final FakeListingContentReader contents = new FakeListingContentReader();
    private final VintedSession session = new VintedSession();
    private final AdjustableClock clock = new AdjustableClock(NOW);

    @Test
    void published_with_the_link_of_the_new_listing() {
        // Étant donné une annonce « Robe bleue » à 12,00 € en erreur de republication
        ListingRepublication failed = aFailedRepublication();
        contents.showing(new ListingReading(sameContentAs(NEW_LISTING), List.of(aPhoto("a"), aPhoto("b"))));
        // Quand le membre la valide « publiée » avec le lien https://www.vinted.fr/items/2-robe-bleue?referrer=catalog
        RepublicationReport report = validate().published(LISTING, Optional.of(NEW_LISTING_LINK));
        // Alors l'outil ouvre la page de l'annonce 2, et y lit « Robe bleue » à 12,00 €
        assertThat(contents.opened()).containsExactly(NEW_LISTING);
        // Et la republication est réussie, avec l'identifiant 2 et la date de la validation
        ListingRepublication republication = report.lastRepublication().orElseThrow();
        assertThat(republication.id()).isEqualTo(failed.id());
        assertThat(republication.outcome()).isEqualTo(RepublicationOutcome.PUBLISHED);
        assertThat(republication.newListingId()).contains(NEW_LISTING);
        assertThat(republication.publishedAt()).contains(NOW);
        // Et la nouvelle annonce est présente, reliée à l'ancienne, et comparée à la sauvegarde
        Listing newListing = listings.find(NEW_LISTING).orElseThrow();
        assertThat(newListing.present()).isTrue();
        assertThat(newListing.title()).isEqualTo("Robe bleue");
        assertThat(newListing.url()).isEqualTo(URI.create("https://www.vinted.fr/items/2-robe-bleue"));
        assertThat(report.newListing()).map(Listing::id).contains(NEW_LISTING);
        assertThat(report.comparison()).contains(List.of());
        assertThat(session.overview().currentAction()).isEmpty();
    }

    @Test
    void link_to_a_listing_with_another_title_or_price_is_refused() {
        // Étant donné une annonce « Robe bleue » à 12,00 € en erreur de republication
        ListingRepublication failed = aFailedRepublication();
        // Quand le membre la valide « publiée » avec le lien d'une annonce « Robe bleue » à 15,00 €
        contents.showing(new ListingReading(aContent(2, "Robe bleue", "Portée une fois", "15.00"),
                List.of(aPhoto("a"))));
        // Alors elle est refusée (NEW_LISTING_MISMATCH), la republication reste en erreur et rien n'est enregistré
        assertThatThrownBy(() -> validate().published(LISTING, Optional.of(NEW_LISTING_LINK)))
                .isInstanceOf(NewListingMismatch.class);
        assertThat(republications.findAll()).containsExactly(failed);
        assertThat(listings.find(NEW_LISTING)).isEmpty();
        assertThat(session.overview().currentAction()).isEmpty();
    }

    @Test
    void invalid_link_is_refused_without_opening_chrome() {
        // Étant donné une annonce en erreur de republication, et une autre déjà republiée en annonce 7
        ListingRepublication failed = aFailedRepublication();
        republications.save(ListingRepublication.attempted(aDeletedListing(9), CLICKED_AT)
                .published(new VintedListingId(7), CLICKED_AT.plusSeconds(5)));
        // Quand le membre la valide « publiée » sans lien, avec un lien sans identifiant d'annonce, avec le lien de
        // l'ancienne annonce ou de la nouvelle annonce d'une autre republication
        // Alors elle est refusée (NEW_LISTING_LINK_INVALID) et Chrome n'est pas ouvert
        List.of(Optional.<String>empty(), Optional.of("https://www.vinted.fr/member/42-cyril"),
                        Optional.of("pas un lien"), Optional.of("https://evil.example/items/2-robe-bleue"),
                        Optional.of("https://www.vinted.fr/items/1-robe-bleue"),
                        Optional.of("https://www.vinted.fr/items/7-pull"))
                .forEach(link -> assertThatThrownBy(() -> validate().published(LISTING, link))
                        .as(link.toString()).isInstanceOf(NewListingLinkInvalid.class));
        assertThat(contents.opened()).isEmpty();
        assertThat(republications.findLatest(LISTING)).contains(failed);
    }

    @Test
    void not_published_allows_a_new_attempt_without_opening_chrome() {
        // Étant donné une annonce en erreur de republication
        aFailedRepublication();
        // Quand le membre la valide « non publiée »
        RepublicationReport report = validate().notPublished(LISTING);
        // Alors la republication est « non publiée », avec la date de la validation, sans ouvrir Chrome
        ListingRepublication republication = report.lastRepublication().orElseThrow();
        assertThat(republication.outcome()).isEqualTo(RepublicationOutcome.NOT_PUBLISHED);
        assertThat(republication.checkedAt()).contains(NOW);
        assertThat(contents.opened()).isEmpty();
        assertThat(report.comparison()).isEmpty();
        // Et « Republier sur Vinted » redevient possible
        republication.allowNewAttempt();
    }

    @Test
    void only_a_republication_in_error_is_validated() {
        // Étant donné une annonce sans republication
        aDeletedListing(1);
        // Quand une validation manuelle est demandée
        // Alors elle est refusée (NO_REPUBLICATION_TO_VERIFY) et Chrome n'est pas ouvert
        assertThatThrownBy(() -> validate().notPublished(LISTING)).isInstanceOf(NoRepublicationToVerify.class);
        // De même, réussie ou « non publiée »
        republications.save(ListingRepublication.attempted(listingBackups.find(LISTING).orElseThrow(), CLICKED_AT)
                .published(NEW_LISTING, CLICKED_AT.plusSeconds(5)));
        assertThatThrownBy(() -> validate().published(LISTING, Optional.of("https://www.vinted.fr/items/3-robe")))
                .isInstanceOf(NoRepublicationToVerify.class);
        republications.save(ListingRepublication.attempted(listingBackups.find(LISTING).orElseThrow(), NOW)
                .notPublished(NOW));
        assertThatThrownBy(() -> validate().notPublished(LISTING)).isInstanceOf(NoRepublicationToVerify.class);
        assertThat(contents.opened()).isEmpty();
    }

    @Test
    void browser_errors_leave_the_republication_failed() {
        // Étant donné une annonce en erreur de republication
        ListingRepublication failed = aFailedRepublication();
        // Quand le membre la valide « publiée » alors qu'une autre action navigateur est en cours
        session.start(BrowserAction.SCANNING);
        assertThatThrownBy(() -> validate().published(LISTING, Optional.of(NEW_LISTING_LINK)))
                .isInstanceOf(BrowserBusy.class);
        assertThat(contents.opened()).isEmpty();
        session.finish();
        // Ou que la page de la nouvelle annonce est illisible
        contents.whereFormIsUnreadable(2, "no title");
        assertThatThrownBy(() -> validate().published(LISTING, Optional.of(NEW_LISTING_LINK)))
                .isInstanceOf(ListingUnreadable.class);
        // Alors la republication reste en erreur et le verrou est libéré
        assertThat(republications.findAll()).containsExactly(failed);
        assertThat(listings.find(NEW_LISTING)).isEmpty();
        assertThat(session.overview().currentAction()).isEmpty();
    }

    private ValidateListingRepublication validate() {
        RepublicationDelay delay = new RepublicationDelay(Duration.ofMinutes(5));
        ShowListingRepublication show = new ShowListingRepublication(new ShowListingDeletion(listings,
                listingBackups, photos, validations, deletions, DeletionMode.REAL, delay), republications, listings,
                RepublicationMode.REAL);
        return new ValidateListingRepublication(session, listingBackups, republications, listings, contents,
                noBackupPause(), clock, show, URI.create("https://www.vinted.fr/member/42-cyril"));
    }

    private static ListingContent sameContentAs(VintedListingId id) {
        return new ListingContent(id, ROBE_BLEUE.title(), ROBE_BLEUE.description(), ROBE_BLEUE.price(),
                ROBE_BLEUE.category(), ROBE_BLEUE.brand(), ROBE_BLEUE.condition(), ROBE_BLEUE.size(),
                ROBE_BLEUE.colors(), ROBE_BLEUE.materials(), ROBE_BLEUE.packageSize(), ROBE_BLEUE.details());
    }

    /** Backed up with two photos, validated, deleted on Vinted a day ago: absent from the wardrobe. */
    private ListingBackup aDeletedListing(long id) {
        VintedListingId listing = new VintedListingId(id);
        listings.saveAll(List.of(Listing.seen(aListing(id, "Robe bleue", "12.00", ACTIVE), SAVED_AT).absent()));
        ListingReading reading = new ListingReading(sameContentAs(listing), List.of(aPhoto("front"), aPhoto("back")));
        reading.photos().forEach(photo -> photos.store(listing, photo));
        ListingBackup backup = ListingBackup.of(reading, SAVED_AT);
        listingBackups.save(backup);
        validations.save(BackupValidation.of(backup, SAVED_AT.plusSeconds(60)));
        deletions.save(ListingDeletion.attempted(backup, CLICKED_AT.minusSeconds(3600))
                .confirmed(CLICKED_AT.minusSeconds(3595)));
        return backup;
    }

    private ListingRepublication aFailedRepublication() {
        ListingRepublication failed = ListingRepublication.attempted(aDeletedListing(1), CLICKED_AT);
        republications.save(failed);
        return failed;
    }
}
