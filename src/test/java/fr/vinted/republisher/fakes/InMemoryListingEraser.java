package fr.vinted.republisher.fakes;

import fr.vinted.republisher.domain.duplicate.ListingEraser;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;

/** Erases a listing from the in-memory stores the tests look at. */
public class InMemoryListingEraser implements ListingEraser {

    private final InMemoryListingRepository listings;
    private final InMemoryListingBackupRepository backups;
    private boolean failing;

    public InMemoryListingEraser(InMemoryListingRepository listings, InMemoryListingBackupRepository backups) {
        this.listings = listings;
        this.backups = backups;
    }

    public InMemoryListingEraser whereErasingFails() {
        failing = true;
        return this;
    }

    public InMemoryListingEraser whereErasingWorksAgain() {
        failing = false;
        return this;
    }

    @Override
    public void erase(VintedListingId listing) {
        if (failing) {
            throw new IllegalStateException("database down");
        }
        listings.remove(listing);
        backups.remove(listing);
    }
}
