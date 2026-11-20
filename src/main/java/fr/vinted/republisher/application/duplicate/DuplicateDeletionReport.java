package fr.vinted.republisher.application.duplicate;

import fr.vinted.republisher.domain.duplicate.DuplicateDeletionOutcome;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.util.Objects;

/** How the deletion of a duplicate ended, and the twin read on sale on Vinted right before. */
public record DuplicateDeletionReport(DuplicateDeletionOutcome outcome, VintedListingId twin) {

    public DuplicateDeletionReport {
        Objects.requireNonNull(outcome);
        Objects.requireNonNull(twin);
    }
}
