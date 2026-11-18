package fr.vinted.republisher.application.sale;

import fr.vinted.republisher.domain.sale.ListingSaleRepository;
import fr.vinted.republisher.domain.sale.SalesOverview;
import fr.vinted.republisher.domain.wardrobe.ListingRepository;

/** Read only: everything comes from the database, nothing is asked of Vinted. */
public class ShowSales {

    private final ListingSaleRepository sales;
    private final ListingRepository listings;

    public ShowSales(ListingSaleRepository sales, ListingRepository listings) {
        this.sales = sales;
        this.listings = listings;
    }

    public SalesOverview show() {
        return SalesOverview.of(sales.findAll(), listings.findAll());
    }
}
