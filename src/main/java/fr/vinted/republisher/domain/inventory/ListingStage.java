package fr.vinted.republisher.domain.inventory;

import fr.vinted.republisher.domain.deletion.DeletionOutcome;
import fr.vinted.republisher.domain.deletion.ListingDeletion;
import fr.vinted.republisher.domain.republication.ListingRepublication;
import fr.vinted.republisher.domain.republication.RepublicationDelay;
import fr.vinted.republisher.domain.republication.RepublicationOutcome;
import fr.vinted.republisher.domain.sale.SaleOrigin;

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
    SOLD,
    GONE,
    TO_DELETE,
    VERIFICATION_FAILED,
    TO_VALIDATE,
    TO_BACK_UP;

    /**
     * The first rule that applies wins, in the order of the constants, but for a sale noted by the member, which wins
     * over every rule: the article no longer exists, whatever the tool did (US-51). {@code sale} is the origin of the
     * listing's recorded sale, which leaves nothing to relaunch (US-50). {@code validated} means a validation holds
     * for the current backup, {@code verificationFailed} that the last verification of the current backup on Vinted
     * failed; a deletion found not done, or a republication checked not published, counts as none.
     */
    public static ListingStage of(boolean present, Optional<SaleOrigin> sale, boolean backedUp, boolean validated,
                                  boolean verificationFailed, Optional<ListingDeletion> lastDeletion,
                                  Optional<ListingRepublication> lastRepublication, RepublicationDelay delay,
                                  Instant now) {
        if (sale.filter(SaleOrigin.NOTED::equals).isPresent()) {
            return SOLD;
        }
        Optional<ListingRepublication> republication =
                lastRepublication.filter(last -> last.outcome() != RepublicationOutcome.NOT_PUBLISHED);
        if (republication.isPresent()) {
            return republication.get().outcome() == RepublicationOutcome.PUBLISHED ? REPUBLISHED : REPUBLICATION_TO_CHECK;
        }
        Optional<ListingDeletion> deletion = lastDeletion.filter(last -> last.outcome() != DeletionOutcome.NOT_DONE);
        if (deletion.isPresent()) {
            return deletion.flatMap(delay::republishableAt)
                    .map(republishableAt -> now.isBefore(republishableAt) ? WAITING : TO_REPUBLISH)
                    .orElse(DELETION_TO_CHECK);
        }
        if (sale.isPresent()) {
            return SOLD;
        }
        if (!present) {
            return GONE;
        }
        if (validated) {
            return TO_DELETE;
        }
        if (verificationFailed) {
            return VERIFICATION_FAILED;
        }
        return backedUp ? TO_VALIDATE : TO_BACK_UP;
    }
}
