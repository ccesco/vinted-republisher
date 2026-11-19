package fr.vinted.republisher.acceptance;

import fr.vinted.republisher.application.cadence.CadenceCheck;
import fr.vinted.republisher.application.deletion.ShowListingDeletion;
import fr.vinted.republisher.application.inventory.ShowFailedListings;
import fr.vinted.republisher.application.inventory.ShowListingInventory;
import fr.vinted.republisher.application.journal.JournalWriter;
import fr.vinted.republisher.application.republication.PrepareManualRepublication;
import fr.vinted.republisher.application.republication.RepublicationChecks;
import fr.vinted.republisher.application.republication.ShowListingRepublication;
import fr.vinted.republisher.domain.backup.BackupValidation;
import fr.vinted.republisher.domain.backup.ListingBackup;
import fr.vinted.republisher.domain.backup.ListingContent;
import fr.vinted.republisher.domain.backup.ListingFormField;
import fr.vinted.republisher.domain.backup.ListingReading;
import fr.vinted.republisher.domain.cadence.CadenceLimitReached;
import fr.vinted.republisher.domain.cadence.WriteCadence;
import fr.vinted.republisher.domain.deletion.DeletionMode;
import fr.vinted.republisher.domain.deletion.ListingDeletion;
import fr.vinted.republisher.domain.inventory.FailureKind;
import fr.vinted.republisher.domain.inventory.ListingStage;
import fr.vinted.republisher.domain.journal.ActionOutcome;
import fr.vinted.republisher.domain.journal.ActionRecord;
import fr.vinted.republisher.domain.journal.JournaledAction;
import fr.vinted.republisher.domain.republication.ListingNotDeleted;
import fr.vinted.republisher.domain.republication.ListingRepublication;
import fr.vinted.republisher.domain.republication.ManualRepublicationInDryRun;
import fr.vinted.republisher.domain.republication.ManualRepublicationReport;
import fr.vinted.republisher.domain.republication.RepublicationDelay;
import fr.vinted.republisher.domain.republication.RepublicationMode;
import fr.vinted.republisher.domain.republication.RepublicationOutcome;
import fr.vinted.republisher.domain.republication.RepublicationTooEarly;
import fr.vinted.republisher.domain.republication.UnfilledField;
import fr.vinted.republisher.domain.session.BrowserAction;
import fr.vinted.republisher.domain.session.BrowserBusy;
import fr.vinted.republisher.domain.session.SessionDisconnected;
import fr.vinted.republisher.domain.session.VintedSession;
import fr.vinted.republisher.domain.backup.ListingNotBackedUp;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import fr.vinted.republisher.fakes.AdjustableClock;
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
import fr.vinted.republisher.fakes.InMemoryListingTagRepository;
import fr.vinted.republisher.fakes.InMemoryPhotoStorage;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static fr.vinted.republisher.domain.backup.BackupFixtures.aContent;
import static fr.vinted.republisher.domain.backup.BackupFixtures.aPhoto;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.ACTIVE;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.aListing;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.tuple;

/** US-45: the tool fills the listing form from the backup, the member completes it and publishes it. */
class PrepareManualRepublicationAcceptanceTest {

    private static final VintedListingId LISTING = new VintedListingId(1);
    private static final Instant SAVED_AT = Instant.parse("2026-11-10T10:00:00Z");
    private static final Instant VALIDATED_AT = Instant.parse("2026-11-10T11:00:00Z");
    private static final Instant NOW = Instant.parse("2026-11-11T10:00:00Z");
    private static final ListingContent ROBE_BLEUE = aContent(1, "Robe bleue", "Portée une fois", "12.00");
    private static final RepublicationDelay DELAY = new RepublicationDelay(Duration.ofMinutes(5));

    private final InMemoryActionJournal journal = new InMemoryActionJournal();
    private final InMemoryListingRepository listings = new InMemoryListingRepository();
    private final InMemoryListingBackupRepository listingBackups = new InMemoryListingBackupRepository();
    private final InMemoryPhotoStorage photos = new InMemoryPhotoStorage();
    private final InMemoryBackupValidationRepository validations = new InMemoryBackupValidationRepository();
    private final InMemoryListingDeletionRepository deletions = new InMemoryListingDeletionRepository();
    private final InMemoryListingRepublicationRepository republications = new InMemoryListingRepublicationRepository();
    private final FakeListingPublisher vinted = new FakeListingPublisher(new FakeWardrobeReader());
    private final VintedSession session = new VintedSession();
    private final AdjustableClock clock = new AdjustableClock(NOW);

