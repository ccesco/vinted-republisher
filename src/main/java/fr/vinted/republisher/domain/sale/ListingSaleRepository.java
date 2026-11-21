package fr.vinted.republisher.domain.sale;

import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.util.List;
import java.util.Optional;

public interface ListingSaleRepository {

    List<ListingSale> findAll();

    Optional<ListingSale> find(VintedListingId listing);

    /** Inserts only, all or nothing: a sale already recorded for the article of a listing fails the whole call. */
    void addAll(List<ListingSale> sales);

    /** Inserts only, atomically: {@link ListingAlreadySold} if its article (US-53) already has a sale, kept. */
    void add(ListingSale sale);

    /** Removes the listing's sale only if it was noted: a noticed sale is never removed. */
    void removeNoted(VintedListingId listing);
}
