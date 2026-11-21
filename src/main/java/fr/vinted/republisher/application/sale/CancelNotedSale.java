package fr.vinted.republisher.application.sale;

import fr.vinted.republisher.domain.inventory.ListingNotFound;
import fr.vinted.republisher.domain.sale.ListingNotSold;
import fr.vinted.republisher.domain.sale.ListingSale;
import fr.vinted.republisher.domain.sale.ListingSaleRepository;
import fr.vinted.republisher.domain.sale.SaleNotNoted;
import fr.vinted.republisher.domain.wardrobe.ListingRepository;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;

/** The member cancels the sale they noted by mistake: the listing's stage is deduced again without it (US-51). */
public class CancelNotedSale {

    private final ListingRepository listings;
    private final ListingSaleRepository sales;

    public CancelNotedSale(ListingRepository listings, ListingSaleRepository sales) {
        this.listings = listings;
        this.sales = sales;
    }

    public void cancel(VintedListingId listing) {
        listings.find(listing).orElseThrow(() -> new ListingNotFound(listing));
        ListingSale sale = sales.find(listing).orElseThrow(() -> new ListingNotSold(listing));
        if (!sale.cancellable()) {
            throw new SaleNotNoted(listing);
        }
        sales.removeNoted(listing);
    }
}
