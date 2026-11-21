package fr.vinted.republisher.adapter.config;

import fr.vinted.republisher.application.sale.CancelNotedSale;
import fr.vinted.republisher.application.sale.NoteSale;
import fr.vinted.republisher.application.sale.NoticeSales;
import fr.vinted.republisher.application.sale.ShowSales;
import fr.vinted.republisher.domain.sale.ListingSaleRepository;
import fr.vinted.republisher.domain.wardrobe.ListingRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
class SaleConfiguration {

    @Bean
    NoticeSales noticeSales(ListingRepository listings, ListingSaleRepository sales, Clock clock) {
        return new NoticeSales(listings, sales, clock);
    }

    @Bean
    ShowSales showSales(ListingSaleRepository sales, ListingRepository listings) {
        return new ShowSales(sales, listings);
    }

    @Bean
    NoteSale noteSale(ListingRepository listings, ListingSaleRepository sales, Clock clock) {
        return new NoteSale(listings, sales, clock);
    }

    @Bean
    CancelNotedSale cancelNotedSale(ListingRepository listings, ListingSaleRepository sales) {
        return new CancelNotedSale(listings, sales);
    }
}
