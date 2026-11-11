package fr.vinted.republisher.adapter.in.web;

import fr.vinted.republisher.application.backup.ShowListingBackupPhoto;
import fr.vinted.republisher.application.backup.ValidateListingBackup;
import fr.vinted.republisher.application.deletion.ShowListingDeletion;
import fr.vinted.republisher.domain.deletion.DeletionMode;
import fr.vinted.republisher.domain.backup.ListingBackup;
import fr.vinted.republisher.domain.backup.ListingReading;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.fakes.InMemoryBackupValidationRepository;
import fr.vinted.republisher.fakes.InMemoryListingBackupRepository;
import fr.vinted.republisher.fakes.InMemoryListingDeletionRepository;
import fr.vinted.republisher.fakes.InMemoryListingRepository;
import fr.vinted.republisher.fakes.InMemoryPhotoStorage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static fr.vinted.republisher.domain.backup.BackupFixtures.aContent;
import static fr.vinted.republisher.domain.backup.BackupFixtures.aPhoto;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.ACTIVE;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.aListing;
import static org.assertj.core.api.Assertions.assertThat;

/** The API of a listing's backup answers as described by the OpenAPI contract. */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@WebMvcTest(ListingBackupApiController.class)
class ListingBackupApiControllerTest {

    private static final Instant SCANNED_AT = Instant.parse("2026-11-10T09:00:00Z");
    private static final Instant SAVED_AT = Instant.parse("2026-11-10T10:00:00Z");
    private static final Instant NOW = Instant.parse("2026-11-11T10:00:00Z");

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private InMemoryListingRepository listings;

    @Autowired
    private InMemoryListingBackupRepository listingBackups;

    @Autowired
    private InMemoryPhotoStorage photos;

    @Test
    void shows_the_verified_backup_with_its_labels_and_photos() {
        aKeptBackup("Pull rayé 1", "12.50");

        assertThat(mvc.get().uri("/api/listings/1/backup"))
                .hasStatusOk()
                .bodyJson().isStrictlyEqualTo("""
                        {"vintedId": 1, "savedAt": "2026-11-10T10:00:00Z", "deletionDryRun": true,
                         "verification": {"passed": true, "failures": []},
                         "content": {"title": "Pull rayé 1", "description": "Porté deux fois",
                                     "price": {"amount": 12.50, "currency": "EUR"},
                                     "category": "Chaussures bébé", "brand": "Kiabi", "condition": "Très bon état",
                                     "size": "", "colors": "Blanc", "packageSize": "Petit"},
                         "photos": [{"rank": 0, "url": "/api/listings/1/backup/photos/0"},
                                    {"rank": 1, "url": "/api/listings/1/backup/photos/1"}]}""");
    }

    @Test
    void shows_every_failure_of_the_verification() {
        ListingBackup backup = aKeptBackup("Robe bleue", "12.00");
        photos.lose(1, backup.photos().get(1));

        assertThat(mvc.get().uri("/api/listings/1/backup"))
                .hasStatusOk()
                .bodyJson().isLenientlyEqualTo("""
                        {"verification": {"passed": false, "failures": [
                          {"kind": "PHOTO_MISSING", "rank": 1},
                          {"kind": "TITLE_CHANGED", "savedTitle": "Robe bleue", "scannedTitle": "Pull rayé 1"},
                          {"kind": "PRICE_CHANGED", "savedPrice": {"amount": 12.00, "currency": "EUR"},
                                                    "scannedPrice": {"amount": 12.50, "currency": "EUR"}}]}}""");
    }

    @Test
    void listing_without_backup_is_not_found() {
        assertThat(mvc.get().uri("/api/listings/1/backup")).hasStatus(404);
        assertThat(mvc.put().uri("/api/listings/1/backup/validation").contentType(MediaType.APPLICATION_JSON)
                .content("{\"savedAt\": \"2026-11-10T10:00:00Z\"}")).hasStatus(404);
    }

    @Test
    void serves_the_kept_file_byte_for_byte_with_its_type() {
        aKeptBackup("Pull rayé 1", "12.50");

        assertThat(mvc.get().uri("/api/listings/1/backup/photos/1"))
                .hasStatusOk()
                .hasContentType("image/webp")
                .body().isEqualTo(aPhoto("back").content());
    }

    @Test
    void missing_photo_is_not_found() {
        aKeptBackup("Pull rayé 1", "12.50");

        assertThat(mvc.get().uri("/api/listings/1/backup/photos/2")).hasStatus(404);
        assertThat(mvc.get().uri("/api/listings/2/backup/photos/0")).hasStatus(404);
    }

