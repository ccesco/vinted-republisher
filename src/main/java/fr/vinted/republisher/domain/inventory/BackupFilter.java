package fr.vinted.republisher.domain.inventory;

/** Where the backup of the listings shown stands; only a validation of the current backup counts. */
public enum BackupFilter {
    ALL, NOT_BACKED_UP, NOT_VALIDATED, VALIDATED;

    boolean accepts(InventoryEntry entry) {
        return switch (this) {
            case ALL -> true;
            case NOT_BACKED_UP -> entry.backup().isEmpty();
            case NOT_VALIDATED -> entry.backup().isPresent() && entry.validatedAt().isEmpty();
            case VALIDATED -> entry.validatedAt().isPresent();
        };
    }
}
