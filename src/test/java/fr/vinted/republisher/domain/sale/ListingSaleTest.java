package fr.vinted.republisher.domain.sale;

import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.anActiveListing;
import static org.assertj.core.api.Assertions.assertThat;

class ListingSaleTest {

    private static final Instant EARLIER = Instant.parse("2026-11-15T10:00:00Z");
    private static final Instant NOW = Instant.parse("2026-11-17T10:00:00Z");

    private static final List<Listing> INVENTORY = List.of(Listing.seen(anActiveListing(1), EARLIER),
            Listing.seen(anActiveListing(2), EARLIER), Listing.seen(anActiveListing(3), EARLIER));

    @Test
    void a_known_listing_shown_sold_is_a_new_sale_noticed_now() {
        assertThat(ListingSale.newlyNoticed(List.of(id(2), id(3)), INVENTORY, List.of(), NOW))
                .containsExactly(new ListingSale(id(2), NOW), new ListingSale(id(3), NOW));
    }

    @Test
    void a_sale_already_recorded_is_not_noticed_again() {
        List<ListingSale> recorded = List.of(new ListingSale(id(2), EARLIER));

        assertThat(ListingSale.newlyNoticed(List.of(id(2), id(3)), INVENTORY, recorded, NOW))
                .containsExactly(new ListingSale(id(3), NOW));
    }

    @Test
    void an_unknown_listing_is_not_a_sale() {
        assertThat(ListingSale.newlyNoticed(List.of(id(9)), INVENTORY, List.of(), NOW)).isEmpty();
    }

    @Test
    void a_listing_shown_sold_twice_is_one_sale() {
        assertThat(ListingSale.newlyNoticed(List.of(id(2), id(2)), INVENTORY, List.of(), NOW))
                .containsExactly(new ListingSale(id(2), NOW));
    }

    private static VintedListingId id(long value) {
        return new VintedListingId(value);
    }
}
