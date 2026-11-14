package fr.vinted.republisher.domain.republication;

import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.util.Optional;

/** Every republication that reached the click on Publish, never removed: at most one per listing. */
public interface ListingRepublicationRepository {

    Optional<ListingRepublication> find(VintedListingId listing);

    /** Records a new attempt, or its success. */
    void save(ListingRepublication republication);
}
