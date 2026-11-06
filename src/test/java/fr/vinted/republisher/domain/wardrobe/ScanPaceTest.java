package fr.vinted.republisher.domain.wardrobe;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Random;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScanPaceTest {

    private static final Duration MINIMUM = Duration.ofSeconds(3);
    private static final Duration MAXIMUM = Duration.ofSeconds(6);

    @Test
    void every_pause_stays_within_the_bounds() {
        ScanPace pace = new ScanPace(MINIMUM, MAXIMUM, new Random(42));

        assertThat(pauses(pace, 1000)).allSatisfy(pause -> assertThat(pause).isBetween(MINIMUM, MAXIMUM));
    }

    @Test
    void pauses_vary() {
        ScanPace pace = new ScanPace(MINIMUM, MAXIMUM, new Random(42));

        assertThat(pauses(pace, 10).distinct()).hasSizeGreaterThan(1);
    }

    @Test
    void equal_bounds_give_that_pause() {
        ScanPace pace = new ScanPace(MINIMUM, MINIMUM, new Random(42));

        assertThat(pace.nextPause()).isEqualTo(MINIMUM);
    }

    @Test
    void refuses_a_maximum_below_the_minimum() {
        assertThatThrownBy(() -> new ScanPace(MAXIMUM, MINIMUM, new Random(42)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Stream<Duration> pauses(ScanPace pace, int count) {
        return IntStream.range(0, count).mapToObj(index -> pace.nextPause());
    }
}
