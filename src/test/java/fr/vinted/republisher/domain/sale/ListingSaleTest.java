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
                .containsExactly(ListingSale.noticed(id(2), NOW), ListingSale.noticed(id(3), NOW));
    }

    @Test
    void a_sale_already_recorded_is_not_noticed_again() {
        List<ListingSale> recorded = List.of(ListingSale.noticed(id(2), EARLIER));

        assertThat(ListingSale.newlyNoticed(List.of(id(2), id(3)), INVENTORY, recorded, NOW))
                .containsExactly(ListingSale.noticed(id(3), NOW));
    }

    @Test
    void an_unknown_listing_is_not_a_sale() {
        assertThat(ListingSale.newlyNoticed(List.of(id(9)), INVENTORY, List.of(), NOW)).isEmpty();
    }

    @Test
    void a_listing_shown_sold_twice_is_one_sale() {
        assertThat(ListingSale.newlyNoticed(List.of(id(2), id(2)), INVENTORY, List.of(), NOW))
                .containsExactly(ListingSale.noticed(id(2), NOW));
    }

    @Test
    void newly_noticed_skips_sold_articles() {
        // AT-53-06. L'annonce 1 republiée en 4 : un seul article, déjà vendu par 4
        Listing republished = Listing.seen(anActiveListing(1), EARLIER);
        List<ListingSale> recorded = List.of(ListingSale.noted(id(4), EARLIER));

        assertThat(ListingSale.newlyNoticed(List.of(id(1)), List.of(republished, sameArticle(republished, 4)),
                recorded, NOW)).isEmpty();
    }

    @Test
    void two_listings_of_one_article_shown_sold_are_one_sale() {
        Listing republished = Listing.seen(anActiveListing(1), EARLIER);

        assertThat(ListingSale.newlyNoticed(List.of(id(4), id(1)), List.of(republished, sameArticle(republished, 4)),
                List.of(), NOW)).containsExactly(ListingSale.noticed(id(4), NOW));
    }

    private static Listing sameArticle(Listing listing, long id) {
        return new Listing(id(id), listing.articleId(), listing.url(), listing.title(), listing.price(), true,
                listing.firstSeenAt(), listing.lastSeenAt());
    }

    @Test
    void a_noted_sale_can_be_cancelled_a_noticed_one_cannot() {
        assertThat(ListingSale.noted(id(1), NOW).cancellable()).isTrue();
        assertThat(ListingSale.noticed(id(1), NOW).cancellable()).isFalse();
    }

    @Test
    void a_sale_tells_its_origin() {
        assertThat(ListingSale.noted(id(1), NOW).origin()).isEqualTo(SaleOrigin.NOTED);
        assertThat(ListingSale.noticed(id(1), NOW).origin()).isEqualTo(SaleOrigin.NOTICED);
    }

    private static VintedListingId id(long value) {
        return new VintedListingId(value);
    }
}
