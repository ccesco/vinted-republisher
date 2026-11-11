package fr.vinted.republisher.adapter.out.persistence;

import fr.vinted.republisher.domain.backup.ListingBackup;
import fr.vinted.republisher.domain.backup.ListingBackupRepository;
import fr.vinted.republisher.domain.deletion.ListingDeletion;
import fr.vinted.republisher.domain.deletion.ListingDeletionRepository;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.ListingRepository;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.List;

import static fr.vinted.republisher.domain.backup.BackupFixtures.aBackup;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.anActiveListing;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** On a disposable PostgreSQL with the real changelog, never the real data. */
@Tag("docker")
@Testcontainers
@SpringBootTest(properties = {
        "app.data-dir=target/test-data",
        "chrome.path=not-started-by-these-tests",
        "chrome.debug-port=9222",
        "vinted.wardrobe-url=http://127.0.0.1/member/1"})
class JpaListingDeletionRepositoryTest {

    private static final VintedListingId LISTING = new VintedListingId(10208778166L);
    private static final Instant SAVED_AT = Instant.parse("2026-11-10T10:00:00.123456Z");
    private static final Instant CLICKED_AT = Instant.parse("2026-11-11T10:00:00.654321Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    @Autowired
    private ListingDeletionRepository deletions;

    @Autowired
    private ListingRepository listings;

    @Autowired
    private ListingBackupRepository listingBackups;

    @Autowired
    private JdbcTemplate database;

    private ListingBackup backup;

    @BeforeEach
    void startWithABackupAndNoDeletion() {
        database.update("DELETE FROM listing_deletion");
        database.update("DELETE FROM backup_validation");
        database.update("DELETE FROM listing_content");
        database.update("DELETE FROM listing");
        listings.saveAll(List.of(Listing.seen(anActiveListing(LISTING.value()), SAVED_AT)));
        backup = aBackup(LISTING.value(), SAVED_AT);
        listingBackups.save(backup);
    }

    @Test
    void nothing_is_found_before_the_first_attempt() {
        assertThat(deletions.findLatest(LISTING)).isEmpty();
    }

    @Test
    void gives_back_an_unconfirmed_and_then_a_confirmed_deletion_as_they_were_saved() {
        ListingDeletion attempt = ListingDeletion.attempted(backup, CLICKED_AT);
        deletions.save(attempt);
        assertThat(deletions.findLatest(LISTING)).contains(attempt);

        ListingDeletion confirmed = attempt.confirmed(CLICKED_AT.plusSeconds(5));
        deletions.save(confirmed);
        assertThat(deletions.findLatest(LISTING)).contains(confirmed);
        assertThat(database.queryForObject("SELECT count(*) FROM listing_deletion", Long.class)).isEqualTo(1);
    }

    @Test
    void the_latest_attempt_is_the_last_one_clicked() {
        ListingDeletion notDone = ListingDeletion.attempted(backup, CLICKED_AT).notDone(CLICKED_AT.plusSeconds(60));
        deletions.save(notDone);
        ListingDeletion second = ListingDeletion.attempted(backup, CLICKED_AT.plusSeconds(120));
        deletions.save(second);

        assertThat(deletions.findLatest(LISTING)).contains(second);
    }

    @Test
    void a_listing_cannot_have_two_confirmed_deletions() {
        deletions.save(ListingDeletion.attempted(backup, CLICKED_AT).confirmed(CLICKED_AT));

        assertThatThrownBy(() -> deletions.save(
                ListingDeletion.attempted(backup, CLICKED_AT.plusSeconds(60)).confirmed(CLICKED_AT)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
