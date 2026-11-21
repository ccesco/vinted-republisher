package fr.vinted.republisher.adapter.out.persistence;

import fr.vinted.republisher.domain.cadence.WriteOrigin;
import fr.vinted.republisher.domain.republication.ListingRepublication;
import fr.vinted.republisher.domain.republication.ListingRepublicationId;
import fr.vinted.republisher.domain.republication.RepublicationOutcome;
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
@Table(name = "listing_republication")
class ListingRepublicationJpaEntity {

    @Id
    private UUID id;

    @Column(name = "listing_id", nullable = false)
    private Long listingId;

    @Column(name = "article_id", nullable = false)
    private UUID articleId;

    @Column(name = "backup_saved_at", nullable = false)
    private Instant backupSavedAt;

    @Column(name = "attempted_at", nullable = false)
    private Instant attemptedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false)
    private RepublicationOutcome outcome;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "new_listing_id")
    private Long newListingId;

    @Column(name = "checked_at")
    private Instant checkedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false)
    private WriteOrigin origin;

    protected ListingRepublicationJpaEntity() {
    }

    /** {@code articleId}: the article of the listing, the new one's too once published. */
    ListingRepublicationJpaEntity(ListingRepublication republication, UUID articleId) {
        id = republication.id().value();
        listingId = republication.listingId().value();
        this.articleId = articleId;
        backupSavedAt = republication.backupSavedAt();
        attemptedAt = republication.attemptedAt();
        outcome = republication.outcome();
        publishedAt = republication.publishedAt().orElse(null);
        newListingId = republication.newListingId().map(VintedListingId::value).orElse(null);
        checkedAt = republication.checkedAt().orElse(null);
        origin = republication.origin();
    }

    ListingRepublication toListingRepublication() {
        return new ListingRepublication(new ListingRepublicationId(id), new VintedListingId(listingId), backupSavedAt,
                attemptedAt, outcome, Optional.ofNullable(publishedAt),
                Optional.ofNullable(newListingId).map(VintedListingId::new), Optional.ofNullable(checkedAt), origin);
    }
}
