package fr.vinted.republisher.domain.republication;

import fr.vinted.republisher.domain.deletion.DeletionOutcome;
import fr.vinted.republisher.domain.deletion.ListingDeletion;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * The minimum time between the proof of a deletion and the republication of its listing. Computed from the proof
 * kept in the database, it survives any restart and is never waited for again once elapsed.
 */
public record RepublicationDelay(Duration minimum) {

    public RepublicationDelay {
        Objects.requireNonNull(minimum);
        if (minimum.isZero() || minimum.isNegative()) {
            throw new InvalidRepublicationDelay(minimum);
        }
    }

    /** Only a confirmed deletion makes its listing republishable. */
    public Optional<Instant> republishableAt(ListingDeletion deletion) {
        if (deletion.outcome() != DeletionOutcome.CONFIRMED) {
            return Optional.empty();
        }
        return deletion.settledAt().map(provenAt -> provenAt.plus(minimum));
    }
}
