package fr.vinted.republisher.application.sale;

import fr.vinted.republisher.domain.inventory.ListingNotFound;
import fr.vinted.republisher.domain.sale.ListingAlreadySold;
import fr.vinted.republisher.domain.sale.ListingSale;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import fr.vinted.republisher.fakes.AdjustableClock;
import fr.vinted.republisher.fakes.InMemoryListingRepository;
import fr.vinted.republisher.fakes.InMemoryListingSaleRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.anActiveListing;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NoteSaleTest {

    private static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");
    private static final VintedListingId LISTING = new VintedListingId(1);

    private final InMemoryListingRepository listings = new InMemoryListingRepository();
    private final InMemoryListingSaleRepository sales = new InMemoryListingSaleRepository();
    private final NoteSale noteSale = new NoteSale(listings, sales, new AdjustableClock(NOW));

    @Test
    void notes_a_known_listing_sold_now() {
        listings.saveAll(List.of(Listing.seen(anActiveListing(1), NOW)));

        noteSale.note(LISTING);

        assertThat(sales.findAll()).containsExactly(ListingSale.noted(LISTING, NOW));
    }

    @Test
    void refuses_an_unknown_listing() {
        assertThatThrownBy(() -> noteSale.note(LISTING)).isInstanceOf(ListingNotFound.class);
        assertThat(sales.findAll()).isEmpty();
    }

    @Test
    void refuses_a_listing_already_noted_sold() {
        listings.saveAll(List.of(Listing.seen(anActiveListing(1), NOW)));
        noteSale.note(LISTING);

        assertThatThrownBy(() -> noteSale.note(LISTING)).isInstanceOf(ListingAlreadySold.class)
                .hasMessage("Listing 1 already has a sale");
        assertThat(sales.findAll()).hasSize(1);
    }
}
