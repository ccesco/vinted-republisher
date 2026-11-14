package fr.vinted.republisher.domain.republication;

import fr.vinted.republisher.domain.deletion.ListingDeletion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static fr.vinted.republisher.domain.backup.BackupFixtures.aBackup;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RepublicationDelayTest {

    private static final Instant CLICKED_AT = Instant.parse("2026-11-11T14:00:00Z");
    private static final Instant PROVEN_AT = Instant.parse("2026-11-11T14:32:00Z");

    private final RepublicationDelay fiveMinutes = new RepublicationDelay(Duration.ofMinutes(5));
    private final ListingDeletion attempt =
            ListingDeletion.attempted(aBackup(1, Instant.parse("2026-11-10T10:00:00Z")), CLICKED_AT);

    @Test
    void a_confirmed_deletion_is_republishable_the_delay_after_its_proof() {
        assertThat(fiveMinutes.republishableAt(attempt.confirmed(PROVEN_AT)))
                .isEqualTo(Optional.of(Instant.parse("2026-11-11T14:37:00Z")));
    }

    @Test
    void an_unconfirmed_deletion_is_not_republishable() {
        assertThat(fiveMinutes.republishableAt(attempt)).isEmpty();
    }

    @Test
    void a_deletion_not_done_is_not_republishable() {
        assertThat(fiveMinutes.republishableAt(attempt.notDone(PROVEN_AT))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PT0S", "-PT5M"})
    void must_be_strictly_positive(String delay) {
        Duration invalid = Duration.parse(delay);

        assertThatThrownBy(() -> new RepublicationDelay(invalid))
                .isInstanceOf(InvalidRepublicationDelay.class)
                .hasMessageContaining(invalid.toString());
    }
}
