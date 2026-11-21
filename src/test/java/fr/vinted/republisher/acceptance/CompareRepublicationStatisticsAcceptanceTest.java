package fr.vinted.republisher.acceptance;

import fr.vinted.republisher.application.inventory.ShowListingDetail;
import fr.vinted.republisher.domain.cadence.WriteOrigin;
import fr.vinted.republisher.domain.inventory.StatisticsComparison;
import fr.vinted.republisher.domain.republication.ListingRepublication;
import fr.vinted.republisher.domain.republication.ListingRepublicationId;
import fr.vinted.republisher.domain.republication.RepublicationDelay;
import fr.vinted.republisher.domain.republication.RepublicationOutcome;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.ListingStatistics;
import fr.vinted.republisher.domain.wardrobe.StatisticsReading;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import fr.vinted.republisher.fakes.AdjustableClock;
import fr.vinted.republisher.fakes.InMemoryActionJournal;
import fr.vinted.republisher.fakes.InMemoryBackupValidationRepository;
import fr.vinted.republisher.fakes.InMemoryBackupVerificationRepository;
import fr.vinted.republisher.fakes.InMemoryListingBackupRepository;
import fr.vinted.republisher.fakes.InMemoryListingDeletionRepository;
import fr.vinted.republisher.fakes.InMemoryListingRepository;
import fr.vinted.republisher.fakes.InMemoryListingRepublicationRepository;
import fr.vinted.republisher.fakes.InMemoryListingSaleRepository;
import fr.vinted.republisher.fakes.InMemoryListingStatisticsRepository;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.anActiveListing;
import static org.assertj.core.api.Assertions.assertThat;

/** US-24: the detail of a republished listing compares it with the listing it replaces. */
class CompareRepublicationStatisticsAcceptanceTest {

    private static final Instant NOV_1 = Instant.parse("2026-11-01T09:00:00Z");
    private static final Instant NOV_9 = Instant.parse("2026-11-09T09:00:00Z");
    private static final Instant NOV_10 = Instant.parse("2026-11-10T09:00:00Z");
    private static final Instant NOV_12 = Instant.parse("2026-11-12T09:00:00Z");
    private static final Instant NOV_15 = Instant.parse("2026-11-15T09:00:00Z");
    private static final Instant NOV_17 = Instant.parse("2026-11-17T09:00:00Z");

    private final InMemoryListingRepository listings = new InMemoryListingRepository();
    private final InMemoryListingStatisticsRepository statistics = new InMemoryListingStatisticsRepository();
    private final InMemoryListingRepublicationRepository republications = new InMemoryListingRepublicationRepository();
    private final ShowListingDetail showListingDetail = new ShowListingDetail(listings, statistics,
            new InMemoryListingBackupRepository(), new InMemoryBackupValidationRepository(),
            new InMemoryListingDeletionRepository(), republications, new RepublicationDelay(Duration.ofMinutes(5)),
            new AdjustableClock(NOV_17), new InMemoryActionJournal(), new InMemoryBackupVerificationRepository(),
                    new InMemoryListingSaleRepository());

    @Test
    void compares_before_and_after_a_republication() {
        // Étant donné l'annonce 1, relevée à 120 vues et 8 favoris, republiée le 10 novembre sous l'annonce 2
        // Et l'annonce 2 relevée à 30 vues et 4 favoris
        known(1, 2);
        statistics.addAll(List.of(reading(1, 50, 2, NOV_1), reading(1, 120, 8, NOV_9), reading(2, 30, 4, NOV_15)));
        republications.save(published(1, 2, NOV_10));

        // Quand j'ouvre la fiche de l'annonce 2
        Optional<StatisticsComparison> comparison = showListingDetail.show(new VintedListingId(2)).comparison();

        // Alors avant : 120 vues et 8 favoris ; après : 30 vues et 4 favoris
        // Et republiée le 10 novembre depuis l'annonce 1, total cumulé 150 vues et 12 favoris
        assertThat(comparison).contains(new StatisticsComparison(new VintedListingId(1), NOV_10,
                Optional.of(reading(1, 120, 8, NOV_9)), Optional.of(reading(2, 30, 4, NOV_15)),
                new ListingStatistics(150, 12), 1));
    }

