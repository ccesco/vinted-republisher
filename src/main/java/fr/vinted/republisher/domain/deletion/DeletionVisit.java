package fr.vinted.republisher.domain.deletion;

import fr.vinted.republisher.domain.wardrobe.ScannedListing;

/** What the deletion decides on the page of the listing, at each step before a click. */
public interface DeletionVisit {

    /** Once the listing is read and its delete button found, before any click: whether to click it. */
    boolean agreesToDelete(ScannedListing read);

    /** Once Vinted shows its confirmation, right before clicking it: the click that cannot be undone. */
    void aboutToConfirm();
}
