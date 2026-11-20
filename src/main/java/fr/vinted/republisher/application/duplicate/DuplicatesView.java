package fr.vinted.republisher.application.duplicate;

import fr.vinted.republisher.domain.duplicate.DuplicateGroups;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The duplicates of the wardrobe, with the listings that have a history to keep, those backed up, and whether
 * deletions are dry runs.
 */
public record DuplicatesView(DuplicateGroups groups, Set<VintedListingId> withHistory,
                             Set<VintedListingId> backedUp, boolean dryRun) {

    public DuplicatesView {
        Objects.requireNonNull(groups);
        withHistory = Set.copyOf(withHistory);
        backedUp = Set.copyOf(backedUp);
    }

    public List<Listing> twinsOf(VintedListingId listing) {
        return groups.twinsOf(listing);
    }

    public boolean deletable(VintedListingId listing) {
        return groups.deletable(listing, withHistory);
    }

    public boolean backedUp(VintedListingId listing) {
        return backedUp.contains(listing);
    }

    /** The twins of a listing that may be deleted as a duplicate; refuses any other. */
    List<Listing> requireDeletable(VintedListingId listing) {
        return groups.requireDeletable(listing, withHistory);
    }
}
