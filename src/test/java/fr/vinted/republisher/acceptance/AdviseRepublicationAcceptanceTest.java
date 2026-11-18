package fr.vinted.republisher.acceptance;

import fr.vinted.republisher.application.inventory.ShowListingInventory;
import fr.vinted.republisher.domain.backup.ListingBackup;
import fr.vinted.republisher.domain.deletion.ListingDeletion;
import fr.vinted.republisher.domain.inventory.BackupFilter;
import fr.vinted.republisher.domain.inventory.InventoryCriteria;
import fr.vinted.republisher.domain.inventory.InventoryEntry;
import fr.vinted.republisher.domain.inventory.InventorySort;
import fr.vinted.republisher.domain.inventory.PresenceFilter;
import fr.vinted.republisher.domain.inventory.RepublicationAdvice;
import fr.vinted.republisher.domain.inventory.SortDirection;
import fr.vinted.republisher.domain.republication.ListingRepublication;
import fr.vinted.republisher.domain.republication.RepublicationDelay;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.ListingStatistics;
import fr.vinted.republisher.domain.wardrobe.StatisticsReading;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import fr.vinted.republisher.fakes.AdjustableClock;
import fr.vinted.republisher.fakes.InMemoryBackupValidationRepository;
import fr.vinted.republisher.fakes.InMemoryListingBackupRepository;
import fr.vinted.republisher.fakes.InMemoryListingDeletionRepository;
import fr.vinted.republisher.fakes.InMemoryListingRepository;
import fr.vinted.republisher.fakes.InMemoryListingRepublicationRepository;
import fr.vinted.republisher.fakes.InMemoryListingStatisticsRepository;
import fr.vinted.republisher.fakes.InMemoryListingTagRepository;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static fr.vinted.republisher.domain.backup.BackupFixtures.aBackup;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.anActiveListing;
import static org.assertj.core.api.Assertions.assertThat;

/** US-35: the inventory advises which listings to republish, without acting. */
class AdviseRepublicationAcceptanceTest {

    private static final Instant NOV_1 = Instant.parse("2026-11-01T10:00:00Z");
    private static final Instant NOV_8 = Instant.parse("2026-11-08T10:00:00Z");
    private static final Instant NOV_12 = Instant.parse("2026-11-12T10:00:00Z");
    private static final Instant NOV_16 = Instant.parse("2026-11-16T10:00:00Z");
    private static final Instant NOV_17 = Instant.parse("2026-11-17T10:00:00Z");

    private final InMemoryListingRepository listings = new InMemoryListingRepository();
    private final InMemoryListingStatisticsRepository statistics = new InMemoryListingStatisticsRepository();
    private final InMemoryListingBackupRepository backups = new InMemoryListingBackupRepository();
    private final InMemoryListingDeletionRepository deletions = new InMemoryListingDeletionRepository();
    private final InMemoryListingRepublicationRepository republications = new InMemoryListingRepublicationRepository();
    private final ShowListingInventory showListingInventory = new ShowListingInventory(listings, statistics, backups,
            new InMemoryBackupValidationRepository(), deletions, republications,
            new RepublicationDelay(Duration.ofMinutes(5)), new AdjustableClock(NOV_17),
            new InMemoryListingTagRepository());

    @Test
    void advises_a_dormant_listing() {
        // Étant donné l'annonce 1 en vente, relevée le 8 novembre à 40 vues et 2 favoris,
        //   puis le 16 novembre à 46 vues et 2 favoris
        onSale(1);
        statistics.addAll(List.of(reading(1, 40, 2, NOV_8), reading(1, 46, 2, NOV_16)));

        // Quand je consulte la liste des annonces le 17 novembre
        // Alors l'annonce 1 est conseillée à republier : 6 vues gagnées depuis le 8 novembre
        assertThat(advices()).containsExactly(Map.entry(1L, Optional.of(new RepublicationAdvice(6, NOV_8))));
    }

