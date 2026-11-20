package fr.vinted.republisher.adapter.in.web;

import fr.vinted.republisher.adapter.in.web.api.model.BackupFilterDto;
import fr.vinted.republisher.adapter.in.web.api.model.InventorySortDto;
import fr.vinted.republisher.adapter.in.web.api.model.PresenceFilterDto;
import fr.vinted.republisher.adapter.in.web.api.model.SortDirectionDto;
import fr.vinted.republisher.application.inventory.ShowListingInventory;
import fr.vinted.republisher.domain.backup.BackupValidation;
import fr.vinted.republisher.domain.backup.ListingBackup;
import fr.vinted.republisher.domain.backup.ListingReading;
import fr.vinted.republisher.domain.inventory.BackupFilter;
import fr.vinted.republisher.domain.inventory.InventorySort;
import fr.vinted.republisher.domain.inventory.PresenceFilter;
import fr.vinted.republisher.domain.inventory.SortDirection;
import fr.vinted.republisher.domain.republication.ListingRepublication;
import fr.vinted.republisher.domain.republication.RepublicationDelay;
import fr.vinted.republisher.domain.tag.Tag;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.ListingStatistics;
import fr.vinted.republisher.domain.wardrobe.StatisticsReading;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import fr.vinted.republisher.fakes.InMemoryBackupValidationRepository;
import fr.vinted.republisher.fakes.InMemoryListingSaleRepository;
import fr.vinted.republisher.fakes.InMemoryBackupVerificationRepository;
import fr.vinted.republisher.fakes.InMemoryListingBackupRepository;
import fr.vinted.republisher.fakes.InMemoryListingDeletionRepository;
import fr.vinted.republisher.fakes.InMemoryListingRepository;
import fr.vinted.republisher.fakes.InMemoryListingRepublicationRepository;
import fr.vinted.republisher.fakes.InMemoryListingStatisticsRepository;
import fr.vinted.republisher.fakes.InMemoryListingTagRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static fr.vinted.republisher.domain.backup.BackupFixtures.aContent;
import static fr.vinted.republisher.domain.backup.BackupFixtures.aPhoto;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.ACTIVE;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.aListing;
import static org.assertj.core.api.Assertions.assertThat;

/** US-18: the API answers as described by the OpenAPI contract. */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@WebMvcTest(ListingInventoryApiController.class)
class ListingInventoryApiControllerTest {

    private static final Instant SCANNED_AT = Instant.parse("2026-11-06T10:15:30Z");
    private static final Instant SAVED_AT = Instant.parse("2026-11-07T10:00:00Z");
    private static final Instant NOW = Instant.parse("2026-11-17T10:00:00Z");

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private InMemoryListingRepository listings;

    @Autowired
    private InMemoryListingStatisticsRepository statistics;

    @Autowired
    private InMemoryListingBackupRepository backups;

    @Autowired
    private InMemoryBackupValidationRepository validations;

    @Autowired
    private InMemoryListingTagRepository tags;

    @Autowired
    private InMemoryListingRepublicationRepository republications;

    @Test
    void empty_inventory_has_no_listing() {
        assertThat(mvc.get().uri("/api/listings"))
                .hasStatusOk()
                .bodyJson().isStrictlyEqualTo("{\"listings\": []}");
    }

    @Test
    void shows_each_listing_with_its_statistics_backup_validation_and_stage() {
        listings.saveAll(List.of(Listing.seen(aListing(17, "Pull rayé", "1.5", ACTIVE), SCANNED_AT),
                Listing.seen(aListing(18, "Jupe", "3", ACTIVE), SCANNED_AT)));
        statistics.addAll(List.of(
                new StatisticsReading(new VintedListingId(17), new ListingStatistics(12, 3), SCANNED_AT.minusSeconds(60)),
                new StatisticsReading(new VintedListingId(17), new ListingStatistics(15, 4), SCANNED_AT)));
        ListingBackup backup = ListingBackup.of(
                new ListingReading(aContent(17), List.of(aPhoto("A"), aPhoto("B"), aPhoto("C"))), SAVED_AT);
        backups.save(backup);
        validations.save(BackupValidation.of(backup, SAVED_AT.plusSeconds(3600)));

        assertThat(mvc.get().uri("/api/listings"))
                .hasStatusOk()
                .bodyJson().isStrictlyEqualTo("""
                        {"listings": [{"vintedId": 18, "title": "Jupe",
                                       "price": {"amount": 3.00, "currency": "EUR"},
                                       "url": "https://vinted.test/items/18", "present": true,
                                       "stage": "TO_BACK_UP", "tags": [],
                                       "republicationTally": {"published": 0, "notPublished": 0}},
                                      {"vintedId": 17, "title": "Pull rayé",
                                       "price": {"amount": 1.50, "currency": "EUR"},
                                       "url": "https://vinted.test/items/17", "present": true,
                                       "stage": "TO_DELETE", "tags": [],
                                       "republicationTally": {"published": 0, "notPublished": 0},
                                       "savedAt": "2026-11-07T10:00:00Z", "photoCount": 3,
                                       "validatedAt": "2026-11-07T11:00:00Z",
                                       "statistics": {"viewCount": 15, "favouriteCount": 4,
                                                      "readAt": "2026-11-06T10:15:30Z"}}]}""");
    }

