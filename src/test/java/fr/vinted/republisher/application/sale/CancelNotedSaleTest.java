package fr.vinted.republisher.application.sale;

import fr.vinted.republisher.domain.inventory.ListingNotFound;
import fr.vinted.republisher.domain.sale.ListingNotSold;
import fr.vinted.republisher.domain.sale.ListingSale;
import fr.vinted.republisher.domain.sale.SaleNotNoted;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import fr.vinted.republisher.fakes.InMemoryListingRepository;
import fr.vinted.republisher.fakes.InMemoryListingSaleRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.anActiveListing;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CancelNotedSaleTest {

    private static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");
    private static final VintedListingId LISTING = new VintedListingId(1);

    private final InMemoryListingRepository listings = new InMemoryListingRepository();
    private final InMemoryListingSaleRepository sales = new InMemoryListingSaleRepository();
    private final CancelNotedSale cancelNotedSale = new CancelNotedSale(listings, sales);

    @Test
    void removes_a_noted_sale() {
        listings.saveAll(List.of(Listing.seen(anActiveListing(1), NOW)));
        sales.addAll(List.of(ListingSale.noted(LISTING, NOW)));

        cancelNotedSale.cancel(LISTING);

        assertThat(sales.findAll()).isEmpty();
    }

    @Test
    void refuses_an_unknown_listing() {
        assertThatThrownBy(() -> cancelNotedSale.cancel(LISTING)).isInstanceOf(ListingNotFound.class);
    }

    @Test
    void refuses_a_listing_without_sale() {
        listings.saveAll(List.of(Listing.seen(anActiveListing(1), NOW)));

        assertThatThrownBy(() -> cancelNotedSale.cancel(LISTING)).isInstanceOf(ListingNotSold.class)
                .hasMessage("Listing 1 has no sale");
    }

    @Test
    void refuses_a_noticed_sale() {
        listings.saveAll(List.of(Listing.seen(anActiveListing(1), NOW)));
        ListingSale noticed = ListingSale.noticed(LISTING, NOW);
        sales.addAll(List.of(noticed));

        assertThatThrownBy(() -> cancelNotedSale.cancel(LISTING)).isInstanceOf(SaleNotNoted.class);
        assertThat(sales.findAll()).containsExactly(noticed);
    }
}
