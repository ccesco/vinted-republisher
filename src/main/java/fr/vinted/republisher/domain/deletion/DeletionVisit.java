package fr.vinted.republisher.domain.deletion;

import fr.vinted.republisher.domain.wardrobe.ScannedListing;

/** What the deletion decides once the listing is read on Vinted and its delete button found, before any click. */
@FunctionalInterface
public interface DeletionVisit {

    /** Whether to click the delete button and Vinted's confirmation. */
    boolean agreesToDelete(ScannedListing read);
}
