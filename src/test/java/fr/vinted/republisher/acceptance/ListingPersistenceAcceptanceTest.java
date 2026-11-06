package fr.vinted.republisher.acceptance;

import fr.vinted.republisher.VintedRepublisherApplication;
import fr.vinted.republisher.application.wardrobe.ShowWardrobe;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.ListingRepository;
import fr.vinted.republisher.domain.wardrobe.WardrobeOverview;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.List;

import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.anActiveListing;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("docker")
@Testcontainers
class ListingPersistenceAcceptanceTest {

    private static final Instant SCANNED_AT = Instant.parse("2026-11-06T10:00:00Z");

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    @Test
    void inventory_survives_a_restart() {
        // Étant donné un scan réussi
        Listing scanned = Listing.seen(anActiveListing(1), SCANNED_AT);
        try (ConfigurableApplicationContext application = startApplication()) {
            application.getBean(ListingRepository.class).saveAll(List.of(scanned));
        }

        // Quand l'application redémarre
        try (ConfigurableApplicationContext restarted = startApplication()) {
            WardrobeOverview overview = restarted.getBean(ShowWardrobe.class).show();

            // Alors l'inventaire et la date du dernier scan restent affichés
            assertThat(overview.listings()).containsExactly(scanned);
            assertThat(overview.lastScanAt()).contains(SCANNED_AT);
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
