package fr.vinted.republisher.adapter.out.persistence;

import fr.vinted.republisher.domain.backup.VerificationResult;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "backup_verification")
class BackupVerificationJpaEntity {

    @Id
    @Column(name = "listing_id", nullable = false)
    private Long listingId;

    @Column(name = "backup_saved_at", nullable = false)
    private Instant backupSavedAt;

    @Column(name = "verified_at", nullable = false)
    private Instant verifiedAt;

    @Column(name = "passed", nullable = false)
    private boolean passed;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "failures", nullable = false)
    private String failures;

    protected BackupVerificationJpaEntity() {
    }

    BackupVerificationJpaEntity(VerificationResult result) {
        listingId = result.listingId().value();
        backupSavedAt = result.backupSavedAt();
        verifiedAt = result.verifiedAt();
        passed = result.passed();
        failures = VerificationFailuresJson.write(result.failures());
    }

    VerificationResult toVerificationResult() {
        return new VerificationResult(new VintedListingId(listingId), backupSavedAt, verifiedAt,
                VerificationFailuresJson.read(failures));
    }
}
