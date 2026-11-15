package fr.vinted.republisher.acceptance;

import java.time.Clock;
import fr.vinted.republisher.domain.republication.RepublicationDelay;
import fr.vinted.republisher.fakes.InMemoryListingRepublicationRepository;
import fr.vinted.republisher.fakes.InMemoryListingDeletionRepository;
import fr.vinted.republisher.application.inventory.ShowListingDetail;
import fr.vinted.republisher.domain.backup.BackupValidation;
import fr.vinted.republisher.domain.backup.ListingBackup;
import fr.vinted.republisher.domain.inventory.ListingDetail;
import fr.vinted.republisher.domain.inventory.ListingNotFound;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.ListingStatistics;
import fr.vinted.republisher.domain.wardrobe.StatisticsReading;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import fr.vinted.republisher.fakes.InMemoryBackupValidationRepository;
import fr.vinted.republisher.fakes.InMemoryListingBackupRepository;
import fr.vinted.republisher.fakes.InMemoryListingRepository;
import fr.vinted.republisher.fakes.InMemoryListingStatisticsRepository;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static fr.vinted.republisher.domain.backup.BackupFixtures.aBackup;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.ACTIVE;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.aListing;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** US-18b: the member opens the detail of a listing. */
class ShowListingDetailAcceptanceTest {

    private static final Instant FIRST = Instant.parse("2026-11-01T09:00:00Z");
    private static final Instant SECOND = FIRST.plus(Duration.ofDays(1));
    private static final Instant THIRD = SECOND.plus(Duration.ofDays(1));

    private final InMemoryListingRepository listings = new InMemoryListingRepository();
    private final InMemoryListingStatisticsRepository statistics = new InMemoryListingStatisticsRepository();
    private final InMemoryListingBackupRepository backups = new InMemoryListingBackupRepository();
    private final InMemoryBackupValidationRepository validations = new InMemoryBackupValidationRepository();
    private final ShowListingDetail showListingDetail =
            new ShowListingDetail(listings, statistics, backups, validations,
                    new InMemoryListingDeletionRepository(), new InMemoryListingRepublicationRepository(),
                    new RepublicationDelay(Duration.ofMinutes(5)), Clock.systemUTC());

    @Test
    void shows_listing_statistics_history_most_recent_first() {
        // Étant donné l'annonce 101 « Pull rayé », 12,50 €, présente, relevée à 5 vues et 1 favori le 1er novembre
        //   puis 12 vues et 3 favoris le 2, et sauvegardée
        Listing listing = Listing.seen(aListing(101, "Pull rayé", "12.50", ACTIVE), SECOND);
        listings.saveAll(List.of(listing));
        statistics.addAll(List.of(reading(101, 5, 1, FIRST), reading(101, 12, 3, SECOND), reading(102, 1, 1, SECOND)));
        ListingBackup backup = aBackup(101, SECOND);
        backups.save(backup);
        validations.save(BackupValidation.of(backup, THIRD));

        // Quand le membre ouvre sa fiche
        ListingDetail detail = showListingDetail.show(new VintedListingId(101));

        // Alors il voit son titre, son prix, son lien Vinted et sa présence
        //   et les relevés du 2 (12 vues, 3 favoris) puis du 1er (5 vues, 1 favori)
        //   et sa sauvegarde
        assertThat(detail.entry().listing()).isEqualTo(listing);
        assertThat(detail.history()).containsExactly(reading(101, 12, 3, SECOND), reading(101, 5, 1, FIRST));
        assertThat(detail.entry().statistics()).contains(reading(101, 12, 3, SECOND));
        assertThat(detail.entry().backup()).contains(backup);
        assertThat(detail.entry().validatedAt()).contains(THIRD);
    }

    @Test
    void listing_never_read_has_empty_history() {
        // Étant donné l'annonce 102 présente, jamais relevée ni sauvegardée
        listings.saveAll(List.of(Listing.seen(aListing(102, "Jupe", "3", ACTIVE), FIRST)));

        // Quand le membre ouvre sa fiche
        ListingDetail detail = showListingDetail.show(new VintedListingId(102));

        // Alors il voit « Aucun relevé » et « Non sauvegardée »
        assertThat(detail.history()).isEmpty();
        assertThat(detail.entry().statistics()).isEmpty();
        assertThat(detail.entry().backup()).isEmpty();
        assertThat(detail.entry().validatedAt()).isEqualTo(Optional.empty());
    }

    @Test
    void absent_listing_tells_when_it_was_last_seen() {
        // Étant donné l'annonce 103 vue au scan du 3 novembre puis absente
        listings.saveAll(List.of(Listing.seen(aListing(103, "Robe", "6", ACTIVE), THIRD).absent()));

        // Quand le membre ouvre sa fiche
        Listing shown = showListingDetail.show(new VintedListingId(103)).entry().listing();

        // Alors il la voit absente, vue pour la dernière fois le 3 novembre
        assertThat(shown.present()).isFalse();
        assertThat(shown.lastSeenAt()).isEqualTo(THIRD);
    }

    @Test
    void unknown_listing_is_refused() {
        assertThatThrownBy(() -> showListingDetail.show(new VintedListingId(999)))
                .isInstanceOf(ListingNotFound.class);
    }

    private static StatisticsReading reading(long id, int views, int favourites, Instant readAt) {
        return new StatisticsReading(new VintedListingId(id), new ListingStatistics(views, favourites), readAt);
    }
}
