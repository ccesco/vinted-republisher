package fr.vinted.republisher.adapter.out.persistence;

import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.ListingRepository;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.List;

import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.ACTIVE;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.aListing;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.anActiveListing;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/** On a disposable PostgreSQL with the real changelog, never the real data. */
@Tag("docker")
@Testcontainers
@SpringBootTest(properties = {
        "app.data-dir=target/test-data",
        "chrome.path=not-started-by-these-tests",
        "chrome.debug-port=9222",
        "vinted.wardrobe-url=http://127.0.0.1/member/1"})
class JpaListingRepositoryTest {

    private static final Instant SCANNED_AT = Instant.parse("2026-11-06T10:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    @Autowired
    private ListingRepository listings;

    @Autowired
    private JdbcTemplate database;

    @BeforeEach
    void startFromAnEmptyInventory() {
        database.update("DELETE FROM listing_content");
        database.update("DELETE FROM listing");
    }

    @Test
    void gives_back_a_listing_as_it_was_saved() {
        Listing listing = Listing.seen(aListing(90000000001L, "Pull rayé", "1.5", ACTIVE), SCANNED_AT);

        listings.saveAll(List.of(listing));

        assertThat(listings.findAll()).containsExactly(listing);
    }

    @Test
    void finds_a_listing_by_its_identifier() {
        Listing listing = Listing.seen(aListing(1, "Pull rayé", "1.5", ACTIVE), SCANNED_AT);
        listings.saveAll(List.of(listing));

        assertThat(listings.find(new VintedListingId(1))).contains(listing);
        assertThat(listings.find(new VintedListingId(2))).isEmpty();
    }

    @Test
    void updates_a_known_listing_without_duplicating_it() {
        listings.saveAll(List.of(Listing.seen(aListing(1, "Ancien titre", "10.00", ACTIVE), SCANNED_AT)));
        Listing updated = Listing.seen(aListing(1, "Nouveau titre", "8.00", ACTIVE), SCANNED_AT.plusSeconds(60));

        listings.saveAll(List.of(updated));

        assertThat(listings.findAll()).containsExactly(updated);
    }

    @Test
    void keeps_an_absent_listing() {
        Listing absent = Listing.seen(anActiveListing(1), SCANNED_AT).absent();

        listings.saveAll(List.of(absent));

        assertThat(listings.findAll()).containsExactly(absent);
    }

    @Test
    void saves_all_or_nothing() {
        Listing good = Listing.seen(anActiveListing(1), SCANNED_AT);
        Listing refusedByPostgres = Listing.seen(aListing(2, "Pull\u0000rayé", "1.00", ACTIVE), SCANNED_AT);

        Throwable failure = catchThrowable(() -> listings.saveAll(List.of(good, refusedByPostgres)));

        assertThat(failure).isInstanceOf(DataAccessException.class);
        assertThat(listings.findAll()).isEmpty();
    }
}
