package fr.vinted.republisher.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ListingDeletionJpaRepository extends JpaRepository<ListingDeletionJpaEntity, UUID> {

    Optional<ListingDeletionJpaEntity> findFirstByListingIdOrderByAttemptedAtDesc(Long listingId);

    @Query("select deletion from ListingDeletionJpaEntity deletion where deletion.attemptedAt = "
            + "(select max(other.attemptedAt) from ListingDeletionJpaEntity other where other.listingId = deletion.listingId)")
    List<ListingDeletionJpaEntity> findAllLatest();
}
