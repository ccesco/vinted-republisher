package fr.vinted.republisher.domain.deletion;

import fr.vinted.republisher.domain.wardrobe.ListingLink;
import fr.vinted.republisher.domain.wardrobe.ScannedListing;

import java.util.Optional;

/**
 * Deletes a listing on Vinted as the member would, in one tab closed at the end. Never calls Vinted's API directly.
 * Both methods throw {@code BrowserUnavailable}, {@code VintedUnreachable}, {@code SessionDisconnected} or
 * {@link ListingPageUnreadable}.
 */
public interface ListingDeleter {

    /**
     * Opens the page of the listing, reads it, makes sure the member is connected and the delete button is there,
     * then asks {@code visit}, before any click. If it agrees, clicks the delete button and Vinted's confirmation,
     * then opens the page again. Answers with the listing as Vinted shows it in the end: empty once it no longer shows
     * it. Lets through what {@code visit} throws.
     */
    Optional<ScannedListing> delete(ListingLink listing, DeletionVisit visit);

    /** Only opens the page of the listing, without any click: empty if Vinted no longer shows it. */
    Optional<ScannedListing> show(ListingLink listing);
}
