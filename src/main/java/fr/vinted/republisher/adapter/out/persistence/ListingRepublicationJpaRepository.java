package fr.vinted.republisher.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface ListingRepublicationJpaRepository extends JpaRepository<ListingRepublicationJpaEntity, UUID> {

    Optional<ListingRepublicationJpaEntity> findByListingId(Long listingId);
}
