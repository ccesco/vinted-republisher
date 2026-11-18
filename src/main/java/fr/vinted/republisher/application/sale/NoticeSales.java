package fr.vinted.republisher.application.sale;

import fr.vinted.republisher.domain.sale.ListingSale;
import fr.vinted.republisher.domain.sale.ListingSaleRepository;
import fr.vinted.republisher.domain.wardrobe.ListingRepository;
import fr.vinted.republisher.domain.wardrobe.WardrobeListing;

import java.time.Clock;

/** Records the known listings a complete wardrobe shows sold for the first time. Opens and clicks nothing. */
public class NoticeSales {

    private final ListingRepository listings;
    private final ListingSaleRepository sales;
    private final Clock clock;

    public NoticeSales(ListingRepository listings, ListingSaleRepository sales, Clock clock) {
        this.listings = listings;
        this.sales = sales;
        this.clock = clock;
    }

    /** To be called once the wardrobe is known to be complete. */
    public void notice(WardrobeListing wardrobe) {
        if (wardrobe.soldListings().isEmpty()) {
            return;
        }
        sales.addAll(ListingSale.newlyNoticed(wardrobe.soldListings(), listings.findAll(), sales.findAll(),
                clock.instant()));
    }
}
