package fr.vinted.republisher.domain.backup;

import fr.vinted.republisher.domain.wardrobe.PauseRange;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class BackupPaceTest {

    private static final Duration MINIMUM = Duration.ofSeconds(10);
    private static final Duration MAXIMUM = Duration.ofSeconds(15);

    @Test
    void pauses_between_listings_stay_within_their_bounds_and_vary() {
        BackupPace pace = new BackupPace(new PauseRange(MINIMUM, MAXIMUM), new Random(42));

        List<Duration> pauses = Stream.generate(pace::nextPauseBetweenListings).limit(200).toList();

        assertThat(pauses).allSatisfy(pause -> assertThat(pause).isBetween(MINIMUM, MAXIMUM));
        assertThat(pauses.stream().distinct().count()).isGreaterThan(100);
    }
}
