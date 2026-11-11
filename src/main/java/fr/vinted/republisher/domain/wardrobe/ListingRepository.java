package fr.vinted.republisher.domain.wardrobe;

import java.util.List;
import java.util.Optional;

public interface ListingRepository {

    List<Listing> findAll();

    Optional<Listing> find(VintedListingId listing);

    /** All or nothing: a failure leaves the inventory as it was. */
    void saveAll(List<Listing> listings);
}
