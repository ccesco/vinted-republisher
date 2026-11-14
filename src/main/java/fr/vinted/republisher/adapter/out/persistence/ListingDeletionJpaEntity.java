package fr.vinted.republisher.adapter.out.persistence;

import fr.vinted.republisher.domain.deletion.DeletionOutcome;
import fr.vinted.republisher.domain.deletion.ListingDeletion;
import fr.vinted.republisher.domain.deletion.ListingDeletionId;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Entity
@Table(name = "listing_deletion")
class ListingDeletionJpaEntity {

    @Id
    private UUID id;

    @Column(name = "listing_id", nullable = false)
    private Long listingId;

    @Column(name = "backup_saved_at", nullable = false)
    private Instant backupSavedAt;

    @Column(name = "attempted_at", nullable = false)
    private Instant attemptedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false)
    private DeletionOutcome outcome;

    @Column(name = "settled_at")
    private Instant settledAt;

    protected ListingDeletionJpaEntity() {
    }

    ListingDeletionJpaEntity(ListingDeletion deletion) {
        id = deletion.id().value();
        listingId = deletion.listingId().value();
        backupSavedAt = deletion.backupSavedAt();
        attemptedAt = deletion.attemptedAt();
        outcome = deletion.outcome();
        settledAt = deletion.settledAt().orElse(null);
    }

    ListingDeletion toListingDeletion() {
        return new ListingDeletion(new ListingDeletionId(id), new VintedListingId(listingId), backupSavedAt,
                attemptedAt, outcome, Optional.ofNullable(settledAt));
    }
}
