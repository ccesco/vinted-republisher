package fr.vinted.republisher.domain.republication;

import fr.vinted.republisher.domain.deletion.DeletionReport;
import fr.vinted.republisher.domain.wardrobe.Listing;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The backup of a listing and its deletion as the member sees them, with its republication, the new listing replacing
 * it once republished, and the mode republications run in. {@code comparison} is only there in answer to a
 * verification that compared the new listing with the backup: empty when they are the same.
 */
public record RepublicationReport(DeletionReport deletion, Optional<ListingRepublication> lastRepublication,
                                  Optional<Listing> newListing, RepublicationMode mode, boolean dryRunPassed,
                                  Optional<List<ListingFieldDifference>> comparison) {

    public RepublicationReport {
        Objects.requireNonNull(deletion);
        Objects.requireNonNull(lastRepublication);
        Objects.requireNonNull(newListing);
        Objects.requireNonNull(mode);
        comparison = Objects.requireNonNull(comparison).map(List::copyOf);
    }
}
