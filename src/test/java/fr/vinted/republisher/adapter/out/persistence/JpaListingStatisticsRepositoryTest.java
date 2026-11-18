package fr.vinted.republisher.adapter.out.persistence;

import fr.vinted.republisher.ApplicationWithDatabaseTest;
import fr.vinted.republisher.domain.wardrobe.ListingStatistics;
import fr.vinted.republisher.domain.wardrobe.ListingStatisticsRepository;
import fr.vinted.republisher.domain.wardrobe.StatisticsReading;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** On a disposable PostgreSQL with the real changelog, never the real data. */
class JpaListingStatisticsRepositoryTest extends ApplicationWithDatabaseTest {

    private static final Instant FIRST_SCAN = Instant.parse("2026-11-06T10:00:00Z");
    private static final Instant NEXT_SCAN = FIRST_SCAN.plus(Duration.ofDays(1));

    @Autowired
    private ListingStatisticsRepository statistics;

    @Autowired
    private JdbcTemplate database;

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
    void latest_reading_as_of_a_date_ignores_later_readings() {
        statistics.addAll(List.of(aReading(1, 12, 3, FIRST_SCAN), aReading(1, 15, 3, NEXT_SCAN),
                aReading(2, 4, 1, NEXT_SCAN)));

        assertThat(statistics.findLatestAsOf(FIRST_SCAN)).containsExactly(aReading(1, 12, 3, FIRST_SCAN));
        assertThat(statistics.findLatestAsOf(FIRST_SCAN.minusSeconds(1))).isEmpty();
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

    @Test
    void history_of_a_listing_is_every_reading_of_it_most_recent_first() {
        statistics.addAll(List.of(aReading(1, 12, 3, FIRST_SCAN), aReading(2, 4, 1, FIRST_SCAN)));
        statistics.addAll(List.of(aReading(1, 15, 3, NEXT_SCAN)));

        assertThat(statistics.historyOf(new VintedListingId(1)))
                .containsExactly(aReading(1, 15, 3, NEXT_SCAN), aReading(1, 12, 3, FIRST_SCAN));
        assertThat(statistics.historyOf(new VintedListingId(3))).isEmpty();
    }

    @Test
    void last_read_at_is_the_date_of_the_most_recent_reading_of_any_listing() {
        assertThat(statistics.findLastReadAt()).isEmpty();
        statistics.addAll(List.of(aReading(2, 4, 1, NEXT_SCAN)));
        statistics.addAll(List.of(aReading(1, 12, 3, FIRST_SCAN)));

        assertThat(statistics.findLastReadAt()).contains(NEXT_SCAN);
    }

    private static StatisticsReading aReading(long listing, int views, int favourites, Instant readAt) {
        return new StatisticsReading(new VintedListingId(listing), new ListingStatistics(views, favourites), readAt);
    }
}
