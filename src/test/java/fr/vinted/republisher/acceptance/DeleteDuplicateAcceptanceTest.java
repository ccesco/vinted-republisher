package fr.vinted.republisher.acceptance;

import fr.vinted.republisher.application.cadence.CadenceCheck;
import fr.vinted.republisher.application.duplicate.DeleteDuplicate;
import fr.vinted.republisher.application.duplicate.DuplicateDeletionReport;
import fr.vinted.republisher.application.duplicate.DuplicatesView;
import fr.vinted.republisher.application.duplicate.ShowDuplicates;
import fr.vinted.republisher.application.journal.JournalWriter;
import fr.vinted.republisher.domain.backup.ListingBackup;
import fr.vinted.republisher.domain.backup.ListingReading;
import fr.vinted.republisher.domain.cadence.VintedWrite;
import fr.vinted.republisher.domain.cadence.WriteCadence;
import fr.vinted.republisher.domain.cadence.WriteOrigin;
import fr.vinted.republisher.domain.deletion.DeletionMode;
import fr.vinted.republisher.domain.deletion.ListingChanged;
import fr.vinted.republisher.domain.deletion.ListingDeletion;
import fr.vinted.republisher.domain.deletion.ListingDifference.PriceChanged;
import fr.vinted.republisher.domain.duplicate.DuplicateDeletionOutcome;
import fr.vinted.republisher.domain.duplicate.ListingHasHistory;
import fr.vinted.republisher.domain.duplicate.NotADuplicate;
import fr.vinted.republisher.domain.duplicate.TwinNotConfirmed;
import fr.vinted.republisher.domain.inventory.ListingNotFound;
import fr.vinted.republisher.domain.journal.ActionOutcome;
import fr.vinted.republisher.domain.journal.ActionRecord;
import fr.vinted.republisher.domain.journal.JournaledAction;
import fr.vinted.republisher.domain.republication.ListingRepublication;
import fr.vinted.republisher.domain.sale.ListingSale;
import fr.vinted.republisher.domain.session.BrowserAction;
import fr.vinted.republisher.domain.session.BrowserBusy;
import fr.vinted.republisher.domain.session.SessionDisconnected;
import fr.vinted.republisher.domain.session.VintedSession;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.Price;
import fr.vinted.republisher.domain.wardrobe.ScannedListing;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import fr.vinted.republisher.fakes.AdjustableClock;
import fr.vinted.republisher.fakes.FakeVintedPages;
import fr.vinted.republisher.fakes.InMemoryActionJournal;
import fr.vinted.republisher.fakes.InMemoryListingBackupRepository;
import fr.vinted.republisher.fakes.InMemoryListingDeletionRepository;
import fr.vinted.republisher.fakes.InMemoryListingEraser;
import fr.vinted.republisher.fakes.InMemoryListingRepository;
import fr.vinted.republisher.fakes.InMemoryListingRepublicationRepository;
import fr.vinted.republisher.fakes.InMemoryListingSaleRepository;
import fr.vinted.republisher.fakes.InMemoryPhotoStorage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static fr.vinted.republisher.domain.backup.BackupFixtures.aBackup;
import static fr.vinted.republisher.domain.backup.BackupFixtures.aReading;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.ACTIVE;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.INACTIVE;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.aListing;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.tuple;

/** US-47: spots the duplicates of the wardrobe and deletes one on Vinted, then erases it from the tool. */
class DeleteDuplicateAcceptanceTest {

    private static final VintedListingId KEPT = new VintedListingId(1);
    private static final VintedListingId DUPLICATE = new VintedListingId(2);
    private static final Instant SCANNED_AT = Instant.parse("2026-11-18T10:00:00Z");

