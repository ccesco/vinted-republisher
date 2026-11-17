package fr.vinted.republisher.adapter.out.time;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SleepingRunWaitingTest {

    private final Clock clock = Clock.systemUTC();
    private final SleepingRunWaiting waiting = new SleepingRunWaiting(clock);

    @Test
    void returns_at_once_for_a_past_date() {
        long start = System.nanoTime();
        waiting.until(clock.instant().minusSeconds(60));
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(50));
    }

    @Test
    void returns_once_the_date_is_reached() {
        Instant until = clock.instant().plusMillis(150);
        waiting.until(until);
        assertThat(clock.instant()).isAfterOrEqualTo(until);
    }
}
