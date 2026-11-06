package fr.vinted.republisher.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface ListingJpaRepository extends JpaRepository<ListingJpaEntity, Long> {
}
