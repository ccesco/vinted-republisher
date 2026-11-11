package fr.vinted.republisher.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface BackupValidationJpaRepository extends JpaRepository<BackupValidationJpaEntity, Long> {
}
