package fr.vinted.republisher.acceptance;

import fr.vinted.republisher.application.deletion.DeleteListing;
import fr.vinted.republisher.application.deletion.RecheckListingDeletion;
import fr.vinted.republisher.application.deletion.ShowListingDeletion;
import fr.vinted.republisher.application.journal.JournalWriter;
import fr.vinted.republisher.domain.backup.BackupValidation;
import fr.vinted.republisher.domain.backup.ListingBackup;
import fr.vinted.republisher.domain.backup.ListingReading;
import fr.vinted.republisher.domain.deletion.DeletionMode;
import fr.vinted.republisher.domain.deletion.DeletionOutcome;
import fr.vinted.republisher.domain.deletion.ListingDeletion;
import fr.vinted.republisher.domain.republication.RepublicationDelay;
import fr.vinted.republisher.domain.session.VintedSession;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import fr.vinted.republisher.fakes.AdjustableClock;
import fr.vinted.republisher.fakes.FakeListingDeleter;
import fr.vinted.republisher.fakes.InMemoryActionJournal;
import fr.vinted.republisher.fakes.InMemoryBackupValidationRepository;
import fr.vinted.republisher.fakes.InMemoryListingBackupRepository;
import fr.vinted.republisher.fakes.InMemoryListingDeletionRepository;
import fr.vinted.republisher.fakes.InMemoryListingRepository;
import fr.vinted.republisher.fakes.InMemoryPhotoStorage;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static fr.vinted.republisher.domain.backup.BackupFixtures.aReading;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.anActiveListing;
import static org.assertj.core.api.Assertions.assertThat;

class RepublicationDelayAcceptanceTest {

    private static final Instant SCANNED_AT = Instant.parse("2026-11-10T09:00:00Z");
    private static final Instant SAVED_AT = Instant.parse("2026-11-10T10:00:00Z");
    private static final Instant VALIDATED_AT = Instant.parse("2026-11-10T11:00:00Z");
    private static final Instant PROVEN_AT = Instant.parse("2026-11-11T14:32:00Z");
    private static final RepublicationDelay FIVE_MINUTES = new RepublicationDelay(Duration.ofMinutes(5));

    private final InMemoryListingRepository listings = new InMemoryListingRepository();
    private final InMemoryListingBackupRepository listingBackups = new InMemoryListingBackupRepository();
    private final InMemoryPhotoStorage photos = new InMemoryPhotoStorage();
    private final InMemoryBackupValidationRepository validations = new InMemoryBackupValidationRepository();
    private final InMemoryListingDeletionRepository deletions = new InMemoryListingDeletionRepository();
    private final FakeListingDeleter vinted = new FakeListingDeleter();
    private final VintedSession session = new VintedSession();
    private final AdjustableClock clock = new AdjustableClock(PROVEN_AT);

    @Test
    void confirmedDeletionIsRepublishableAfterMinimumDelay() {
        // Étant donné une annonce dont la suppression a été prouvée à 14:32
        // Et un délai minimal de 5 minutes
        aConfirmedDeletion(1);
        // Quand le membre ouvre l'aperçu de sa sauvegarde à 14:34
        clock.advance(Duration.ofMinutes(2));
        // Alors la suppression indique « republiable à partir de 14:37 »
        assertThat(showListingDeletion().show(new VintedListingId(1)).republishableAt())
                .isEqualTo(Optional.of(Instant.parse("2026-11-11T14:37:00Z")));
    }

    @Test
    void elapsedDelayLeavesListingRepublishable() {
        // Étant donné une annonce dont la suppression a été prouvée à 14:32
        aConfirmedDeletion(1);
        // Quand le membre ouvre l'aperçu de sa sauvegarde à 14:45
        clock.advance(Duration.ofMinutes(13));
        // Alors la suppression indique « republiable à partir de 14:37 », date déjà passée
        assertThat(showListingDeletion().show(new VintedListingId(1)).republishableAt())
                .hasValueSatisfying(date -> assertThat(date)
                        .isEqualTo(Instant.parse("2026-11-11T14:37:00Z")).isBefore(clock.instant()));
    }

    @Test
    void noRepublicationDateWithoutConfirmedDeletion() {
        // Étant donné une annonce sans suppression, une autre dont la suppression est non confirmée,
        // une troisième dont la suppression est non faite
        aValidatedListing(1);
        anUnconfirmedDeletion(2);
        anUnconfirmedDeletion(3);
        vinted.showing(anActiveListing(3));
        assertThat(recheckListingDeletion().recheck(new VintedListingId(3)).lastDeletion())
                .map(ListingDeletion::outcome).contains(DeletionOutcome.NOT_DONE);
        // Quand le membre ouvre l'aperçu de leur sauvegarde
        // Alors aucune n'a de date de republication
        for (long listing = 1; listing <= 3; listing++) {
            assertThat(showListingDeletion().show(new VintedListingId(listing)).republishableAt()).isEmpty();
        }
    }

    @Test
    void recheckProofStartsTheDelay() {
        // Étant donné une suppression cliquée à 14:00, restée non confirmée
        clock.set(Instant.parse("2026-11-11T14:00:00Z"));
        anUnconfirmedDeletion(1);
        // Quand le membre la revérifie à 16:00 et Vinted ne montre plus l'annonce
        clock.set(Instant.parse("2026-11-11T16:00:00Z"));
        vinted.showingNothing();
        // Alors l'annonce est republiable à partir de 16:05
        assertThat(recheckListingDeletion().recheck(new VintedListingId(1)).republishableAt())
                .isEqualTo(Optional.of(Instant.parse("2026-11-11T16:05:00Z")));
    }

    private void aConfirmedDeletion(long listing) {
        aValidatedListing(listing);
        vinted.showing(anActiveListing(listing));
        deleteListing().delete(new VintedListingId(listing), SAVED_AT);
    }

    private void anUnconfirmedDeletion(long listing) {
        aValidatedListing(listing);
        vinted.showing(anActiveListing(listing)).whereTheListingStaysOnceConfirmed();
        deleteListing().delete(new VintedListingId(listing), SAVED_AT);
    }

    private ShowListingDeletion showListingDeletion() {
        return new ShowListingDeletion(listings, listingBackups, photos, validations, deletions, DeletionMode.REAL,
                FIVE_MINUTES);
    }

    private DeleteListing deleteListing() {
        return new DeleteListing(session, listings, listingBackups, photos, validations, deletions, vinted,
                DeletionMode.REAL, FIVE_MINUTES, clock, new JournalWriter(new InMemoryActionJournal(), Clock.systemUTC()));
    }

    private RecheckListingDeletion recheckListingDeletion() {
        return new RecheckListingDeletion(session, listings, listingBackups, photos, validations, deletions, vinted,
                DeletionMode.REAL, FIVE_MINUTES, clock, new JournalWriter(new InMemoryActionJournal(), Clock.systemUTC()));
    }

    private void aValidatedListing(long listing) {
        listings.saveAll(List.of(Listing.seen(anActiveListing(listing), SCANNED_AT)));
        ListingReading reading = aReading(listing);
        reading.photos().forEach(photo -> photos.store(new VintedListingId(listing), photo));
        ListingBackup backup = ListingBackup.of(reading, SAVED_AT);
        listingBackups.save(backup);
        validations.save(BackupValidation.of(backup, VALIDATED_AT));
    }
}
