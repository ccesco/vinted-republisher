package fr.vinted.republisher.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface ListingDeletionJpaRepository extends JpaRepository<ListingDeletionJpaEntity, UUID> {

    Optional<ListingDeletionJpaEntity> findFirstByListingIdOrderByAttemptedAtDesc(Long listingId);
}
