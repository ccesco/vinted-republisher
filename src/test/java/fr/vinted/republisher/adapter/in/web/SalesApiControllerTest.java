package fr.vinted.republisher.adapter.in.web;

import fr.vinted.republisher.application.sale.CancelNotedSale;
import fr.vinted.republisher.application.sale.NoteSale;
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

import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
    private static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");

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
        sales.addAll(List.of(ListingSale.noticed(new VintedListingId(1), NOV_15),
                ListingSale.noticed(new VintedListingId(2), NOV_17)));

        assertThat(mvc.get().uri("/api/sales"))
                .hasStatusOk()
                .bodyJson().isStrictlyEqualTo("""
                        {"sales": [
                           {"listingId": 2, "title": "Jean", "price": {"amount": 25.50, "currency": "EUR"},
                            "noticedAt": "2026-11-17T10:00:00Z", "origin": "NOTICED"},
                           {"listingId": 1, "title": "Pull rayé", "price": {"amount": 10.00, "currency": "EUR"},
                            "noticedAt": "2026-11-15T10:00:00Z", "origin": "NOTICED"}],
                         "totals": [{"amount": 35.50, "currency": "EUR"}]}""");
    }

    @Test
    void no_sale_yet() {
        assertThat(mvc.get().uri("/api/sales")).hasStatusOk()
                .bodyJson().isStrictlyEqualTo("""
                        {"sales": [], "totals": []}""");
    }

    @Test
    void notes_a_listing_sold_now() {
        // AT-51-08
        aKnownListing();

        assertThat(mvc.put().uri("/api/listings/1/sale")).hasStatus(HttpStatus.NO_CONTENT);

        assertThat(sales.findAll()).containsExactly(ListingSale.noted(new VintedListingId(1), NOW));
        assertThat(mvc.get().uri("/api/sales")).bodyJson().extractingPath("$.sales[0].origin").isEqualTo("NOTED");
    }

    @Test
    void noting_a_listing_already_sold_is_a_conflict() {
        aKnownListing();
        sales.addAll(List.of(ListingSale.noticed(new VintedListingId(1), NOV_15)));

        assertThat(mvc.put().uri("/api/listings/1/sale")).hasStatus(HttpStatus.CONFLICT)
                .bodyJson().isStrictlyEqualTo("""
                        {"reason": "ALREADY_SOLD"}""");
    }

    @Test
    void cancels_a_noted_sale() {
        aKnownListing();
        sales.addAll(List.of(ListingSale.noted(new VintedListingId(1), NOV_15)));

        assertThat(mvc.delete().uri("/api/listings/1/sale")).hasStatus(HttpStatus.NO_CONTENT);

        assertThat(sales.findAll()).isEmpty();
    }

    @Test
    void cancelling_a_noticed_sale_is_a_conflict() {
        aKnownListing();
        sales.addAll(List.of(ListingSale.noticed(new VintedListingId(1), NOV_15)));

        assertThat(mvc.delete().uri("/api/listings/1/sale")).hasStatus(HttpStatus.CONFLICT)
                .bodyJson().isStrictlyEqualTo("""
                        {"reason": "NOT_NOTED"}""");
    }

    @Test
    void unknown_listing_or_missing_sale_is_not_found() {
        assertThat(mvc.put().uri("/api/listings/1/sale")).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.delete().uri("/api/listings/1/sale")).hasStatus(HttpStatus.NOT_FOUND);
        aKnownListing();
        assertThat(mvc.delete().uri("/api/listings/1/sale")).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void a_request_from_another_site_is_refused() {
        aKnownListing();
        sales.addAll(List.of(ListingSale.noted(new VintedListingId(1), NOV_15)));

        assertThat(mvc.put().uri("/api/listings/1/sale").header("Origin", "https://example.test"))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mvc.delete().uri("/api/listings/1/sale").header("Origin", "https://example.test"))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(sales.findAll()).containsExactly(ListingSale.noted(new VintedListingId(1), NOV_15));
    }

    private void aKnownListing() {
        listings.saveAll(List.of(Listing.seen(aListing(1, "Pull rayé", "10", INACTIVE), NOV_15)));
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

        @Bean
        NoteSale noteSale(InMemoryListingRepository listings, InMemoryListingSaleRepository sales) {
            return new NoteSale(listings, sales, Clock.fixed(NOW, ZoneOffset.UTC));
        }

        @Bean
        CancelNotedSale cancelNotedSale(InMemoryListingRepository listings, InMemoryListingSaleRepository sales) {
            return new CancelNotedSale(listings, sales);
        }
    }
}
