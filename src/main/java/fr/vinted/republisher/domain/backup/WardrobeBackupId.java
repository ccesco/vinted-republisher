package fr.vinted.republisher.domain.backup;

import java.util.Objects;
import java.util.UUID;

public record WardrobeBackupId(UUID value) {

    public WardrobeBackupId {
        Objects.requireNonNull(value);
    }
}
