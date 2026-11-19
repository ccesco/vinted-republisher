package fr.vinted.republisher.domain.inventory;

/**
 * Where the backup of the listings shown stands; only a validation, or a failed verification (US-40), of the current
 * backup counts.
 */
public enum BackupFilter {
    ALL, NOT_BACKED_UP, NOT_VALIDATED, VALIDATED, VERIFICATION_FAILED;

    boolean accepts(InventoryEntry entry) {
        return switch (this) {
            case ALL -> true;
            case NOT_BACKED_UP -> entry.backup().isEmpty();
            case NOT_VALIDATED -> entry.backup().isPresent() && entry.validatedAt().isEmpty();
            case VALIDATED -> entry.validatedAt().isPresent();
            case VERIFICATION_FAILED -> entry.stage() == ListingStage.VERIFICATION_FAILED;
        };
    }
}
