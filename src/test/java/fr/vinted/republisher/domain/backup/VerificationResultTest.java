package fr.vinted.republisher.domain.backup;

import fr.vinted.republisher.domain.backup.VerificationFailure.ListingAbsent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static fr.vinted.republisher.domain.backup.BackupFixtures.aBackup;
import static org.assertj.core.api.Assertions.assertThat;

class VerificationResultTest {

    private static final Instant SAVED_AT = Instant.parse("2026-11-10T10:00:00Z");
    private static final Instant VERIFIED_AT = Instant.parse("2026-11-18T10:00:00Z");

    @Test
    void holds_for_the_version_of_the_backup_it_compared() {
        ListingBackup backup = aBackup(1, SAVED_AT);
        VerificationResult result = VerificationResult.of(backup, new BackupVerification(List.of()), VERIFIED_AT);

        assertThat(result.listingId()).isEqualTo(backup.id());
        assertThat(result.backupSavedAt()).isEqualTo(SAVED_AT);
        assertThat(result.verifiedAt()).isEqualTo(VERIFIED_AT);
        assertThat(result.passed()).isTrue();
        assertThat(result.holdsFor(backup)).isTrue();
        assertThat(result.holdsFor(aBackup(1, SAVED_AT.plusSeconds(1)))).isFalse();
        assertThat(result.holdsFor(aBackup(2, SAVED_AT))).isFalse();
    }

    @Test
    void a_failure_counts_only_for_the_version_compared() {
        ListingBackup backup = aBackup(1, SAVED_AT);
        VerificationResult failed = VerificationResult.of(backup,
                new BackupVerification(List.of(new ListingAbsent())), VERIFIED_AT);

        assertThat(failed.passed()).isFalse();
        assertThat(failed.failures()).containsExactly(new ListingAbsent());
        assertThat(failed.failsFor(backup)).isTrue();
        assertThat(failed.failsFor(aBackup(1, VERIFIED_AT.plusSeconds(60)))).isFalse();
    }
}
