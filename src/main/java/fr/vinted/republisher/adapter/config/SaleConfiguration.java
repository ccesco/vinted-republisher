package fr.vinted.republisher.adapter.config;

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
}
