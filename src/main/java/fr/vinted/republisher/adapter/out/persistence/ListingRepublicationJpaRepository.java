package fr.vinted.republisher.adapter.out.persistence;

import fr.vinted.republisher.domain.cadence.WriteOrigin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ListingRepublicationJpaRepository extends JpaRepository<ListingRepublicationJpaEntity, UUID> {

    Optional<ListingRepublicationJpaEntity> findFirstByListingIdOrderByAttemptedAtDesc(Long listingId);

    @Query("select republication from ListingRepublicationJpaEntity republication where republication.attemptedAt = "
            + "(select max(other.attemptedAt) from ListingRepublicationJpaEntity other"
            + " where other.listingId = republication.listingId)")
    List<ListingRepublicationJpaEntity> findAllLatest();

    @Query("select new fr.vinted.republisher.adapter.out.persistence.NotPublishedCount(attempt.listingId, count(attempt))"
            + " from ListingRepublicationJpaEntity attempt"
            + " where attempt.outcome = fr.vinted.republisher.domain.republication.RepublicationOutcome.NOT_PUBLISHED"
            + " group by attempt.listingId")
    List<NotPublishedCount> countNotPublishedPerListing();

    @Query("select r.newListingId from ListingRepublicationJpaEntity r where r.newListingId is not null")
    List<Long> findNewListingIds();

    Optional<ListingRepublicationJpaEntity> findByNewListingId(long newListingId);

    @Query("select attempt.attemptedAt from ListingRepublicationJpaEntity attempt where attempt.origin = :origin"
            + " and attempt.attemptedAt > :since")
    List<Instant> findAttemptedAtAfter(WriteOrigin origin, Instant since);
}
