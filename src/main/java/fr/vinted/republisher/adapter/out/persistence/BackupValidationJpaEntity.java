package fr.vinted.republisher.adapter.out.persistence;

import fr.vinted.republisher.domain.backup.BackupValidation;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "backup_validation")
class BackupValidationJpaEntity {

    @Id
    @Column(name = "listing_id", nullable = false)
    private Long listingId;

    @Column(name = "backup_saved_at", nullable = false)
    private Instant backupSavedAt;

    @Column(name = "validated_at", nullable = false)
    private Instant validatedAt;

    protected BackupValidationJpaEntity() {
    }

    BackupValidationJpaEntity(BackupValidation validation) {
        listingId = validation.listingId().value();
        backupSavedAt = validation.backupSavedAt();
        validatedAt = validation.validatedAt();
    }

    BackupValidation toBackupValidation() {
        return new BackupValidation(new VintedListingId(listingId), backupSavedAt, validatedAt);
    }
}
