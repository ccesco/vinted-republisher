package fr.vinted.republisher.fakes;

import fr.vinted.republisher.domain.backup.WardrobeBackup;
import fr.vinted.republisher.domain.backup.WardrobeBackupId;
import fr.vinted.republisher.domain.backup.WardrobeBackupRepository;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class InMemoryWardrobeBackupRepository implements WardrobeBackupRepository {

    private final Map<WardrobeBackupId, WardrobeBackup> backups = new LinkedHashMap<>();

    @Override
    public Optional<WardrobeBackup> findUnfinished() {
        return backups.values().stream().filter(backup -> backup.finishedAt().isEmpty()).findFirst();
    }

    @Override
    public Optional<WardrobeBackup> findLastFinished() {
        return backups.values().stream().filter(backup -> backup.finishedAt().isPresent())
                .max(Comparator.comparing((WardrobeBackup backup) -> backup.finishedAt().orElse(Instant.MIN)));
    }

    @Override
    public void save(WardrobeBackup backup) {
        backups.put(backup.id(), backup);
    }
}
