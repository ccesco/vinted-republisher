package fr.vinted.republisher.adapter.out.persistence;

import fr.vinted.republisher.domain.backup.WardrobeBackup;
import fr.vinted.republisher.domain.backup.WardrobeBackupRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
class JpaWardrobeBackupRepository implements WardrobeBackupRepository {

    private final WardrobeBackupJpaRepository repository;

    JpaWardrobeBackupRepository(WardrobeBackupJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<WardrobeBackup> findUnfinished() {
        return repository.findByFinishedAtIsNull().map(WardrobeBackupJpaEntity::toWardrobeBackup);
    }

    @Override
    public Optional<WardrobeBackup> findLastFinished() {
        return repository.findFirstByFinishedAtIsNotNullOrderByFinishedAtDesc().map(WardrobeBackupJpaEntity::toWardrobeBackup);
    }

    @Override
    public void save(WardrobeBackup backup) {
        repository.save(new WardrobeBackupJpaEntity(backup));
    }
}