    @Test
    void does_not_advise_a_listing_still_seen() {
        // Étant donné l'annonce 1 en vente, relevée le 8 novembre à 40 vues, puis le 16 novembre à 50 vues
        onSale(1);
        statistics.addAll(List.of(reading(1, 40, 0, NOV_8), reading(1, 50, 0, NOV_16)));

        // Quand je consulte la liste
        // Alors l'annonce 1 n'est pas conseillée
        assertThat(advices()).containsExactly(Map.entry(1L, Optional.empty()));
    }

    @Test
    void does_not_advise_a_listing_with_many_favourites() {
        // Étant donné l'annonce 1 en vente, relevée le 8 novembre à 40 vues et 5 favoris,
        //   puis le 16 novembre à 41 vues et 5 favoris
        onSale(1);
        statistics.addAll(List.of(reading(1, 40, 5, NOV_8), reading(1, 41, 5, NOV_16)));

        // Quand je consulte la liste
        // Alors l'annonce 1 n'est pas conseillée
        assertThat(advices()).containsExactly(Map.entry(1L, Optional.empty()));
    }

    @Test
    void does_not_advise_without_enough_readings() {
        // Étant donné l'annonce 1 relevée seulement depuis le 12 novembre
        // Et l'annonce 2 relevée le 1er novembre puis le 8 novembre, plus depuis
        onSale(1);
        onSale(2);
        statistics.addAll(List.of(reading(1, 1, 0, NOV_12), reading(1, 2, 0, NOV_16),
                reading(2, 1, 0, NOV_1), reading(2, 2, 0, NOV_8)));

        // Quand je consulte la liste
        // Alors aucune n'est conseillée
        assertThat(advices()).containsOnly(Map.entry(1L, Optional.empty()), Map.entry(2L, Optional.empty()));
    }

    @Test
    void does_not_advise_a_listing_not_on_sale() {
        // Étant donné trois annonces endormies : l'une absente du dressing, l'une dont la suppression est confirmée,
        //   l'une republiée
        listings.saveAll(List.of(Listing.seen(anActiveListing(1), NOV_8).absent(),
                Listing.seen(anActiveListing(2), NOV_8), Listing.seen(anActiveListing(3), NOV_8)));
        ListingBackup deleted = aBackup(2, NOV_8);
        ListingBackup republished = aBackup(3, NOV_8);
        backups.save(deleted);
        backups.save(republished);
        deletions.save(ListingDeletion.attempted(deleted, NOV_12).confirmed(NOV_12));
        deletions.save(ListingDeletion.attempted(republished, NOV_12).confirmed(NOV_12));
        republications.save(ListingRepublication.attempted(republished, NOV_12)
                .published(new VintedListingId(4), NOV_12));
        statistics.addAll(List.of(reading(1, 1, 0, NOV_8), reading(1, 1, 0, NOV_16),
                reading(2, 1, 0, NOV_8), reading(2, 1, 0, NOV_16),
                reading(3, 1, 0, NOV_8), reading(3, 1, 0, NOV_16)));

        // Quand je consulte la liste
        // Alors aucune n'est conseillée
        assertThat(advices()).containsOnly(Map.entry(1L, Optional.empty()), Map.entry(2L, Optional.empty()),
                Map.entry(3L, Optional.empty()));
    }

    private void onSale(long id) {
        listings.saveAll(List.of(Listing.seen(anActiveListing(id), NOV_16)));
    }

    private static StatisticsReading reading(long id, int views, int favourites, Instant readAt) {
        return new StatisticsReading(new VintedListingId(id), new ListingStatistics(views, favourites), readAt);
    }

    private Map<Long, Optional<RepublicationAdvice>> advices() {
        InventoryCriteria all = new InventoryCriteria(PresenceFilter.ALL, BackupFilter.ALL, "", InventorySort.RECENCY,
                SortDirection.DESC);
        return showListingInventory.show(all).stream()
                .collect(Collectors.toMap(entry -> entry.listing().id().value(), InventoryEntry::advice));
    }
}
