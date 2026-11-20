package fr.vinted.republisher.adapter.out.persistence;

import fr.vinted.republisher.ApplicationWithDatabaseTest;
import fr.vinted.republisher.domain.sale.ListingAlreadySold;
import fr.vinted.republisher.domain.sale.ListingSale;
import fr.vinted.republisher.domain.sale.ListingSaleRepository;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.ListingRepository;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.List;

import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.anActiveListing;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** On a disposable PostgreSQL with the real changelog, never the real data. */
class JpaListingSaleRepositoryTest extends ApplicationWithDatabaseTest {

    private static final Instant NOTICED_AT = Instant.parse("2026-11-17T10:00:00Z");

    @Autowired
    private ListingSaleRepository sales;

    @Autowired
    private ListingRepository listings;

    @Test
    void no_sale_before_the_first_one() {
        assertThat(sales.findAll()).isEmpty();
    }

    @Test
    void gives_back_the_sales_as_they_were_added() {
        listings.saveAll(List.of(Listing.seen(anActiveListing(1), NOTICED_AT),
                Listing.seen(anActiveListing(2), NOTICED_AT)));
        List<ListingSale> added = List.of(ListingSale.noticed(new VintedListingId(1), NOTICED_AT),
                ListingSale.noticed(new VintedListingId(2), NOTICED_AT.plusSeconds(60)));

        sales.addAll(added);

        assertThat(sales.findAll()).containsExactlyInAnyOrderElementsOf(added);
    }

    @Test
    void only_a_known_listing_can_be_sold() {
        assertThatThrownBy(() -> sales.addAll(List.of(ListingSale.noticed(new VintedListingId(9), NOTICED_AT))))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(sales.findAll()).isEmpty();
    }

    @Test
    void finds_removes_and_keeps_the_origin_of_a_noted_sale() {
        listings.saveAll(List.of(Listing.seen(anActiveListing(1), NOTICED_AT),
                Listing.seen(anActiveListing(2), NOTICED_AT)));
        ListingSale noted = ListingSale.noted(new VintedListingId(1), NOTICED_AT);
        ListingSale noticed = ListingSale.noticed(new VintedListingId(2), NOTICED_AT);
        sales.addAll(List.of(noted, noticed));

        assertThat(sales.find(new VintedListingId(1))).contains(noted);
        assertThat(sales.find(new VintedListingId(3))).isEmpty();

        sales.removeNoted(new VintedListingId(1));

        assertThat(sales.findAll()).containsExactly(noticed);
    }

    @Test
    void a_noted_sale_is_added_once_and_never_replaces_a_sale() {
        listings.saveAll(List.of(Listing.seen(anActiveListing(1), NOTICED_AT)));
        ListingSale noticed = ListingSale.noticed(new VintedListingId(1), NOTICED_AT);
        sales.addAll(List.of(noticed));

        assertThatThrownBy(() -> sales.add(ListingSale.noted(new VintedListingId(1), NOTICED_AT.plusSeconds(60))))
                .isInstanceOf(ListingAlreadySold.class);

        assertThat(sales.findAll()).containsExactly(noticed);
    }

    @Test
    void a_reading_never_overwrites_a_sale() {
        listings.saveAll(List.of(Listing.seen(anActiveListing(1), NOTICED_AT)));
        ListingSale noted = ListingSale.noted(new VintedListingId(1), NOTICED_AT);
        sales.add(noted);

        assertThatThrownBy(() -> sales.addAll(List.of(
                ListingSale.noticed(new VintedListingId(1), NOTICED_AT.plusSeconds(60)))))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(sales.findAll()).containsExactly(noted);
    }

    @Test
    void removing_the_noted_sale_never_removes_a_noticed_one() {
        listings.saveAll(List.of(Listing.seen(anActiveListing(1), NOTICED_AT)));
        ListingSale noticed = ListingSale.noticed(new VintedListingId(1), NOTICED_AT);
        sales.addAll(List.of(noticed));

        sales.removeNoted(new VintedListingId(1));

        assertThat(sales.findAll()).containsExactly(noticed);
    }
}