    /** AT-44-04 */
    @Test
    void each_listing_bears_the_republication_tally_of_its_article() {
        // Étant donné 17 constatée non publiée puis republiée en 20
        listings.saveAll(List.of(Listing.seen(aListing(17, "Pull rayé", "1.5", ACTIVE), SCANNED_AT).absent(),
                Listing.seen(aListing(20, "Pull rayé", "1.5", ACTIVE), SCANNED_AT)));
        ListingBackup backup = ListingBackup.of(new ListingReading(aContent(17), List.of(aPhoto("A"))), SAVED_AT);
        Instant attemptedAt = NOW.minusSeconds(1200);
        republications.save(ListingRepublication.attempted(backup, attemptedAt).notPublished(attemptedAt.plusSeconds(60)));
        republications.save(ListingRepublication.attempted(backup, attemptedAt.plusSeconds(600))
                .published(new VintedListingId(20), attemptedAt.plusSeconds(660)));

        // Quand le membre demande la liste
        // Alors chaque Listing porte republicationTally, avec published et notPublished
        assertThat(mvc.get().uri("/api/listings?presence=ALL"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.listings[*].republicationTally").isEqualTo(List.of(
                        Map.of("published", 1, "notPublished", 1), Map.of("published", 1, "notPublished", 1)));
    }

