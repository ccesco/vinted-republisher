package fr.vinted.republisher.adapter.out.persistence;

import fr.vinted.republisher.domain.backup.BackupValidation;
import fr.vinted.republisher.domain.backup.BackupValidationRepository;
import fr.vinted.republisher.domain.backup.ListingBackup;
import fr.vinted.republisher.domain.backup.ListingBackupRepository;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.ListingRepository;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.List;

import static fr.vinted.republisher.domain.backup.BackupFixtures.aBackup;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.anActiveListing;
import static org.assertj.core.api.Assertions.assertThat;

/** On a disposable PostgreSQL with the real changelog, never the real data. */
@Tag("docker")
@Testcontainers
@SpringBootTest(properties = {
        "app.data-dir=target/test-data",
        "chrome.path=not-started-by-these-tests",
        "chrome.debug-port=9222",
        "vinted.wardrobe-url=http://127.0.0.1/member/1"})
class JpaBackupValidationRepositoryTest {

    private static final VintedListingId LISTING = new VintedListingId(10208778166L);
    private static final Instant SAVED_AT = Instant.parse("2026-11-10T10:00:00.123456Z");
    private static final Instant VALIDATED_AT = Instant.parse("2026-11-11T10:00:00.654321Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    @Autowired
    private BackupValidationRepository validations;

    @Autowired
    private ListingRepository listings;

    @Autowired
    private ListingBackupRepository listingBackups;

    @Autowired
    private JdbcTemplate database;

    @BeforeEach
    void startWithoutAnyBackup() {
        database.update("DELETE FROM backup_validation");
        database.update("DELETE FROM listing_content");
        database.update("DELETE FROM listing");
        listings.saveAll(List.of(Listing.seen(anActiveListing(LISTING.value()), SAVED_AT)));
    }

    @Test
    void nothing_is_found_before_the_first_validation() {
        assertThat(validations.findAll()).isEmpty();
        assertThat(validations.find(LISTING)).isEmpty();
    }

    @Test
    void gives_back_the_validation_as_it_was_saved() {
        ListingBackup backup = aBackup(LISTING.value(), SAVED_AT);
        listingBackups.save(backup);

        validations.save(BackupValidation.of(backup, VALIDATED_AT));

        BackupValidation expected = new BackupValidation(LISTING, SAVED_AT, VALIDATED_AT);
        assertThat(validations.find(LISTING)).contains(expected);
        assertThat(validations.findAll()).containsExactly(expected);
    }

    @Test
    void new_validation_replaces_the_previous_one_of_the_listing() {
        ListingBackup first = aBackup(LISTING.value(), SAVED_AT);
        listingBackups.save(first);
        validations.save(BackupValidation.of(first, VALIDATED_AT));
        ListingBackup second = aBackup(LISTING.value(), VALIDATED_AT);
        listingBackups.save(second);

        validations.save(BackupValidation.of(second, VALIDATED_AT.plusSeconds(60)));

        assertThat(validations.findAll()).containsExactly(BackupValidation.of(second, VALIDATED_AT.plusSeconds(60)));
    }

    @Test
    void replacing_the_backup_keeps_the_validation_of_the_previous_version() {
        ListingBackup first = aBackup(LISTING.value(), SAVED_AT);
        listingBackups.save(first);
        validations.save(BackupValidation.of(first, VALIDATED_AT));

        listingBackups.save(aBackup(LISTING.value(), VALIDATED_AT));

        assertThat(validations.find(LISTING)).contains(BackupValidation.of(first, VALIDATED_AT));
    }
}
