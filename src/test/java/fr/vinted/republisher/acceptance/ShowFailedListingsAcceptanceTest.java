package fr.vinted.republisher.acceptance;

import fr.vinted.republisher.application.inventory.ShowFailedListings;
import fr.vinted.republisher.application.inventory.ShowListingInventory;
import fr.vinted.republisher.domain.inventory.FailedListing;
import fr.vinted.republisher.domain.journal.ActionOutcome;
import fr.vinted.republisher.domain.journal.ActionRecord;
import fr.vinted.republisher.domain.journal.JournaledAction;
import fr.vinted.republisher.domain.republication.RepublicationDelay;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import fr.vinted.republisher.fakes.InMemoryActionJournal;
import fr.vinted.republisher.fakes.InMemoryBackupValidationRepository;
import fr.vinted.republisher.fakes.InMemoryBackupVerificationRepository;
import fr.vinted.republisher.fakes.InMemoryListingBackupRepository;
import fr.vinted.republisher.fakes.InMemoryListingDeletionRepository;
import fr.vinted.republisher.fakes.InMemoryListingRepository;
import fr.vinted.republisher.fakes.InMemoryListingRepublicationRepository;
import fr.vinted.republisher.fakes.InMemoryListingStatisticsRepository;
import fr.vinted.republisher.fakes.InMemoryListingTagRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.ACTIVE;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.aListing;
import static org.assertj.core.api.Assertions.assertThat;

/** US-39: the member sees every listing whose last action failed, with its cause. */
class ShowFailedListingsAcceptanceTest {

    private static final Instant SCANNED_AT = Instant.parse("2026-11-10T09:00:00Z");
    private static final Instant NOV_17 = Instant.parse("2026-11-17T10:00:00Z");
    private static final Instant NOV_18 = Instant.parse("2026-11-18T10:00:00Z");

    private final InMemoryListingRepository listings = new InMemoryListingRepository();
    private final InMemoryActionJournal journal = new InMemoryActionJournal();
    private final ShowFailedListings showFailedListings = new ShowFailedListings(journal,
            new ShowListingInventory(listings, new InMemoryListingStatisticsRepository(),
                    new InMemoryListingBackupRepository(), new InMemoryBackupValidationRepository(),
                    new InMemoryListingDeletionRepository(), new InMemoryListingRepublicationRepository(),
                    new RepublicationDelay(Duration.ofMinutes(5)), Clock.systemUTC(),
                    new InMemoryListingTagRepository(), new InMemoryBackupVerificationRepository()));

    @Test
    void lists_listings_whose_last_action_failed() {
        // Étant donné une annonce dont la suppression a été refusée (sauvegarde remplacée)
        // Et une annonce dont la republication est en doute
        // Et une annonce dont la revérification a conclu « non faite »
        // Et une annonce dont la suppression a échoué, session non connectée
        // Et une annonce supprimée avec succès
        listings.saveAll(List.of(present(101), present(102), present(103), present(104), present(105)));
        write(101, JournaledAction.DELETION, ActionOutcome.REFUSED, "BACKUP_REPLACED", NOV_18);
        write(102, JournaledAction.REPUBLICATION, ActionOutcome.IN_DOUBT, null, NOV_18);
        write(103, JournaledAction.DELETION_RECHECK, ActionOutcome.NOT_DONE, null, NOV_18);
        write(104, JournaledAction.DELETION, ActionOutcome.FAILED, "SESSION_DISCONNECTED", NOV_18);
        write(105, JournaledAction.DELETION, ActionOutcome.SUCCEEDED, null, NOV_18);

        // Quand le membre demande les annonces en échec
        List<FailedListing> failed = showFailedListings.show();

        // Alors les quatre premières sont renvoyées, chacune avec sa dernière ligne du journal
        // Et l'annonce supprimée avec succès n'y est pas
        assertThat(failed).extracting(listing -> listing.listing().id().value()).containsExactly(104L, 103L, 102L, 101L);
        assertThat(failed).extracting(listing -> listing.failure().record().outcome())
                .containsExactly(ActionOutcome.FAILED, ActionOutcome.NOT_DONE, ActionOutcome.IN_DOUBT,
                        ActionOutcome.REFUSED);
        assertThat(failed.getFirst().failure().record().reason()).contains("SESSION_DISCONNECTED");
    }