    @Test
    void lists_the_republication_advice() {
        Instant weekAndADayAgo = NOW.minus(Duration.ofDays(8));
        listings.saveAll(List.of(Listing.seen(aListing(17, "Pull rayé", "1.5", ACTIVE), SCANNED_AT)));
        statistics.addAll(List.of(
                new StatisticsReading(new VintedListingId(17), new ListingStatistics(40, 2), weekAndADayAgo),
                new StatisticsReading(new VintedListingId(17), new ListingStatistics(46, 2),
                        weekAndADayAgo.plus(Duration.ofDays(7)))));

        assertThat(mvc.get().uri("/api/listings"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.listings[0].republicationAdvice")
                .isEqualTo(Map.of("viewsGained", 6, "since", weekAndADayAgo.toString()));
    }

    @Test
    void backup_kept_before_photos_has_no_photo_count() {
        listings.saveAll(List.of(Listing.seen(aListing(17, "Pull rayé", "1.5", ACTIVE), SCANNED_AT)));
        backups.save(new ListingBackup(aContent(17), List.of(), SAVED_AT));

        assertThat(mvc.get().uri("/api/listings"))
                .bodyJson().doesNotHavePath("$.listings[0].photoCount");
    }

    @Test
    void passes_the_criteria_to_the_inventory() {
        listings.saveAll(List.of(Listing.seen(aListing(17, "Pull rayé", "1.5", ACTIVE), SCANNED_AT).absent(),
                Listing.seen(aListing(18, "Pull bleu", "3", ACTIVE), SCANNED_AT).absent(),
                Listing.seen(aListing(19, "Jupe", "2", ACTIVE), SCANNED_AT).absent()));

        assertThat(mvc.get().uri("/api/listings?presence=ABSENT&backup=NOT_BACKED_UP&search=PULL&sort=PRICE&direction=ASC"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.listings[*].vintedId").isEqualTo(List.of(17, 18));
    }

    @Test
    void rejects_unknown_values_and_too_long_search() {
        // Quand la page demande GET /api/listings?sort=COLOUR
        // Alors la réponse est 400
        assertThat(mvc.get().uri("/api/listings?sort=COLOUR")).hasStatus(400);
        // Quand elle demande une recherche de 101 caractères
        // Alors la réponse est 400
        assertThat(mvc.get().uri("/api/listings?search=" + "a".repeat(101))).hasStatus(400);
        assertThat(mvc.get().uri("/api/listings?search=" + "a".repeat(100))).hasStatusOk();
    }

    @Test
    void filters_on_tag_and_shows_tags() {
        listings.saveAll(List.of(Listing.seen(aListing(17, "Pull rayé", "1.5", ACTIVE), SCANNED_AT),
                Listing.seen(aListing(18, "Jupe", "3", ACTIVE), SCANNED_AT)));
        tags.add(new Tag("lot"), List.of(new VintedListingId(17), new VintedListingId(18)));
        tags.add(new Tag("été"), List.of(new VintedListingId(17)));

        // Quand elle demande GET /api/listings?tag=Été
        // Alors les annonces renvoyées portent « été » et chacune donne ses tags
        assertThat(mvc.get().uri("/api/listings").param("tag", "Été"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.listings[*].vintedId").isEqualTo(List.of(17));
        assertThat(mvc.get().uri("/api/listings").param("tag", "Été"))
                .bodyJson().extractingPath("$.listings[0].tags").isEqualTo(List.of("été", "lot"));
    }

    @Test
    void blank_tag_means_no_tag_filter_and_too_long_tag_is_refused() {
        listings.saveAll(List.of(Listing.seen(aListing(17, "Pull rayé", "1.5", ACTIVE), SCANNED_AT)));

        assertThat(mvc.get().uri("/api/listings").param("tag", "  "))
                .hasStatusOk().bodyJson().extractingPath("$.listings[*].vintedId").isEqualTo(List.of(17));
        assertThat(mvc.get().uri("/api/listings").param("tag", "a".repeat(31))).hasStatus(400);
        assertThat(mvc.get().uri("/api/listings").param("tag", "a".repeat(101))).hasStatus(400);
    }

    @Test
    void filters_on_stage_and_shows_the_republication() {
        // Étant donné 17 republiée en 20 (connue de l'outil), 18 à vérifier, 19 à sauvegarder
        listings.saveAll(List.of(Listing.seen(aListing(17, "Pull rayé", "1.5", ACTIVE), SCANNED_AT).absent(),
                Listing.seen(aListing(18, "Jupe", "3", ACTIVE), SCANNED_AT).absent(),
                Listing.seen(aListing(19, "Robe", "2", ACTIVE), SCANNED_AT),
                Listing.seen(aListing(20, "Pull rayé", "1.5", ACTIVE), SCANNED_AT)));
        Instant attemptedAt = NOW.minusSeconds(600);
        republications.save(ListingRepublication.attempted(ListingBackup.of(
                        new ListingReading(aContent(17), List.of(aPhoto("A"))), SAVED_AT), attemptedAt)
                .published(new VintedListingId(20), attemptedAt.plusSeconds(60)));
        republications.save(ListingRepublication.attempted(ListingBackup.of(
                new ListingReading(aContent(18), List.of(aPhoto("A"))), SAVED_AT), attemptedAt));

        // Quand la page demande GET /api/listings?presence=ALL&stage=REPUBLISHED
        // Alors seule 17 est renvoyée, avec sa republication et l'adresse de la nouvelle annonce
        assertThat(mvc.get().uri("/api/listings?presence=ALL&stage=REPUBLISHED"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.listings[0].republication").isEqualTo(Map.of(
                        "outcome", "PUBLISHED", "attemptedAt", "2026-11-17T09:50:00Z",
                        "publishedAt", "2026-11-17T09:51:00Z", "newListingId", 20,
                        "newListingUrl", "https://vinted.test/items/20", "backupSavedAt", "2026-11-07T10:00:00Z"));
        assertThat(mvc.get().uri("/api/listings?presence=ALL&stage=REPUBLISHED"))
                .bodyJson().extractingPath("$.listings[*].vintedId").isEqualTo(List.of(17));
        // Et 18, à vérifier, porte sa tentative ; 19 n'a pas de republication
        assertThat(mvc.get().uri("/api/listings?presence=ALL&stage=REPUBLICATION_TO_CHECK"))
                .bodyJson().extractingPath("$.listings[0].republication.attemptedAt")
                .isEqualTo("2026-11-17T09:50:00Z");
        assertThat(mvc.get().uri("/api/listings?stage=TO_BACK_UP"))
                .bodyJson().doesNotHavePath("$.listings[0].republication");
        // Et une étape inconnue est refusée
        assertThat(mvc.get().uri("/api/listings?stage=ARCHIVED")).hasStatus(400);
    }

    @Test
    void contract_knows_every_criterion() {
        assertThat(names(PresenceFilterDto.values())).containsExactly(names(PresenceFilter.values()));
        assertThat(names(BackupFilterDto.values())).containsExactly(names(BackupFilter.values()));
        assertThat(names(InventorySortDto.values())).containsExactly(names(InventorySort.values()));
        assertThat(names(SortDirectionDto.values())).containsExactly(names(SortDirection.values()));
    }

    private static String[] names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toArray(String[]::new);
    }

    @TestConfiguration
    static class InventoryUseCase {

        @Bean
        InMemoryListingRepository listings() {
            return new InMemoryListingRepository();
        }

        @Bean
        InMemoryListingStatisticsRepository statistics() {
            return new InMemoryListingStatisticsRepository();
        }

        @Bean
        InMemoryListingBackupRepository backups() {
            return new InMemoryListingBackupRepository();
        }

        @Bean
        InMemoryBackupValidationRepository validations() {
            return new InMemoryBackupValidationRepository();
        }

        @Bean
        ShowListingInventory showListingInventory(InMemoryListingRepository listings,
                                                  InMemoryListingStatisticsRepository statistics,
                                                  InMemoryListingBackupRepository backups,
                                                  InMemoryBackupValidationRepository validations,
                                                  InMemoryListingTagRepository tags,
                                                  InMemoryListingRepublicationRepository republications) {
            return new ShowListingInventory(listings, statistics, backups, validations,
                    new InMemoryListingDeletionRepository(), republications,
                    new RepublicationDelay(Duration.ofMinutes(5)), Clock.fixed(NOW, ZoneOffset.UTC), tags,
                    new InMemoryBackupVerificationRepository(), new InMemoryListingSaleRepository());
        }

        @Bean
        InMemoryListingRepublicationRepository republications() {
            return new InMemoryListingRepublicationRepository();
        }

        @Bean
        InMemoryListingTagRepository tags() {
            return new InMemoryListingTagRepository();
        }
    }
}