    private final AdjustableClock clock = new AdjustableClock(Instant.parse("2026-11-19T10:00:00Z"));
    private final InMemoryListingRepository listings = new InMemoryListingRepository();
    private final InMemoryListingBackupRepository backups = new InMemoryListingBackupRepository();
    private final InMemoryPhotoStorage photos = new InMemoryPhotoStorage();
    private final InMemoryListingDeletionRepository deletions = new InMemoryListingDeletionRepository();
    private final InMemoryListingRepublicationRepository republications = new InMemoryListingRepublicationRepository();
    private final InMemoryListingSaleRepository sales = new InMemoryListingSaleRepository();
    private final InMemoryActionJournal journal = new InMemoryActionJournal();
    private final InMemoryListingEraser eraser = new InMemoryListingEraser(listings, backups);
    private final VintedSession session = new VintedSession();
    private final FakeVintedPages vinted = new FakeVintedPages();
    private final CadenceCheck cadence = new CadenceCheck(deletions, republications,
            new WriteCadence(Duration.ofSeconds(30), Duration.ofMinutes(2), 2, 2), clock);

    // AT-47-01
    @Test
    void groups_present_listings_with_the_same_title_and_price() {
        scanned(1, "Robe bleue", "12.00", ACTIVE);
        scanned(2, "Robe bleue", "12.00", ACTIVE);
        scanned(3, "Robe bleue", "15.00", ACTIVE);
        listings.saveAll(List.of(Listing.seen(aListing(4, "Robe bleue", "12.00", ACTIVE), SCANNED_AT).absent()));

        DuplicatesView view = showDuplicates(DeletionMode.REAL).show();

        assertThat(view.groups().groups()).singleElement().satisfies(group -> {
            assertThat(group.title()).isEqualTo("Robe bleue");
            assertThat(group.price()).isEqualTo(euros("12.00"));
            assertThat(group.listings()).extracting(Listing::id).containsExactly(KEPT, DUPLICATE);
        });
        assertThat(view.deletable(KEPT)).isTrue();
        assertThat(view.dryRun()).isFalse();
        assertThat(vinted.opened()).isEmpty();
    }

    // AT-47-02
    @Test
    void refuses_a_listing_with_history() {
        twoDuplicates();
        ListingRepublication republication = ListingRepublication.attempted(aBackup(9, SCANNED_AT), SCANNED_AT,
                WriteOrigin.RUN);
        republications.save(republication.published(KEPT, SCANNED_AT));
        sales.addAll(List.of(new ListingSale(new VintedListingId(3), SCANNED_AT)));

        DuplicatesView view = showDuplicates(DeletionMode.REAL).show();
        Throwable refusal = catchThrowable(() -> deleteDuplicate(DeletionMode.REAL).delete(KEPT));

        assertThat(view.deletable(KEPT)).isFalse();
        assertThat(view.deletable(DUPLICATE)).isTrue();
        assertThat(refusal).isInstanceOf(ListingHasHistory.class);
        assertThat(vinted.opened()).isEmpty();
    }

    // AT-47-03
    @Test
    void refuses_a_listing_without_twin() {
        scanned(1, "Robe bleue", "12.00", ACTIVE);

        Throwable refusal = catchThrowable(() -> deleteDuplicate(DeletionMode.REAL).delete(KEPT));

        assertThat(refusal).isInstanceOf(NotADuplicate.class);
        assertThat(vinted.opened()).isEmpty();
        assertThat(journal.records()).extracting(ActionRecord::action, ActionRecord::outcome, ActionRecord::reason)
                .containsExactly(tuple(JournaledAction.DUPLICATE_DELETION, ActionOutcome.REFUSED,
                        Optional.of("NOT_A_DUPLICATE")));
    }

    @Test
    void refuses_an_unknown_listing_without_journal() {
        Throwable refusal = catchThrowable(() -> deleteDuplicate(DeletionMode.REAL).delete(DUPLICATE));

        assertThat(refusal).isInstanceOf(ListingNotFound.class);
        assertThat(journal.records()).isEmpty();
    }

