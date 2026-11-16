package fr.vinted.republisher.domain.cadence;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WriteCadenceTest {

    private static final Instant NOW = Instant.parse("2026-11-16T10:00:00Z");
    private static final WriteCadence CADENCE = new WriteCadence(Duration.ofMinutes(2), 2, 3);

    private static Instant minutesAgo(long minutes) {
        return NOW.minus(Duration.ofMinutes(minutes));
    }

    private static Instant hoursAgo(long hours) {
        return NOW.minus(Duration.ofHours(hours));
    }

    @Test
    void first_write_is_allowed() {
        assertThatCode(() -> CADENCE.check(VintedWrite.DELETION, List.of(), List.of(), NOW))
                .doesNotThrowAnyException();
    }

    @Test
    void the_window_starts_24_hours_before_now() {
        assertThat(CADENCE.windowStart(NOW)).isEqualTo(hoursAgo(24));
    }

    @Test
    void a_write_within_the_minimum_interval_of_the_last_one_is_refused_until_it_ends() {
        assertThatThrownBy(() -> CADENCE.check(VintedWrite.DELETION, List.of(minutesAgo(1)),
                List.of(), NOW))
                .isInstanceOfSatisfying(CadenceLimitReached.class,
                        refusal -> assertThat(refusal.allowedAt()).isEqualTo(minutesAgo(1).plus(Duration.ofMinutes(2))));
    }

    @Test
    void the_minimum_interval_holds_whatever_the_kind_of_the_last_write() {
        assertThatThrownBy(() -> CADENCE.check(VintedWrite.DELETION, List.of(), List.of(minutesAgo(1)), NOW))
                .isInstanceOf(CadenceLimitReached.class);
        assertThatThrownBy(() -> CADENCE.check(VintedWrite.REPUBLICATION, List.of(minutesAgo(1)), List.of(), NOW))
                .isInstanceOf(CadenceLimitReached.class);
    }

    @Test
    void a_write_once_the_minimum_interval_ended_is_allowed() {
        assertThatCode(() -> CADENCE.check(VintedWrite.DELETION, List.of(minutesAgo(2)), List.of(), NOW))
                .doesNotThrowAnyException();
    }

    @Test
    void a_write_over_the_daily_limit_of_its_kind_is_refused_until_the_oldest_leaves_the_window() {
        assertThatThrownBy(() -> CADENCE.check(VintedWrite.DELETION, List.of(hoursAgo(5), hoursAgo(23)),
                List.of(), NOW))
                .isInstanceOfSatisfying(CadenceLimitReached.class,
                        refusal -> assertThat(refusal.allowedAt()).isEqualTo(NOW.plus(Duration.ofHours(1))));
    }

    @Test
    void the_place_freed_first_is_the_one_needed_over_the_limit() {
        List<Instant> fourRepublications = List.of(hoursAgo(1), hoursAgo(20), hoursAgo(10), hoursAgo(23));

        assertThatThrownBy(() -> CADENCE.check(VintedWrite.REPUBLICATION, List.of(), fourRepublications, NOW))
                .isInstanceOfSatisfying(CadenceLimitReached.class,
                        refusal -> assertThat(refusal.allowedAt()).isEqualTo(NOW.plus(Duration.ofHours(4))));
    }

    @Test
    void writes_older_than_24_hours_do_not_count() {
        assertThatCode(() -> CADENCE.check(VintedWrite.DELETION, List.of(hoursAgo(25), hoursAgo(24), hoursAgo(5)),
                List.of(), NOW))
                .doesNotThrowAnyException();
    }

    @Test
    void daily_limits_are_per_kind() {
        assertThatCode(() -> CADENCE.check(VintedWrite.REPUBLICATION, List.of(hoursAgo(5), hoursAgo(6)),
                List.of(), NOW))
                .doesNotThrowAnyException();
    }

    @Test
    void the_latest_date_wins_when_both_limits_are_reached() {
        assertThatThrownBy(() -> CADENCE.check(VintedWrite.DELETION, List.of(minutesAgo(1), hoursAgo(23)),
                List.of(), NOW))
                .isInstanceOfSatisfying(CadenceLimitReached.class,
                        refusal -> assertThat(refusal.allowedAt()).isEqualTo(NOW.plus(Duration.ofHours(1))));
    }

    @Test
    void interval_must_be_under_24_hours_and_limits_strictly_positive() {
        assertThatThrownBy(() -> new WriteCadence(Duration.ZERO, 1, 1)).isInstanceOf(InvalidWriteCadence.class);
        assertThatThrownBy(() -> new WriteCadence(Duration.ofMinutes(-1), 1, 1))
                .isInstanceOf(InvalidWriteCadence.class);
        assertThatThrownBy(() -> new WriteCadence(Duration.ofHours(24), 1, 1)).isInstanceOf(InvalidWriteCadence.class);
        assertThatThrownBy(() -> new WriteCadence(Duration.ofMinutes(2), 0, 1))
                .isInstanceOf(InvalidWriteCadence.class);
        assertThatThrownBy(() -> new WriteCadence(Duration.ofMinutes(2), 1, -1))
                .isInstanceOf(InvalidWriteCadence.class);
    }
}
