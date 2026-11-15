package fr.vinted.republisher.adapter.out.persistence;

import fr.vinted.republisher.domain.backup.ListingBackup;
import fr.vinted.republisher.domain.backup.ListingBackupRepository;
import fr.vinted.republisher.domain.republication.ListingRepublication;
import fr.vinted.republisher.domain.republication.ListingRepublicationRepository;
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
class JpaListingRepublicationRepositoryTest {

    private static final VintedListingId PUBLISHED_LISTING = new VintedListingId(10208778166L);
    private static final VintedListingId FAILED_LISTING = new VintedListingId(10208778167L);
    private static final VintedListingId NEW_LISTING = new VintedListingId(10300000001L);
    private static final Instant SAVED_AT = Instant.parse("2026-11-10T10:00:00.123456Z");
    private static final Instant CLICKED_AT = Instant.parse("2026-11-11T10:06:00.654321Z");
    private static final Instant PROVEN_AT = Instant.parse("2026-11-11T10:06:20.111111Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    @Autowired
    private ListingRepublicationRepository republications;

    @Autowired
    private ListingRepository listings;

    @Autowired
    private ListingBackupRepository listingBackups;

    @Autowired
    private JdbcTemplate database;

    @BeforeEach
    void startWithTwoBackupsAndNoRepublication() {
        database.update("DELETE FROM listing_republication");
        database.update("DELETE FROM listing_content");
        database.update("DELETE FROM listing");
        for (VintedListingId listing : List.of(PUBLISHED_LISTING, FAILED_LISTING)) {
            listings.saveAll(List.of(Listing.seen(anActiveListing(listing.value()), SAVED_AT).absent()));
            listingBackups.save(aBackup(listing.value(), SAVED_AT));
        }
        listings.saveAll(List.of(Listing.seen(anActiveListing(NEW_LISTING.value()), PROVEN_AT)));
    }

    @Test
    void nothing_is_found_before_the_first_attempt() {
        assertThat(republications.findLatest(PUBLISHED_LISTING)).isEmpty();
    }

    @Test
    void republicationsSurviveRestart() {
        // Étant donné une republication réussie et une autre en erreur
        ListingRepublication attempt = ListingRepublication.attempted(backupOf(PUBLISHED_LISTING), CLICKED_AT);
        republications.save(attempt);
        ListingRepublication published = attempt.published(NEW_LISTING, PROVEN_AT);
        republications.save(published);
        ListingRepublication failed = ListingRepublication.attempted(backupOf(FAILED_LISTING), CLICKED_AT);
        republications.save(failed);
        // Quand l'outil redémarre (rien n'est gardé en mémoire : tout est relu en base)
        // Alors les deux sont relues à l'identique, avec le lien entre ancienne et nouvelle annonce
        assertThat(republications.findLatest(PUBLISHED_LISTING)).contains(published);
        assertThat(republications.findLatest(FAILED_LISTING)).contains(failed);
        assertThat(republications.findAllLatest()).containsExactlyInAnyOrder(published, failed);
        assertThat(database.queryForObject("SELECT count(*) FROM listing_republication", Long.class)).isEqualTo(2);
    }

    @Test
    void a_listing_cannot_be_republished_twice() {
        republications.save(ListingRepublication.attempted(backupOf(FAILED_LISTING), CLICKED_AT));

        assertThatThrownBy(() -> republications.save(
                ListingRepublication.attempted(backupOf(FAILED_LISTING), CLICKED_AT.plusSeconds(60))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void a_listing_checked_not_published_can_be_attempted_again_and_keeps_its_history() {
        ListingRepublication notPublished = ListingRepublication.attempted(backupOf(FAILED_LISTING), CLICKED_AT)
                .notPublished(PROVEN_AT);
        republications.save(notPublished);
        ListingRepublication again =
                ListingRepublication.attempted(backupOf(FAILED_LISTING), CLICKED_AT.plusSeconds(600));
        republications.save(again);

        assertThat(republications.findLatest(FAILED_LISTING)).contains(again);
        assertThat(republications.findAllLatest()).containsExactly(again);
        assertThat(database.queryForObject("SELECT checked_at FROM listing_republication WHERE id = ?", Instant.class,
                notPublished.id().value())).isEqualTo(PROVEN_AT);
    }

    @Test
    void the_new_listings_of_published_republications_are_found() {
        ListingRepublication attempt = ListingRepublication.attempted(backupOf(PUBLISHED_LISTING), CLICKED_AT);
        republications.save(attempt.published(NEW_LISTING, PROVEN_AT));
        republications.save(ListingRepublication.attempted(backupOf(FAILED_LISTING), CLICKED_AT));

        assertThat(republications.findNewListings()).containsExactly(NEW_LISTING);
    }

    @Test
    void the_new_listing_must_be_known() {
        ListingRepublication attempt = ListingRepublication.attempted(backupOf(PUBLISHED_LISTING), CLICKED_AT);
        republications.save(attempt);

        assertThatThrownBy(() -> republications.save(attempt.published(new VintedListingId(99), PROVEN_AT)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private ListingBackup backupOf(VintedListingId listing) {
        return listingBackups.find(listing).orElseThrow();
    }
}
