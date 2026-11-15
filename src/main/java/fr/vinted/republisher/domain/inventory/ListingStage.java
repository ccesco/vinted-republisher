package fr.vinted.republisher.domain.inventory;

import fr.vinted.republisher.domain.deletion.DeletionOutcome;
import fr.vinted.republisher.domain.deletion.ListingDeletion;
import fr.vinted.republisher.domain.republication.ListingRepublication;
import fr.vinted.republisher.domain.republication.RepublicationDelay;

import java.time.Instant;
import java.util.Optional;

/**
 * Where a listing stands in its backup, validation, deletion, wait and republication. Deduced from what the tool
 * keeps at each reading, never stored, so a restart gives the same stage.
 */
public enum ListingStage {
    REPUBLISHED,
    REPUBLICATION_TO_CHECK,
    DELETION_TO_CHECK,
    WAITING,
    TO_REPUBLISH,
    GONE,
    TO_DELETE,
    TO_VALIDATE,
    TO_BACK_UP;

    /**
     * The first rule that applies wins, in the order of the constants. {@code validated} means a validation holds for
     * the current backup; a deletion found not done counts as no deletion.
     */
    public static ListingStage of(boolean present, boolean backedUp, boolean validated,
                                  Optional<ListingDeletion> lastDeletion,
                                  Optional<ListingRepublication> republication, RepublicationDelay delay,
                                  Instant now) {
        if (republication.isPresent()) {
            return switch (republication.get().outcome()) {
                case PUBLISHED -> REPUBLISHED;
                case FAILED -> REPUBLICATION_TO_CHECK;
            };
        }
        Optional<ListingDeletion> deletion = lastDeletion.filter(last -> last.outcome() != DeletionOutcome.NOT_DONE);
        if (deletion.isPresent()) {
            return deletion.flatMap(delay::republishableAt)
                    .map(republishableAt -> now.isBefore(republishableAt) ? WAITING : TO_REPUBLISH)
                    .orElse(DELETION_TO_CHECK);
        }
        if (!present) {
            return GONE;
        }
        if (validated) {
            return TO_DELETE;
        }
        return backedUp ? TO_VALIDATE : TO_BACK_UP;
    }
}
