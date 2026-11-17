package fr.vinted.republisher.acceptance;

import fr.vinted.republisher.DisposablePostgres;
import fr.vinted.republisher.VintedRepublisherApplication;
import fr.vinted.republisher.application.wardrobe.ShowWardrobe;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.ListingRepository;
import fr.vinted.republisher.domain.wardrobe.WardrobeOverview;
import fr.vinted.republisher.domain.wardrobe.WardrobeScan;
import fr.vinted.republisher.domain.wardrobe.WardrobeScanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import fr.vinted.republisher.application.inventory.ShowListingInventory;
import fr.vinted.republisher.domain.inventory.InventoryCriteria;
import fr.vinted.republisher.domain.inventory.InventoryEntry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.time.Instant;
import java.util.List;

import static fr.vinted.republisher.DisposablePostgres.POSTGRES;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.anActiveListing;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("docker")
class ListingPersistenceAcceptanceTest {

    private static final Instant SCANNED_AT = Instant.parse("2026-11-06T10:00:00Z");
    private static final Instant FINISHED_AT = SCANNED_AT.plusSeconds(60);

    @BeforeEach
    void startFromEmptyTables() {
        DisposablePostgres.empty();
    }

    @Test
    void inventory_survives_a_restart() {
        // Étant donné un scan terminé
        Listing scanned = Listing.seen(anActiveListing(1), SCANNED_AT);
        try (ConfigurableApplicationContext application = startApplication()) {
            application.getBean(ListingRepository.class).saveAll(List.of(scanned));
            application.getBean(WardrobeScanRepository.class)
                    .save(WardrobeScan.startedAt(SCANNED_AT).finished(FINISHED_AT));
        }

        // Quand l'application redémarre
        try (ConfigurableApplicationContext restarted = startApplication()) {
            WardrobeOverview overview = restarted.getBean(ShowWardrobe.class).show();

            // Alors l'inventaire et la date du dernier scan restent affichés
            assertThat(restarted.getBean(ShowListingInventory.class).show(InventoryCriteria.defaults()))
                    .extracting(InventoryEntry::listing).containsExactly(scanned);
            assertThat(overview.lastScanAt()).contains(FINISHED_AT);
        }
    }

    private static ConfigurableApplicationContext startApplication() {
        return new SpringApplicationBuilder(VintedRepublisherApplication.class)
                .web(WebApplicationType.NONE)
                .run(
                        "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                        "--spring.datasource.username=" + POSTGRES.getUsername(),
                        "--spring.datasource.password=" + POSTGRES.getPassword(),
                        "--app.data-dir=target/test-data",
                        "--chrome.path=not-started-by-these-tests",
                        "--chrome.debug-port=9222",
                        "--vinted.wardrobe-url=http://127.0.0.1/member/1");
    }
}