    // AT-47-04
    @Test
    void deletes_the_duplicate_then_erases_it() {
        twoDuplicates();
        ListingReading reading = aReading(2);
        reading.photos().forEach(photo -> photos.store(DUPLICATE, photo));
        backups.save(ListingBackup.of(reading, SCANNED_AT));
        journal.write(new ActionRecord(SCANNED_AT, JournaledAction.WARDROBE_BACKUP, ActionOutcome.SUCCEEDED,
                Optional.of(DUPLICATE), Optional.empty()));

        DuplicateDeletionReport report = deleteDuplicate(DeletionMode.REAL).delete(DUPLICATE);

        assertThat(report).isEqualTo(new DuplicateDeletionReport(DuplicateDeletionOutcome.DELETED, KEPT));
        assertThat(vinted.opened()).containsExactly(KEPT, DUPLICATE, DUPLICATE, DUPLICATE);
        assertThat(vinted.confirmationClicks()).isEqualTo(1);
        assertThat(listings.find(DUPLICATE)).isEmpty();
        assertThat(listings.find(KEPT)).isPresent();
        assertThat(backups.find(DUPLICATE)).isEmpty();
        assertThat(photos.files(2)).isEmpty();
        assertThat(journal.records()).extracting(ActionRecord::action, ActionRecord::outcome).containsExactly(
                tuple(JournaledAction.WARDROBE_BACKUP, ActionOutcome.SUCCEEDED),
                tuple(JournaledAction.DUPLICATE_DELETION, ActionOutcome.SUCCEEDED));
        assertThat(session.overview().currentAction()).isEmpty();
    }

    // AT-47-05
    @Test
    void refuses_when_no_twin_is_confirmed_on_vinted() {
        twoDuplicates();
        vinted.showing(aListing(1, "Robe bleue", "12.00", INACTIVE));

        Throwable reserved = catchThrowable(() -> deleteDuplicate(DeletionMode.REAL).delete(DUPLICATE));
        vinted.showing(aListing(1, "Robe bleue", "10.00", ACTIVE));
        Throwable otherPrice = catchThrowable(() -> deleteDuplicate(DeletionMode.REAL).delete(DUPLICATE));
        vinted.showingNothingFor(KEPT);
        Throwable gone = catchThrowable(() -> deleteDuplicate(DeletionMode.REAL).delete(DUPLICATE));

        assertThat(List.of(reserved, otherPrice, gone)).allMatch(TwinNotConfirmed.class::isInstance);
        assertThat(vinted.opened()).doesNotContain(DUPLICATE);
        assertThat(listings.find(DUPLICATE)).isPresent();
        assertThat(journal.records()).extracting(ActionRecord::reason)
                .containsOnly(Optional.of("TWIN_NOT_CONFIRMED"));
    }

    @Test
    void reads_the_twins_until_one_is_confirmed() {
        twoDuplicates();
        scanned(3, "Robe bleue", "12.00", ACTIVE);
        vinted.showingNothingFor(KEPT).showing(aListing(3, "Robe bleue", "12.00", ACTIVE));

        DuplicateDeletionReport report = deleteDuplicate(DeletionMode.REAL).delete(DUPLICATE);

        assertThat(report.twin()).isEqualTo(new VintedListingId(3));
    }

    // AT-47-06
    @Test
    void refuses_a_duplicate_changed_on_vinted() {
        twoDuplicates();
        vinted.showing(aListing(2, "Robe bleue", "10.00", ACTIVE));

        Throwable refusal = catchThrowable(() -> deleteDuplicate(DeletionMode.REAL).delete(DUPLICATE));

        assertThat(refusal).isInstanceOf(ListingChanged.class);
        assertThat(((ListingChanged) refusal).differences())
                .containsExactly(new PriceChanged(euros("12.00"), euros("10.00")));
        assertThat(vinted.confirmationClicks()).isZero();
        assertThat(listings.find(DUPLICATE)).isPresent();
    }

    // AT-47-07
    @Test
    void erases_a_duplicate_already_gone_without_click() {
        twoDuplicates();
        vinted.showingNothingFor(DUPLICATE);

        DuplicateDeletionReport report = deleteDuplicate(DeletionMode.REAL).delete(DUPLICATE);

        assertThat(report.outcome()).isEqualTo(DuplicateDeletionOutcome.ALREADY_GONE);
        assertThat(vinted.confirmationClicks()).isZero();
        assertThat(listings.find(DUPLICATE)).isEmpty();
    }

