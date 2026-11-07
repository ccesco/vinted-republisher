package fr.vinted.republisher.adapter.out.persistence;

import fr.vinted.republisher.domain.backup.CategoryDetails;
import fr.vinted.republisher.domain.backup.FormOption;
import fr.vinted.republisher.domain.backup.FormOptions;
import fr.vinted.republisher.domain.backup.ListingBackup;
import fr.vinted.republisher.domain.backup.ListingBackupRepository;
import fr.vinted.republisher.domain.backup.ListingContent;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.ListingRepository;
import fr.vinted.republisher.domain.wardrobe.Price;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static fr.vinted.republisher.domain.backup.BackupFixtures.aContent;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.anActiveListing;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/** AT-05-12, on a disposable PostgreSQL with the real changelog, never the real data. */
@Tag("docker")
@Testcontainers
@SpringBootTest(properties = {
        "app.data-dir=target/test-data",
        "chrome.path=not-started-by-these-tests",
        "chrome.debug-port=9222",
        "vinted.wardrobe-url=http://127.0.0.1/member/1"})
class JpaListingBackupRepositoryTest {

    private static final Instant SAVED_AT = Instant.parse("2026-11-07T10:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    @Autowired
    private ListingBackupRepository backups;

    @Autowired
    private ListingRepository listings;

    @Autowired
    private JdbcTemplate database;

    @BeforeEach
    void startWithTwoListingsNeverBackedUp() {
        database.update("DELETE FROM listing_content");
        database.update("DELETE FROM listing");
        listings.saveAll(List.of(Listing.seen(anActiveListing(1), SAVED_AT), Listing.seen(anActiveListing(2), SAVED_AT)));
    }

    @Test
    void gives_back_every_field_as_it_was_saved() {
        // Étant donné une sauvegarde terminée
        ListingBackup full = new ListingBackup(aFullContent(1), SAVED_AT);
        ListingBackup minimal = new ListingBackup(aMinimalContent(2), SAVED_AT);
        backups.save(full);
        backups.save(minimal);
        // Quand l'application redémarre
        // Alors le contenu sauvegardé de chaque annonce est relu à l'identique, colonne par colonne
        assertThat(backups.findAll()).containsExactlyInAnyOrder(full, minimal);
    }

    @Test
    void a_new_backup_of_a_listing_replaces_the_previous_one() {
        backups.save(new ListingBackup(aContent(1, "Pull rayé", "Porté deux fois"), SAVED_AT));
        ListingBackup newer = new ListingBackup(aContent(1, "Pull rayé", "Porté trois fois"), SAVED_AT.plusSeconds(60));

        backups.save(newer);

        assertThat(backups.findAll()).containsExactly(newer);
    }

    @Test
    void each_value_has_its_own_typed_column() {
        backups.save(new ListingBackup(aFullContent(1), SAVED_AT));

        assertThat(database.queryForObject(
                "SELECT pg_typeof(color_ids)::text || ' ' || pg_typeof(price)::text || ' ' || pg_typeof(catalog_id)::text"
                        + " FROM listing_content", String.class))
                .isEqualTo("bigint[] numeric bigint");
    }

    @Test
    void a_backup_belongs_to_a_known_listing() {
        Throwable attempt = catchThrowable(() -> backups.save(new ListingBackup(aContent(99), SAVED_AT)));

        assertThat(attempt).isInstanceOf(DataAccessException.class);
    }

    private static ListingContent aFullContent(long id) {
        return new ListingContent(new VintedListingId(id), "Livre de recettes", "Comme neuf",
                new Price(new BigDecimal("8.00"), "EUR"),
                new FormOption(2312, "Livres de cuisine"),
                Optional.of(new FormOption(60, "Kiabi")),
                new FormOption(6, "Neuf sans étiquette"),
                Optional.of(new FormOption(588, "")),
                Optional.of(new FormOptions(List.of(12L, 1L), "Blanc, Noir")),
                Optional.of(new FormOptions(List.of(44L), "Coton")),
                new FormOption(2, "Moyen"),
                new CategoryDetails(Optional.of("9782070612758"), Optional.of("Recettes"), Optional.of("Une autrice"),
                        Optional.of("Fabricant"), Optional.of("Libellé du fabricant"), Optional.of("Modèle"),
                        Optional.of(new BigDecimal("21.5")), Optional.of(new BigDecimal("30")), Optional.of("cm")));
    }

    private static ListingContent aMinimalContent(long id) {
        return new ListingContent(new VintedListingId(id), "Pull", "",
                new Price(new BigDecimal("1.00"), "EUR"),
                new FormOption(1653, "Chaussures bébé"),
                Optional.empty(),
                new FormOption(2, "Très bon état"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                new FormOption(1, "Petit"),
                CategoryDetails.none());
    }
}
