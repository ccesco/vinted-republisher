package fr.vinted.republisher.domain.duplicate;

import fr.vinted.republisher.domain.wardrobe.VintedListingId;

/** Forgets a listing deleted from Vinted as a duplicate. */
public interface ListingEraser {

    /**
     * All or nothing: erases the listing and everything kept about it (statistics readings, price history, tags,
     * backup with its photos as recorded, validation and verifications), and its article with its tags if no other
     * listing puts it on sale (US-53). The journal keeps its entries; the photo
     * files are not touched.
     */
    void erase(VintedListingId listing);
}
