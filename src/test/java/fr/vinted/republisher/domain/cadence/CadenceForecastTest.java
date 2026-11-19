package fr.vinted.republisher.domain.cadence;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CadenceForecastTest {

    private static final Instant NOW = Instant.parse("2026-11-19T10:00:00Z");
    private static final WriteCadence CADENCE = new WriteCadence(Duration.ofSeconds(30), Duration.ofMinutes(2), 2, 3);

    @Test
    void a_write_is_allowed_after_the_interval_since_the_last_write_of_any_kind() {
        CadenceForecast forecast = new CadenceForecast(CADENCE, List.of(), List.of(NOW.minusSeconds(10)));

        assertThat(forecast.allowedAt(VintedWrite.DELETION, NOW)).isEqualTo(NOW.plusSeconds(20));
        assertThat(forecast.allowedAt(VintedWrite.REPUBLICATION, NOW)).isEqualTo(NOW.plusSeconds(110));
    }

    @Test
    void a_planned_write_counts_for_the_next_ones() {
        CadenceForecast forecast = new CadenceForecast(CADENCE, List.of(), List.of())
                .withWriteAt(VintedWrite.DELETION, NOW.plusSeconds(5));

        assertThat(forecast.allowedAt(VintedWrite.DELETION, NOW)).isEqualTo(NOW.plusSeconds(35));
    }

    @Test
    void a_planned_write_counts_against_the_daily_limit_of_its_kind() {
        CadenceForecast forecast = new CadenceForecast(CADENCE, List.of(NOW.minus(Duration.ofHours(23))), List.of())
                .withWriteAt(VintedWrite.DELETION, NOW);

        assertThat(forecast.allowedAt(VintedWrite.DELETION, NOW.plusSeconds(60)))
                .isEqualTo(NOW.plus(Duration.ofHours(1)));
        assertThat(forecast.allowedAt(VintedWrite.REPUBLICATION, NOW.plusSeconds(120)))
                .isEqualTo(NOW.plusSeconds(120));
    }
}
