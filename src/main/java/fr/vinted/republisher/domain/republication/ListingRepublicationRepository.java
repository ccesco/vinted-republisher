package fr.vinted.republisher.domain.republication;

import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Every republication that reached the click on Publish, never removed: several per listing once checked not published. */
public interface ListingRepublicationRepository {

    /** The last attempt for the listing: the one that decides whether it can be republished. */
    Optional<ListingRepublication> findLatest(VintedListingId listing);

    /** The last attempt of every listing. */
    List<ListingRepublication> findAllLatest();

    /** The new listings of every published republication. */
    Set<VintedListingId> findNewListings();

    /** Records a new attempt, or how it was settled. */
    void save(ListingRepublication republication);
}