    @Test
    void fills_the_form_leaves_unfillable_fields_empty_and_never_publishes() {
        // Étant donné une annonce à republier dont la taille (identifiant 588) n'existe plus sur Vinted
        aDeletedListing(Duration.ofMinutes(6));
        vinted.whereSizeCannotBeFilled();

        // Quand le membre la republie manuellement
        ManualRepublicationReport report = prepare(RepublicationMode.REAL).prepare(LISTING, SAVED_AT);

        // Alors le formulaire « Vendre » est rempli depuis la sauvegarde dans un nouvel onglet, la taille laissée vide
        assertThat(vinted.filledContent()).contains(ROBE_BLEUE);
        assertThat(vinted.sentPhotos()).containsExactly(aPhoto("front"), aPhoto("back"));
        // Et « Publier » n'est jamais cliqué, l'onglet reste ouvert
        assertThat(vinted.publishClicks()).isZero();
        assertThat(vinted.tabsLeftOpen()).isEqualTo(1);
        // Et la réponse liste la taille (option 588) parmi les champs à compléter
        assertThat(report.unfilledFields()).containsExactly(new UnfilledField(ListingFormField.SIZE, Optional.of(588L)));
        // Et une republication en erreur est enregistrée, l'annonce est « Republication à vérifier »
        ListingRepublication republication = republications.findLatest(LISTING).orElseThrow();
        assertThat(republication.outcome()).isEqualTo(RepublicationOutcome.FAILED);
        assertThat(republication.attemptedAt()).isEqualTo(NOW);
        assertThat(report.republication().lastRepublication()).contains(republication);
        assertThat(session.overview().currentAction()).isEmpty();
        // Et le journal a une ligne « republication » en doute « republication manuelle : publiez sur Vinted puis
        //   validez »
        assertThat(journal.records()).extracting(ActionRecord::action, ActionRecord::outcome, ActionRecord::reason)
                .containsExactly(tuple(JournaledAction.REPUBLICATION, ActionOutcome.IN_DOUBT,
                        Optional.of("MANUAL_REPUBLICATION_PREPARED")));
        // Et l'annonce est dans la section « Dans le doute » de la page « Échecs »
        assertThat(showFailedListings().show()).singleElement().satisfies(failed -> {
            assertThat(failed.kind()).isEqualTo(FailureKind.IN_DOUBT);
            assertThat(failed.stage()).isEqualTo(ListingStage.REPUBLICATION_TO_CHECK);
        });
        // Et la fiche propose de valider la republication « publiée » ou « non publiée » (republication en erreur)
        assertThatCode(republication::requireFailed).doesNotThrowAnyException();
    }

    @Test
    void refused_like_a_republication() {
        // Étant donné une annonce dont la date de republication n'est pas atteinte
        aDeletedListing(Duration.ofMinutes(2));
        // Quand le membre la republie manuellement
        // Alors c'est refusé avec la raison de la republication et Chrome n'ouvre aucun formulaire
        assertRefusedBeforeChrome(RepublicationTooEarly.class, RepublicationMode.REAL);
        assertThat(journal.records()).extracting(ActionRecord::outcome, ActionRecord::reason)
                .containsExactly(tuple(ActionOutcome.REFUSED, Optional.of("TOO_EARLY")));
    }

    @Test
    void refused_when_the_old_listing_is_not_deleted() {
        // Ou dont l'ancienne annonce n'est pas supprimée
        aValidatedListing();
        assertRefusedBeforeChrome(ListingNotDeleted.class, RepublicationMode.REAL);
    }

    @Test
    void refused_while_the_browser_is_busy() {
        // Ou alors que le navigateur est occupé
        aDeletedListing(Duration.ofMinutes(6));
        session.start(BrowserAction.SCANNING);
        assertThat(catchThrowable(() -> prepare(RepublicationMode.REAL).prepare(LISTING, SAVED_AT)))
                .isInstanceOf(BrowserBusy.class);
        assertThat(vinted.formsOpened()).isZero();
        assertThat(republications.findAll()).isEmpty();
    }

