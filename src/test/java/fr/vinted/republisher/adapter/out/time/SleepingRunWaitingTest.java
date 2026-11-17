package fr.vinted.republisher.adapter.out.time;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class SleepingRunWaitingTest {

    private final Clock clock = Clock.systemUTC();
    private final SleepingRunWaiting waiting = new SleepingRunWaiting(clock, Duration.ofMillis(20));

    @Test
    void returns_at_once_for_a_past_date() {
        long start = System.nanoTime();
        waiting.until(clock.instant().minusSeconds(60), () -> false);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(50));
    }

    @Test
    void returns_once_the_date_is_reached() {
        Instant until = clock.instant().plusMillis(150);
        waiting.until(until, () -> false);
        assertThat(clock.instant()).isAfterOrEqualTo(until);
    }

    @Test
    void returns_early_when_interrupted() {
        // Étant donné une attente d'une heure ; Quand une pause ou un arrêt est demandé
        AtomicBoolean interrupted = new AtomicBoolean();
        Thread.ofVirtual().start(() -> {
            sleep(Duration.ofMillis(100));
            interrupted.set(true);
        });
        long start = System.nanoTime();
        waiting.until(clock.instant().plus(Duration.ofHours(1)), interrupted::get);
        // Alors l'attente se termine dans la seconde
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(1));
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
