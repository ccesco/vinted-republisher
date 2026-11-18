package fr.vinted.republisher.adapter.in.web;

import fr.vinted.republisher.application.sale.ShowSales;
import fr.vinted.republisher.domain.sale.ListingSale;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import fr.vinted.republisher.fakes.InMemoryListingRepository;
import fr.vinted.republisher.fakes.InMemoryListingSaleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.Instant;
import java.util.List;

import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.INACTIVE;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.aListing;
import static org.assertj.core.api.Assertions.assertThat;

/** US-36: the API answers as described by the OpenAPI contract. */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@WebMvcTest(SalesApiController.class)
class SalesApiControllerTest {

    private static final Instant NOV_15 = Instant.parse("2026-11-15T10:00:00Z");
    private static final Instant NOV_17 = Instant.parse("2026-11-17T10:00:00Z");

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private InMemoryListingRepository listings;

    @Autowired
    private InMemoryListingSaleRepository sales;

    @Test
    void shows_the_sales_most_recent_first_with_their_totals() {
        // AT-36-06
        listings.saveAll(List.of(Listing.seen(aListing(1, "Pull rayé", "10", INACTIVE), NOV_15),
                Listing.seen(aListing(2, "Jean", "25.50", INACTIVE), NOV_15)));
        sales.addAll(List.of(new ListingSale(new VintedListingId(1), NOV_15),
                new ListingSale(new VintedListingId(2), NOV_17)));

        assertThat(mvc.get().uri("/api/sales"))
                .hasStatusOk()
                .bodyJson().isStrictlyEqualTo("""
                        {"sales": [
                           {"listingId": 2, "title": "Jean", "price": {"amount": 25.50, "currency": "EUR"},
                            "noticedAt": "2026-11-17T10:00:00Z"},
                           {"listingId": 1, "title": "Pull rayé", "price": {"amount": 10.00, "currency": "EUR"},
                            "noticedAt": "2026-11-15T10:00:00Z"}],
                         "totals": [{"amount": 35.50, "currency": "EUR"}]}""");
    }

    @Test
    void no_sale_yet() {
        assertThat(mvc.get().uri("/api/sales")).hasStatusOk()
                .bodyJson().isStrictlyEqualTo("""
                        {"sales": [], "totals": []}""");
    }

    @TestConfiguration
    static class SalesUseCase {

        @Bean
        InMemoryListingRepository listings() {
            return new InMemoryListingRepository();
        }

        @Bean
        InMemoryListingSaleRepository sales() {
            return new InMemoryListingSaleRepository();
        }

        @Bean
        ShowSales showSales(InMemoryListingSaleRepository sales, InMemoryListingRepository listings) {
            return new ShowSales(sales, listings);
        }
    }
}
