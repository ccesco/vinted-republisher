package fr.vinted.republisher.application.cadence;

import fr.vinted.republisher.domain.cadence.CadenceLimitReached;
import fr.vinted.republisher.domain.cadence.VintedWrite;
import fr.vinted.republisher.domain.cadence.WriteCadence;
import fr.vinted.republisher.domain.deletion.ListingDeletion;
import fr.vinted.republisher.fakes.AdjustableClock;
import fr.vinted.republisher.fakes.InMemoryListingDeletionRepository;
import fr.vinted.republisher.fakes.InMemoryListingRepublicationRepository;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static fr.vinted.republisher.domain.backup.BackupFixtures.aBackup;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/** US-15: a write that passed the cadence holds it until its action ends, whatever tab it runs in. */
class CadenceCheckTest {

    private static final Instant NOW = Instant.parse("2026-11-18T10:00:00Z");

    private final InMemoryListingDeletionRepository deletions = new InMemoryListingDeletionRepository();
    private final AdjustableClock clock = new AdjustableClock(NOW);
    private final CadenceCheck cadence = new CadenceCheck(deletions, new InMemoryListingRepublicationRepository(),
            new WriteCadence(Duration.ofMinutes(2), 20, 20), clock);

    @Test
    void a_write_in_progress_holds_the_cadence() {
        cadence.reserve(VintedWrite.DELETION);
        clock.advance(Duration.ofSeconds(30));

        CadenceLimitReached refused = catchThrowableOfType(CadenceLimitReached.class,
                () -> cadence.reserve(VintedWrite.REPUBLICATION));

        assertThat(refused.allowedAt()).isEqualTo(NOW.plusSeconds(120));
    }

    @Test
    void a_released_reservation_no_longer_counts() {
        WriteReservation first = cadence.reserve(VintedWrite.DELETION);
        clock.advance(Duration.ofSeconds(20));
        deletions.save(ListingDeletion.attempted(aBackup(1, NOW), clock.instant()));
        clock.advance(Duration.ofSeconds(10));

        cadence.release(first);

        CadenceLimitReached refused = catchThrowableOfType(CadenceLimitReached.class,
                () -> cadence.reserve(VintedWrite.DELETION));
        assertThat(refused.allowedAt()).isEqualTo(NOW.plusSeconds(140));
        clock.set(NOW.plusSeconds(140));
        assertThat(cadence.reserve(VintedWrite.DELETION)).isNotNull();
    }
}
