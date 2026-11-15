package fr.vinted.republisher.domain.journal;

import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * One action as written in the journal: when, which, how it ended, on which listing if any, and why (the same code as
 * the API refusal or problem) if there is a reason.
 */
public record ActionRecord(Instant occurredAt, JournaledAction action, ActionOutcome outcome,
                           Optional<VintedListingId> listingId, Optional<String> reason) {

    public ActionRecord {
        Objects.requireNonNull(occurredAt);
        Objects.requireNonNull(action);
        Objects.requireNonNull(outcome);
        Objects.requireNonNull(listingId);
        Objects.requireNonNull(reason);
    }
}
