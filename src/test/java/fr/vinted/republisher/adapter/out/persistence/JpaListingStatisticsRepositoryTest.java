package fr.vinted.republisher.adapter.out.persistence;

import fr.vinted.republisher.domain.wardrobe.ListingStatistics;
import fr.vinted.republisher.domain.wardrobe.ListingStatisticsRepository;
import fr.vinted.republisher.domain.wardrobe.StatisticsReading;
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

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** On a disposable PostgreSQL with the real changelog, never the real data. */
@Tag("docker")
@Testcontainers
@SpringBootTest(properties = {
        "app.data-dir=target/test-data",
        "chrome.path=not-started-by-these-tests",
        "chrome.debug-port=9222",
        "vinted.wardrobe-url=http://127.0.0.1/member/1"})
class JpaListingStatisticsRepositoryTest {

    private static final Instant FIRST_SCAN = Instant.parse("2026-11-06T10:00:00Z");
    private static final Instant NEXT_SCAN = FIRST_SCAN.plus(Duration.ofDays(1));

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    @Autowired
    private ListingStatisticsRepository statistics;

    @Autowired
    private JdbcTemplate database;

    @BeforeEach
    void startWithoutAnyReading() {
        database.update("DELETE FROM listing_statistics_reading");
    }

    @Test
    void nothing_is_found_before_the_first_reading() {
        assertThat(statistics.findLatest()).isEmpty();
    }

    @Test
    void gives_back_readings_as_they_were_added() {
        List<StatisticsReading> readings = List.of(aReading(1, 12, 3, FIRST_SCAN), aReading(2, 0, 0, FIRST_SCAN));

        statistics.addAll(readings);

        assertThat(statistics.findLatest()).containsExactlyInAnyOrderElementsOf(readings);
    }

    @Test
    void latest_reading_of_each_listing_is_the_most_recent_one() {
        statistics.addAll(List.of(aReading(1, 15, 3, NEXT_SCAN), aReading(2, 4, 1, FIRST_SCAN)));
        statistics.addAll(List.of(aReading(1, 12, 3, FIRST_SCAN)));

        assertThat(statistics.findLatest())
                .containsExactlyInAnyOrder(aReading(1, 15, 3, NEXT_SCAN), aReading(2, 4, 1, FIRST_SCAN));
    }

    @Test
    void every_reading_is_kept() {
        statistics.addAll(List.of(aReading(1, 12, 3, FIRST_SCAN)));
        statistics.addAll(List.of(aReading(1, 12, 3, NEXT_SCAN)));

        assertThat(database.queryForObject("SELECT count(*) FROM listing_statistics_reading WHERE listing_id = 1",
                Integer.class)).isEqualTo(2);
    }

    @Test
    void reading_of_a_listing_not_in_the_inventory_is_kept() {
        statistics.addAll(List.of(aReading(999, 1, 0, FIRST_SCAN)));

        assertThat(statistics.findLatest()).containsExactly(aReading(999, 1, 0, FIRST_SCAN));
    }

    private static StatisticsReading aReading(long listing, int views, int favourites, Instant readAt) {
        return new StatisticsReading(new VintedListingId(listing), new ListingStatistics(views, favourites), readAt);
    }
}
