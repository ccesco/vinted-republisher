package fr.vinted.republisher.domain.wardrobe;

import java.util.List;
import java.util.Optional;

public interface ListingRepository {

    List<Listing> findAll();

    Optional<Listing> find(VintedListingId listing);

    /**
     * All or nothing: a failure leaves the inventory as it was. Adds to the price history of each listing the
     * {@link PriceChange} its price makes, if any.
     */
    void saveAll(List<Listing> listings);

    /** Every price the listing had, most recent first. */
    List<PriceChange> priceHistoryOf(VintedListingId listing);
}
