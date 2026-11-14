package fr.vinted.republisher.domain.deletion;

import fr.vinted.republisher.domain.backup.ListingBackup;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * An attempt to delete a listing on Vinted, from the click on Vinted's confirmation on. It starts unconfirmed, so that
 * a crash right after the click leaves a doubt rather than no trace, and is settled once only.
 */
public record ListingDeletion(ListingDeletionId id, VintedListingId listingId, Instant backupSavedAt,
                              Instant attemptedAt, DeletionOutcome outcome, Optional<Instant> settledAt) {

    public ListingDeletion {
        Objects.requireNonNull(id);
        Objects.requireNonNull(listingId);
        Objects.requireNonNull(backupSavedAt);
        Objects.requireNonNull(attemptedAt);
        Objects.requireNonNull(outcome);
        Objects.requireNonNull(settledAt);
    }

    public static ListingDeletion attempted(ListingBackup backup, Instant attemptedAt) {
        return new ListingDeletion(new ListingDeletionId(UUID.randomUUID()), backup.id(), backup.savedAt(),
                attemptedAt, DeletionOutcome.UNCONFIRMED, Optional.empty());
    }

    public ListingDeletion confirmed(Instant provenAt) {
        return settled(DeletionOutcome.CONFIRMED, provenAt);
    }

    public ListingDeletion notDone(Instant checkedAt) {
        return settled(DeletionOutcome.NOT_DONE, checkedAt);
    }

    /** A listing is deleted once at most, and never again while a deletion is in doubt. */
    public void allowNewAttempt() {
        switch (outcome) {
            case CONFIRMED -> throw new ListingAlreadyDeleted(listingId);
            case UNCONFIRMED -> throw new DeletionUnconfirmed(listingId);
            case NOT_DONE -> {
            }
        }
    }

    private ListingDeletion settled(DeletionOutcome settledOutcome, Instant at) {
        if (outcome != DeletionOutcome.UNCONFIRMED) {
            throw new NoUnconfirmedDeletion(listingId);
        }
        return new ListingDeletion(id, listingId, backupSavedAt, attemptedAt, settledOutcome, Optional.of(at));
    }
}
