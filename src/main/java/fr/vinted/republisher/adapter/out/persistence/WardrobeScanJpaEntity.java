package fr.vinted.republisher.adapter.out.persistence;

import fr.vinted.republisher.domain.wardrobe.WardrobeScan;
import fr.vinted.republisher.domain.wardrobe.WardrobeScanId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Entity
@Table(name = "wardrobe_scan")
class WardrobeScanJpaEntity {

    @Id
    private UUID id;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    protected WardrobeScanJpaEntity() {
    }

    WardrobeScanJpaEntity(WardrobeScan scan) {
        id = scan.id().value();
        startedAt = scan.startedAt();
        finishedAt = scan.finishedAt().orElse(null);
    }

    WardrobeScan toWardrobeScan() {
        return new WardrobeScan(new WardrobeScanId(id), startedAt, Optional.ofNullable(finishedAt));
    }
}
