package fr.vinted.republisher.adapter.out.browser;

import fr.vinted.republisher.domain.deletion.DeletionVisit;
import fr.vinted.republisher.domain.deletion.ListingDeleter;
import fr.vinted.republisher.domain.deletion.ListingPageUnreadable;
import fr.vinted.republisher.domain.wardrobe.ListingLink;
import fr.vinted.republisher.domain.wardrobe.ScannedListing;

import java.util.Optional;

/**
 * Stands in for the browser adapter until the delete button and Vinted's confirmation are observed with the member
 * (AT-09-20): it never opens Chrome, so nothing can be clicked on the real account.
 */
public class UnobservedListingDeleter implements ListingDeleter {

    private static final String NOT_OBSERVED =
            "deleting on Vinted is not available until its page is observed with the member (AT-09-20)";

    @Override
    public Optional<ScannedListing> delete(ListingLink listing, DeletionVisit visit) {
        throw new ListingPageUnreadable(NOT_OBSERVED);
    }

    @Override
    public Optional<ScannedListing> show(ListingLink listing) {
        throw new ListingPageUnreadable(NOT_OBSERVED);
    }
}
