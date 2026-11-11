package fr.vinted.republisher.domain.backup;

import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static fr.vinted.republisher.domain.backup.BackupFixtures.aBackup;
import static org.assertj.core.api.Assertions.assertThat;

class BackupValidationTest {

    private static final Instant SAVED_AT = Instant.parse("2026-11-10T10:00:00Z");
    private static final Instant VALIDATED_AT = Instant.parse("2026-11-11T10:00:00Z");

    @Test
    void validation_holds_for_the_backup_it_was_given_to() {
        ListingBackup backup = aBackup(1, SAVED_AT);

        BackupValidation validation = BackupValidation.of(backup, VALIDATED_AT);

        assertThat(validation).isEqualTo(new BackupValidation(new VintedListingId(1), SAVED_AT, VALIDATED_AT));
        assertThat(validation.holdsFor(backup)).isTrue();
    }

    @Test
    void validation_no_longer_holds_once_the_backup_is_replaced() {
        BackupValidation validation = BackupValidation.of(aBackup(1, SAVED_AT), VALIDATED_AT);

        assertThat(validation.holdsFor(aBackup(1, VALIDATED_AT.plusSeconds(60)))).isFalse();
    }

    @Test
    void validation_holds_for_its_listing_only() {
        BackupValidation validation = BackupValidation.of(aBackup(1, SAVED_AT), VALIDATED_AT);

        assertThat(validation.holdsFor(aBackup(2, SAVED_AT))).isFalse();
    }
}
