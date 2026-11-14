package fr.vinted.republisher.domain.deletion;

import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.util.Optional;

/** Every deletion attempt, never removed. */
public interface ListingDeletionRepository {

    /** The most recent attempt on the listing. */
    Optional<ListingDeletion> findLatest(VintedListingId listing);

    /** Records a new attempt, or the settlement of an existing one. */
    void save(ListingDeletion deletion);
}
