package fr.vinted.republisher.domain.journal;

import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * One action as written in the journal: when, which, how it ended, on which listing if any, and why (the same code as
 * the API refusal or problem) if there is a reason.
 */
public record ActionRecord(Instant occurredAt, JournaledAction action, ActionOutcome outcome,
                           Optional<VintedListingId> listingId, Optional<String> reason) {

    /** Reasons of a refusal that only asks to wait: cadence, republication delay, browser busy. */
    public static final Set<String> WAITING_REASONS =
            Set.of("CADENCE_LIMIT_REACHED", "TOO_EARLY", "BROWSER_BUSY");

    public ActionRecord {
        Objects.requireNonNull(occurredAt);
        Objects.requireNonNull(action);
        Objects.requireNonNull(outcome);
        Objects.requireNonNull(listingId);
        Objects.requireNonNull(reason);
    }

    /** A refusal, a doubt, a recheck found not done or a failure: the member has to look at it. */
    public boolean isFailure() {
        return switch (outcome) {
            case IN_DOUBT, NOT_DONE, FAILED -> true;
            case REFUSED -> !isWaitingRefusal();
            case STARTED, SUCCEEDED, DRY_RUN -> false;
        };
    }

    /** Nothing was done and nothing is wrong with the listing: it changes neither a failure nor a success. */
    public boolean isWaitingRefusal() {
        return outcome == ActionOutcome.REFUSED && reason.filter(WAITING_REASONS::contains).isPresent();
    }
}