    // AT-47-08
    @Test
    void unconfirmed_deletion_erases_nothing() {
        twoDuplicates();
        vinted.whereTheListingStaysOnceConfirmed();

        DuplicateDeletionReport stays = deleteDuplicate(DeletionMode.REAL).delete(DUPLICATE);
        clock.advance(Duration.ofMinutes(5));
        FakeVintedPages crashing = new FakeVintedPages().showing(aListing(1, "Robe bleue", "12.00", ACTIVE))
                .showing(aListing(2, "Robe bleue", "12.00", ACTIVE)).whereBrowserStopsAfterTheConfirmation();
        DuplicateDeletionReport crashed = deleteDuplicate(DeletionMode.REAL, crashing).delete(DUPLICATE);

        assertThat(List.of(stays.outcome(), crashed.outcome())).containsOnly(DuplicateDeletionOutcome.UNCONFIRMED);
        assertThat(listings.find(DUPLICATE)).isPresent();
        assertThat(journal.records()).extracting(ActionRecord::outcome)
                .containsExactly(ActionOutcome.IN_DOUBT, ActionOutcome.IN_DOUBT);
        assertThat(session.overview().currentAction()).isEmpty();
    }

    // AT-47-08
    @Test
    void erase_failure_after_the_deletion_on_vinted_is_in_doubt_then_erased_without_click() {
        twoDuplicates();
        eraser.whereErasingFails();

        DuplicateDeletionReport failed = deleteDuplicate(DeletionMode.REAL).delete(DUPLICATE);
        clock.advance(Duration.ofMinutes(5));
        eraser.whereErasingWorksAgain();
        DuplicateDeletionReport retried = deleteDuplicate(DeletionMode.REAL).delete(DUPLICATE);

        assertThat(failed.outcome()).isEqualTo(DuplicateDeletionOutcome.UNCONFIRMED);
        assertThat(retried.outcome()).isEqualTo(DuplicateDeletionOutcome.ALREADY_GONE);
        assertThat(vinted.confirmationClicks()).isEqualTo(1);
        assertThat(listings.find(DUPLICATE)).isEmpty();
        assertThat(journal.records()).extracting(ActionRecord::outcome)
                .containsExactly(ActionOutcome.IN_DOUBT, ActionOutcome.SUCCEEDED);
    }

    @Test
    void history_recorded_while_waiting_for_the_lock_is_checked_again_under_it() {
        twoDuplicates();
        // A sale recorded between the check before the lock and the lock itself.
        InMemoryListingSaleRepository soldMeanwhile = new InMemoryListingSaleRepository() {
            private int reads;

            @Override
            public List<ListingSale> findAll() {
                return reads++ == 0 ? List.of() : List.of(new ListingSale(DUPLICATE, SCANNED_AT));
            }
        };
        ShowDuplicates show = new ShowDuplicates(listings, backups, deletions, republications, soldMeanwhile,
                DeletionMode.REAL);

        Throwable refusal = catchThrowable(() -> new DeleteDuplicate(session, show, listings, vinted, eraser, photos,
                DeletionMode.REAL, new JournalWriter(journal, clock)).delete(DUPLICATE));

        assertThat(refusal).isInstanceOf(ListingHasHistory.class);
        assertThat(vinted.opened()).isEmpty();
        assertThat(session.overview().currentAction()).isEmpty();
    }

    // AT-47-09
    @Test
    void dry_run_clicks_and_erases_nothing() {
        twoDuplicates();

        DuplicateDeletionReport report = deleteDuplicate(DeletionMode.DRY_RUN).delete(DUPLICATE);

        assertThat(report.outcome()).isEqualTo(DuplicateDeletionOutcome.DRY_RUN_PASSED);
        assertThat(vinted.opened()).containsExactly(KEPT, DUPLICATE, DUPLICATE);
        assertThat(vinted.confirmationClicks()).isZero();
        assertThat(listings.find(DUPLICATE)).isPresent();
        assertThat(journal.records()).extracting(ActionRecord::outcome).containsExactly(ActionOutcome.DRY_RUN);
    }