    @Test
    void counts_in_cadence() {
        // Étant donné la limite de cadence atteinte
        aDeletedListing(Duration.ofMinutes(6));
        ListingBackup other = ListingBackup.of(new ListingReading(aContent(9, "Pull", "Laine", "8.00"),
                List.of(aPhoto("pull"))), SAVED_AT);
        republications.save(ListingRepublication.attempted(other, NOW.minusSeconds(30)));
        // Quand le membre republie manuellement une annonce à republier
        // Alors c'est refusé (CADENCE_LIMIT_REACHED) et Chrome n'ouvre aucun formulaire
        assertThat(catchThrowable(() -> prepare(RepublicationMode.REAL).prepare(LISTING, SAVED_AT)))
                .isInstanceOf(CadenceLimitReached.class);
        assertThat(vinted.formsOpened()).isZero();
        assertThat(republications.findLatest(LISTING)).isEmpty();
    }

    @Test
    void failure_of_the_form_records_nothing() {
        // Page du formulaire illisible ou session non connectée : rien n'est enregistré, l'échec est journalisé
        aDeletedListing(Duration.ofMinutes(6));
        vinted.whereSessionIsDisconnected();
        assertThat(catchThrowable(() -> prepare(RepublicationMode.REAL).prepare(LISTING, SAVED_AT)))
                .isInstanceOf(SessionDisconnected.class);
        assertThat(republications.findAll()).isEmpty();
        assertThat(session.overview().currentAction()).isEmpty();
        assertThat(journal.records()).extracting(ActionRecord::outcome, ActionRecord::reason)
                .containsExactly(tuple(ActionOutcome.FAILED, Optional.of("SESSION_DISCONNECTED")));
    }

    @Test
    void listing_never_backed_up_is_not_journaled_even_in_dry_run() {
        assertThat(catchThrowable(() -> prepare(RepublicationMode.DRY_RUN).prepare(LISTING, SAVED_AT)))
                .isInstanceOf(ListingNotBackedUp.class);
        assertThat(journal.records()).isEmpty();
    }

    @Test
    void refused_in_dry_run() {
        // Étant donné le dry-run de republication actif
        aDeletedListing(Duration.ofMinutes(6));
        // Quand le membre republie manuellement une annonce à republier
        // Alors c'est refusé (MANUAL_REPUBLICATION_IN_DRY_RUN), rien n'est enregistré
        assertRefusedBeforeChrome(ManualRepublicationInDryRun.class, RepublicationMode.DRY_RUN);
        assertThat(journal.records()).extracting(ActionRecord::outcome, ActionRecord::reason)
                .containsExactly(tuple(ActionOutcome.REFUSED, Optional.of("MANUAL_REPUBLICATION_IN_DRY_RUN")));
    }

    private void assertRefusedBeforeChrome(Class<? extends RuntimeException> refusal, RepublicationMode mode) {
        assertThat(catchThrowable(() -> prepare(mode).prepare(LISTING, SAVED_AT))).isInstanceOf(refusal);
        assertThat(vinted.formsOpened()).isZero();
        assertThat(republications.findAll()).isEmpty();
        assertThat(session.overview().currentAction()).isEmpty();
    }

    private PrepareManualRepublication prepare(RepublicationMode mode) {
        ShowListingRepublication show = new ShowListingRepublication(new ShowListingDeletion(listings,
                listingBackups, photos, validations, deletions, DeletionMode.REAL, DELAY), republications, listings,
                mode);
        return new PrepareManualRepublication(session,
                new RepublicationChecks(listingBackups, photos, validations, deletions, republications, DELAY, clock),
                new CadenceCheck(deletions, republications,
                        new WriteCadence(Duration.ofMinutes(2), Duration.ofMinutes(2), 2, 2), clock),
                republications, vinted, mode, clock, show, new JournalWriter(journal, clock));
    }

    private ShowFailedListings showFailedListings() {
        return new ShowFailedListings(journal, new ShowListingInventory(listings,
                new InMemoryListingStatisticsRepository(), listingBackups, validations, deletions, republications,
                DELAY, clock, new InMemoryListingTagRepository(), new InMemoryBackupVerificationRepository()));
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
    private void aDeletedListing(Duration ago) {
        ListingBackup backup = aValidatedListing();
        Instant provenAt = NOW.minus(ago);
        deletions.save(ListingDeletion.attempted(backup, provenAt.minusSeconds(5)).confirmed(provenAt));
    }
}
