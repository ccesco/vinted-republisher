package fr.vinted.republisher.domain.wardrobe;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Random;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScanPaceTest {

    private static final Duration THREE = Duration.ofSeconds(3);
    private static final Duration SIX = Duration.ofSeconds(6);
    private static final Duration TEN = Duration.ofSeconds(10);
    private static final Duration FIFTEEN = Duration.ofSeconds(15);

    private final ScanPace pace = new ScanPace(new PauseRange(THREE, SIX), new PauseRange(TEN, FIFTEEN), new Random(42));

    @Test
    void every_pause_between_scrolls_stays_within_its_bounds() {
        assertThat(thousand(pace::nextPauseBetweenScrolls)).allSatisfy(pause -> assertThat(pause).isBetween(THREE, SIX));
    }

    @Test
    void every_pause_between_listings_stays_within_its_bounds() {
        assertThat(thousand(pace::nextPauseBetweenListings)).allSatisfy(pause -> assertThat(pause).isBetween(TEN, FIFTEEN));
    }

    @Test
    void pauses_vary() {
        assertThat(thousand(pace::nextPauseBetweenListings).distinct()).hasSizeGreaterThan(1);
    }

    @Test
    void equal_bounds_give_that_pause() {
        ScanPace steady = new ScanPace(new PauseRange(THREE, THREE), new PauseRange(TEN, TEN), new Random(42));

        assertThat(steady.nextPauseBetweenScrolls()).isEqualTo(THREE);
        assertThat(steady.nextPauseBetweenListings()).isEqualTo(TEN);
    }

    @Test
    void refuses_a_maximum_below_the_minimum() {
        assertThatThrownBy(() -> new PauseRange(SIX, THREE)).isInstanceOf(IllegalArgumentException.class);
    }

    private static Stream<Duration> thousand(Supplier<Duration> pause) {
        return Stream.generate(pause).limit(1000);
    }
}
