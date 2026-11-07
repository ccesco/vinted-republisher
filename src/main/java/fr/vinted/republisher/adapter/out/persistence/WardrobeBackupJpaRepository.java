package fr.vinted.republisher.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface WardrobeBackupJpaRepository extends JpaRepository<WardrobeBackupJpaEntity, UUID> {

    Optional<WardrobeBackupJpaEntity> findByFinishedAtIsNull();

    Optional<WardrobeBackupJpaEntity> findFirstByFinishedAtIsNotNullOrderByFinishedAtDesc();
}
