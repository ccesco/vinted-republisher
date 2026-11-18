package fr.vinted.republisher.domain.sale;

import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.Price;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SalesOverviewTest {

    private static final Instant NOV_15 = Instant.parse("2026-11-15T10:00:00Z");
    private static final Instant NOV_17 = Instant.parse("2026-11-17T10:00:00Z");

    @Test
    void sales_most_recent_first_with_their_listing() {
        // AT-36-06
        List<Listing> inventory = List.of(listing(1, "Pull rayé", "10", "EUR"), listing(2, "Jean", "25.50", "EUR"));
        List<ListingSale> sales = List.of(new ListingSale(id(1), NOV_15), new ListingSale(id(2), NOV_17));

        SalesOverview overview = SalesOverview.of(sales, inventory);

        assertThat(overview.sales()).containsExactly(
                new Sale(id(2), "Jean", price("25.50", "EUR"), NOV_17),
                new Sale(id(1), "Pull rayé", price("10", "EUR"), NOV_15));
        assertThat(overview.totals()).containsExactly(price("35.50", "EUR"));
    }

    @Test
    void one_total_per_currency_sorted_by_currency() {
        List<Listing> inventory = List.of(listing(1, "Pull", "10", "PLN"), listing(2, "Jean", "5", "EUR"),
                listing(3, "Robe", "2", "PLN"));
        List<ListingSale> sales = List.of(new ListingSale(id(1), NOV_15), new ListingSale(id(2), NOV_15),
                new ListingSale(id(3), NOV_17));

        assertThat(SalesOverview.of(sales, inventory).totals())
                .containsExactly(price("5", "EUR"), price("12", "PLN"));
    }

    @Test
    void sales_noticed_together_are_shown_in_a_stable_order() {
        List<Listing> inventory = List.of(listing(1, "Pull", "10", "EUR"), listing(2, "Jean", "5", "EUR"),
                listing(3, "Robe", "2", "EUR"));
        List<ListingSale> sales = List.of(new ListingSale(id(2), NOV_17), new ListingSale(id(1), NOV_17),
                new ListingSale(id(3), NOV_15));

        assertThat(SalesOverview.of(sales, inventory).sales()).extracting(Sale::listingId)
                .containsExactly(id(1), id(2), id(3));
    }

    @Test
    void no_sale_no_total() {
        SalesOverview overview = SalesOverview.of(List.of(), List.of());

        assertThat(overview.sales()).isEmpty();
        assertThat(overview.totals()).isEmpty();
    }

    private static Listing listing(long id, String title, String amount, String currency) {
        return new Listing(id(id), URI.create("https://vinted.test/items/" + id), title, price(amount, currency), false,
                NOV_15, NOV_15);
    }

    private static Price price(String amount, String currency) {
        return new Price(new BigDecimal(amount), currency);
    }

    private static VintedListingId id(long value) {
        return new VintedListingId(value);
    }
}
