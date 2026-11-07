package fr.vinted.republisher.domain.backup;

import java.util.Optional;

public interface WardrobeBackupRepository {

    Optional<WardrobeBackup> findUnfinished();

    Optional<WardrobeBackup> findLastFinished();

    void save(WardrobeBackup backup);
}