    // AT-47-10
    @Test
    void refuses_while_the_listing_is_busy() {
        twoDuplicates();
        session.start(BrowserAction.DELETING, DUPLICATE);

        Throwable refusal = catchThrowable(() -> deleteDuplicate(DeletionMode.REAL).delete(DUPLICATE));

        assertThat(refusal).isInstanceOf(BrowserBusy.class);
        assertThat(vinted.opened()).isEmpty();
    }

    // AT-47-10, AT-49-04b
    @Test
    void ignores_the_cadence_and_does_not_count_in_it() {
        // Étant donné la limite de cadence des suppressions atteinte (réel)
        twoDuplicates();
        scanned(3, "Pull", "5.00", ACTIVE);
        scanned(4, "Pull", "5.00", ACTIVE);
        deletions.save(ListingDeletion.attempted(aBackup(9, SCANNED_AT), clock.instant().minusSeconds(10),
                WriteOrigin.RUN));
        deletions.save(ListingDeletion.attempted(aBackup(8, SCANNED_AT), clock.instant().minusSeconds(3600),
                WriteOrigin.RUN));
        Instant runAllowedAt = cadence.allowedAt(VintedWrite.DELETION);
        // Quand le membre supprime un doublon
        DuplicateDeletionReport report = deleteDuplicate(DeletionMode.REAL).delete(DUPLICATE);
        // Alors il est supprimé sur Vinted
        assertThat(report.outcome()).isEqualTo(DuplicateDeletionOutcome.DELETED);
        // Et la série peut ensuite supprimer une annonce sans attendre davantage
        assertThat(cadence.allowedAt(VintedWrite.DELETION)).isEqualTo(runAllowedAt);
    }

    // AT-47-11
    @Test
    void refuses_on_browser_problems() {
        twoDuplicates();
        vinted.failingBeforeAnyClick(() -> new SessionDisconnected("redirected to /member/login"));

        Throwable refusal = catchThrowable(() -> deleteDuplicate(DeletionMode.REAL).delete(DUPLICATE));

        assertThat(refusal).isInstanceOf(SessionDisconnected.class);
        assertThat(listings.find(DUPLICATE)).isPresent();
        assertThat(session.overview().currentAction()).isEmpty();
        assertThat(journal.records()).extracting(ActionRecord::outcome, ActionRecord::reason)
                .containsExactly(tuple(ActionOutcome.FAILED, Optional.of("SESSION_DISCONNECTED")));
    }

    // AT-47-12
    @Test
    void journals_every_request() {
        twoDuplicates();
        scanned(3, "Pull", "5.00", ACTIVE);
        catchThrowable(() -> deleteDuplicate(DeletionMode.REAL).delete(new VintedListingId(3)));
        deleteDuplicate(DeletionMode.DRY_RUN).delete(DUPLICATE);

        assertThat(journal.records()).extracting(ActionRecord::action)
                .containsOnly(JournaledAction.DUPLICATE_DELETION).hasSize(2);
        assertThat(journal.lastOfEachListing()).isEmpty();
    }

    private void twoDuplicates() {
        scanned(1, "Robe bleue", "12.00", ACTIVE);
        scanned(2, "Robe bleue", "12.00", ACTIVE);
    }

    private ScannedListing scanned(long id, String title, String price, boolean active) {
        ScannedListing listing = aListing(id, title, price, active);
        listings.saveAll(List.of(Listing.seen(listing, SCANNED_AT)));
        vinted.showing(listing);
        return listing;
    }

    private ShowDuplicates showDuplicates(DeletionMode mode) {
        return new ShowDuplicates(listings, backups, deletions, republications, sales, mode);
    }

    private DeleteDuplicate deleteDuplicate(DeletionMode mode) {
        return deleteDuplicate(mode, vinted);
    }

    private DeleteDuplicate deleteDuplicate(DeletionMode mode, FakeVintedPages pages) {
        return new DeleteDuplicate(session, showDuplicates(mode), listings, pages, eraser, photos, mode,
                new JournalWriter(journal, clock));
    }

    private static Price euros(String amount) {
        return new Price(new BigDecimal(amount), "EUR");
    }
}
