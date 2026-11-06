package fr.vinted.republisher.domain.wardrobe;

import java.util.List;

public interface ListingRepository {

    List<Listing> findAll();

    /** All or nothing: a failure leaves the inventory as it was. */
    void saveAll(List<Listing> listings);
}