    @Test
    void validates_the_version_seen() {
        aKeptBackup("Pull rayé 1", "12.50");

        assertThat(validate("2026-11-10T10:00:00Z"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.validatedAt").isEqualTo("2026-11-11T10:00:00Z");
    }

    @Test
    void replaced_backup_is_a_conflict() {
        aKeptBackup("Pull rayé 1", "12.50");

        assertThat(validate("2026-11-09T10:00:00Z"))
                .hasStatus(409)
                .bodyJson().isStrictlyEqualTo("{\"reason\": \"BACKUP_REPLACED\"}");
    }

    @Test
    void failed_verification_is_refused_with_its_failures() {
        ListingBackup backup = aKeptBackup("Pull rayé 1", "12.50");
        photos.lose(1, backup.photos().get(0));

        assertThat(validate("2026-11-10T10:00:00Z"))
                .hasStatus(422)
                .bodyJson().isStrictlyEqualTo("""
                        {"reason": "VERIFICATION_FAILED",
                         "verification": {"passed": false, "failures": [{"kind": "PHOTO_MISSING", "rank": 0}]}}""");
    }

    @Test
    void request_without_the_date_seen_is_invalid() {
        aKeptBackup("Pull rayé 1", "12.50");

        assertThat(mvc.put().uri("/api/listings/1/backup/validation").contentType(MediaType.APPLICATION_JSON)
                .content("{}")).hasStatus(400);
    }

    @Test
    void validation_requested_by_another_site_is_refused() {
        aKeptBackup("Pull rayé 1", "12.50");

        assertThat(mvc.put().uri("/api/listings/1/backup/validation").header("Origin", "https://example.test")
                .contentType(MediaType.APPLICATION_JSON).content("{\"savedAt\": \"2026-11-10T10:00:00Z\"}"))
                .hasStatus(403);
        assertThat(mvc.get().uri("/api/listings/1/backup")).bodyJson().doesNotHavePath("$.validatedAt");
    }

    private MockMvcTester.MockMvcRequestBuilder validate(String savedAt) {
        return mvc.put().uri("/api/listings/1/backup/validation").contentType(MediaType.APPLICATION_JSON)
                .content("{\"savedAt\": \"" + savedAt + "\"}");
    }

    private ListingBackup aKeptBackup(String title, String price) {
        listings.saveAll(List.of(Listing.seen(aListing(1, "Pull rayé 1", "12.50", ACTIVE), SCANNED_AT)));
        ListingReading reading = new ListingReading(aContent(1, title, "Porté deux fois", price),
                List.of(aPhoto("front"), aPhoto("back")));
        reading.photos().forEach(photo -> photos.store(reading.content().id(), photo));
        ListingBackup backup = ListingBackup.of(reading, SAVED_AT);
        listingBackups.save(backup);
        return backup;
    }

    @TestConfiguration
    static class Fakes {

        @Bean
        InMemoryListingRepository listings() {
            return new InMemoryListingRepository();
        }

        @Bean
        InMemoryListingBackupRepository listingBackups() {
            return new InMemoryListingBackupRepository();
        }

        @Bean
        InMemoryPhotoStorage photos() {
            return new InMemoryPhotoStorage();
        }

        @Bean
        InMemoryBackupValidationRepository validations() {
            return new InMemoryBackupValidationRepository();
        }

        @Bean
        ShowListingDeletion showListingDeletion(InMemoryListingRepository listings,
                                                InMemoryListingBackupRepository listingBackups,
                                                InMemoryPhotoStorage photos,
                                                InMemoryBackupValidationRepository validations) {
            return new ShowListingDeletion(listings, listingBackups, photos, validations,
                    new InMemoryListingDeletionRepository(), DeletionMode.DRY_RUN);
        }

        @Bean
        ValidateListingBackup validateListingBackup(InMemoryListingRepository listings,
                                                    InMemoryListingBackupRepository listingBackups,
                                                    InMemoryPhotoStorage photos,
                                                    InMemoryBackupValidationRepository validations) {
            return new ValidateListingBackup(listings, listingBackups, photos, validations,
                    Clock.fixed(NOW, ZoneOffset.UTC));
        }

        @Bean
        ShowListingBackupPhoto showListingBackupPhoto(InMemoryListingBackupRepository listingBackups,
                                                      InMemoryPhotoStorage photos) {
            return new ShowListingBackupPhoto(listingBackups, photos);
        }
    }
}