    @Test
    void later_success_clears_failure_and_waiting_refusal_does_not() {
        // Étant donné une annonce dont la suppression a échoué, puis réussi
        // Et une annonce dont la suppression a échoué, puis été refusée pour cadence
        // Et une annonce dont la suppression a été refusée pour cadence seulement
        listings.saveAll(List.of(present(101), present(102), present(103)));
        write(101, JournaledAction.DELETION, ActionOutcome.FAILED, "VINTED_UNREACHABLE", NOV_17);
        write(101, JournaledAction.DELETION, ActionOutcome.SUCCEEDED, null, NOV_18);
        write(102, JournaledAction.DELETION, ActionOutcome.FAILED, "VINTED_UNREACHABLE", NOV_17);
        write(102, JournaledAction.DELETION, ActionOutcome.REFUSED, "CADENCE_LIMIT_REACHED", NOV_18);
        write(103, JournaledAction.DELETION, ActionOutcome.REFUSED, "CADENCE_LIMIT_REACHED", NOV_18);

        // Quand le membre demande les annonces en échec
        List<FailedListing> failed = showFailedListings.show();

        // Alors seule la deuxième est renvoyée, avec l'échec de sa suppression pour cause
        assertThat(failed).singleElement().satisfies(listing -> {
            assertThat(listing.listing().id().value()).isEqualTo(102L);
            assertThat(listing.failure().record().outcome()).isEqualTo(ActionOutcome.FAILED);
            assertThat(listing.failure().record().reason()).contains("VINTED_UNREACHABLE");
        });
    }

    @Test
    void verified_republication_leaves_the_failed_listings() {
        // US-44 AT-44-07. Étant donné une annonce dont la republication est restée dans le doute
        // Et une annonce validée « non publiée » par le membre
        listings.saveAll(List.of(present(101), present(102)));
        write(101, JournaledAction.REPUBLICATION, ActionOutcome.IN_DOUBT, null, NOV_17);
        write(102, JournaledAction.REPUBLICATION, ActionOutcome.IN_DOUBT, null, NOV_17);
        // Quand la vérification trouve la première publiée et que la seconde est validée « non publiée »
        write(101, JournaledAction.REPUBLICATION_VERIFICATION, ActionOutcome.SUCCEEDED, null, NOV_18);
        write(102, JournaledAction.REPUBLICATION_VALIDATION, ActionOutcome.NOT_DONE, null, NOV_18);

        // Alors seule la seconde reste dans les annonces en échec
        assertThat(showFailedListings.show()).singleElement().satisfies(listing -> {
            assertThat(listing.listing().id().value()).isEqualTo(102L);
            assertThat(listing.failure().record().action()).isEqualTo(JournaledAction.REPUBLICATION_VALIDATION);
        });
    }

    @Test
    void ignores_listings_absent_from_vinted() {
        // Étant donné une annonce dont la republication a échoué, puis vue absente de Vinted
        listings.saveAll(List.of(present(101).absent()));
        write(101, JournaledAction.REPUBLICATION, ActionOutcome.FAILED, "FORM_UNREADABLE", NOV_18);

        // Quand le membre demande les annonces en échec
        // Alors elle n'est pas renvoyée
        assertThat(showFailedListings.show()).isEmpty();
    }

    @Test
    void most_recent_failure_first() {
        // Étant donné deux annonces en échec, la première le 17/11/2026, la seconde le 18/11/2026
        listings.saveAll(List.of(present(101), present(102)));
        write(101, JournaledAction.DELETION, ActionOutcome.IN_DOUBT, null, NOV_17);
        write(102, JournaledAction.DELETION, ActionOutcome.IN_DOUBT, null, NOV_18);

        // Quand le membre demande les annonces en échec
        // Alors la seconde vient d'abord
        assertThat(showFailedListings.show()).extracting(listing -> listing.listing().id().value())
                .containsExactly(102L, 101L);
    }

    private void write(long listingId, JournaledAction action, ActionOutcome outcome, String reason,
                       Instant occurredAt) {
        journal.write(new ActionRecord(occurredAt, action, outcome, Optional.of(new VintedListingId(listingId)),
                Optional.ofNullable(reason)));
    }

    private static Listing present(long id) {
        return Listing.seen(aListing(id, "Annonce " + id, "10", ACTIVE), SCANNED_AT);
    }
}
