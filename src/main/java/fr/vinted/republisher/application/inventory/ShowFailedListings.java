package fr.vinted.republisher.application.inventory;

import fr.vinted.republisher.domain.inventory.FailedListing;
import fr.vinted.republisher.domain.inventory.InventoryCriteria;
import fr.vinted.republisher.domain.journal.ActionJournal;

import java.util.List;

/** Reads the journal and what the tool keeps, never Vinted. */
public class ShowFailedListings {

    private final ActionJournal journal;
    private final ShowListingInventory inventory;

    public ShowFailedListings(ActionJournal journal, ShowListingInventory inventory) {
        this.journal = journal;
        this.inventory = inventory;
    }

    /** Most recent failure first. */
    public List<FailedListing> show() {
        return FailedListing.among(inventory.show(InventoryCriteria.everyListing()), journal.lastOfEachListing());
    }
}
