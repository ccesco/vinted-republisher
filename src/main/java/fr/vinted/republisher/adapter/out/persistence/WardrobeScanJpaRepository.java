package fr.vinted.republisher.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface WardrobeScanJpaRepository extends JpaRepository<WardrobeScanJpaEntity, UUID> {

    Optional<WardrobeScanJpaEntity> findByFinishedAtIsNull();

    Optional<WardrobeScanJpaEntity> findFirstByFinishedAtIsNotNullOrderByFinishedAtDesc();
}
