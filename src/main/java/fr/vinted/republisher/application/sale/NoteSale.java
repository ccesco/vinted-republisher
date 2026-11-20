package fr.vinted.republisher.application.sale;

import fr.vinted.republisher.domain.inventory.ListingNotFound;
import fr.vinted.republisher.domain.sale.ListingSale;
import fr.vinted.republisher.domain.sale.ListingSaleRepository;
import fr.vinted.republisher.domain.wardrobe.ListingRepository;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.time.Clock;

/**
 * The member notes a known listing sold, whatever its stage, dated now; refused with
 * {@link fr.vinted.republisher.domain.sale.ListingAlreadySold} if it already has a sale. Nothing is done on Vinted (US-51).
 */
public class NoteSale {

    private final ListingRepository listings;
    private final ListingSaleRepository sales;
    private final Clock clock;

    public NoteSale(ListingRepository listings, ListingSaleRepository sales, Clock clock) {
        this.listings = listings;
        this.sales = sales;
        this.clock = clock;
    }

    public void note(VintedListingId listing) {
        listings.find(listing).orElseThrow(() -> new ListingNotFound(listing));
        sales.add(ListingSale.noted(listing, clock.instant()));
    }
}
