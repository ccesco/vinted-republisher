package fr.vinted.republisher.adapter.out.persistence;

import fr.vinted.republisher.domain.backup.WardrobeBackup;
import fr.vinted.republisher.domain.backup.WardrobeBackupId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Entity
@Table(name = "wardrobe_backup")
class WardrobeBackupJpaEntity {

    @Id
    private UUID id;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    protected WardrobeBackupJpaEntity() {
    }

    WardrobeBackupJpaEntity(WardrobeBackup backup) {
        id = backup.id().value();
        startedAt = backup.startedAt();
        finishedAt = backup.finishedAt().orElse(null);
    }

    WardrobeBackup toWardrobeBackup() {
        return new WardrobeBackup(new WardrobeBackupId(id), startedAt, Optional.ofNullable(finishedAt));
    }
}