    @Test
    void cumulates_a_chain_of_republications() {
        // Étant donné l'annonce 1 (100 vues, 5 favoris) republiée sous la 2 (40 vues, 3 favoris),
        //   elle-même republiée sous la 3 (10 vues, 1 favori)
        known(1, 2, 3);
        statistics.addAll(List.of(reading(1, 100, 5, NOV_1), reading(2, 40, 3, NOV_12), reading(3, 10, 1, NOV_17)));
        republications.save(published(1, 2, NOV_10));
        republications.save(published(2, 3, NOV_15));

        // Quand j'ouvre la fiche de l'annonce 3
        StatisticsComparison comparison = showListingDetail.show(new VintedListingId(3)).comparison().orElseThrow();

        // Alors avant : celle de l'annonce 2 ; total cumulé 150 vues et 9 favoris sur deux annonces précédentes
        assertThat(comparison.previousListing()).isEqualTo(new VintedListingId(2));
        assertThat(comparison.republishedAt()).isEqualTo(NOV_15);
        assertThat(comparison.before()).contains(reading(2, 40, 3, NOV_12));
        assertThat(comparison.cumulated()).isEqualTo(new ListingStatistics(150, 9));
        assertThat(comparison.previousListingCount()).isEqualTo(2);
    }

    @Test
    void no_comparison_without_a_published_republication() {
        // Étant donné une annonce jamais republiée, une annonce dont la republication est en erreur ou non publiée,
        //   et l'annonce d'origine d'une republication publiée
        known(1, 2, 3, 4);
        republications.save(attempted(2, RepublicationOutcome.FAILED));
        republications.save(attempted(3, RepublicationOutcome.NOT_PUBLISHED));
        republications.save(published(4, 1, NOV_10));

        // Quand j'ouvre leur fiche, alors aucune n'a de comparaison, sauf la nouvelle annonce
        assertThat(showListingDetail.show(new VintedListingId(2)).comparison()).isEmpty();
        assertThat(showListingDetail.show(new VintedListingId(3)).comparison()).isEmpty();
        assertThat(showListingDetail.show(new VintedListingId(4)).comparison()).isEmpty();
        assertThat(showListingDetail.show(new VintedListingId(1)).comparison()).isPresent();
    }

    @Test
    void missing_readings_count_for_nothing() {
        // Étant donné l'annonce 1 jamais relevée, republiée sous l'annonce 2 pas encore relevée
        known(1, 2);
        republications.save(published(1, 2, NOV_10));

        // Quand j'ouvre la fiche de l'annonce 2
        StatisticsComparison comparison = showListingDetail.show(new VintedListingId(2)).comparison().orElseThrow();

        // Alors avant et après sont absents et le total cumulé est de 0 vue et 0 favori
        assertThat(comparison.before()).isEmpty();
        assertThat(comparison.after()).isEmpty();
        assertThat(comparison.cumulated()).isEqualTo(new ListingStatistics(0, 0));
    }

    @Test
    void a_chain_that_loops_stops_on_a_listing_already_seen() {
        // Cas limite (impossible sur Vinted) : 1 republiée sous 2, et 2 republiée sous 1
        known(1, 2);
        statistics.addAll(List.of(reading(1, 10, 1, NOV_9), reading(2, 20, 2, NOV_15)));
        republications.save(published(1, 2, NOV_10));
        republications.save(published(2, 1, NOV_12));

        StatisticsComparison comparison = showListingDetail.show(new VintedListingId(2)).comparison().orElseThrow();

        assertThat(comparison.previousListingCount()).isEqualTo(1);
        assertThat(comparison.cumulated()).isEqualTo(new ListingStatistics(30, 3));
    }

    private void known(long... ids) {
        listings.saveAll(Arrays.stream(ids).mapToObj(id -> Listing.seen(anActiveListing(id), NOV_1))
                .toList());
    }

    private static ListingRepublication published(long listing, long newListing, Instant publishedAt) {
        return new ListingRepublication(new ListingRepublicationId(UUID.randomUUID()), new VintedListingId(listing),
                NOV_1, publishedAt.minusSeconds(60), RepublicationOutcome.PUBLISHED, Optional.of(publishedAt),
                Optional.of(new VintedListingId(newListing)), Optional.empty(), WriteOrigin.RUN);
    }

    private static ListingRepublication attempted(long listing, RepublicationOutcome outcome) {
        return new ListingRepublication(new ListingRepublicationId(UUID.randomUUID()), new VintedListingId(listing),
                NOV_1, NOV_9, outcome, Optional.empty(), Optional.empty(),
                outcome == RepublicationOutcome.NOT_PUBLISHED ? Optional.of(NOV_10) : Optional.empty(),
                        WriteOrigin.RUN);
    }

    private static StatisticsReading reading(long listing, int views, int favourites, Instant readAt) {
        return new StatisticsReading(new VintedListingId(listing), new ListingStatistics(views, favourites), readAt);
    }
}
