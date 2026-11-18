package fr.vinted.republisher.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface BackupVerificationJpaRepository extends JpaRepository<BackupVerificationJpaEntity, Long> {
}
