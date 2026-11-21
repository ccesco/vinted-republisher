package fr.vinted.republisher.domain.wardrobe;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.ACTIVE;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.aListing;
import static org.assertj.core.api.Assertions.assertThat;

class PriceChangeTest {

    private static final Instant FIRST = Instant.parse("2026-11-01T09:00:00Z");
    private static final Instant SECOND = Instant.parse("2026-11-02T09:00:00Z");
    private static final VintedListingId ID = new VintedListingId(101);

    @Test
    void first_price_starts_the_history() {
        Listing saved = Listing.seen(aListing(101, "Pull", "12.50", ACTIVE), FIRST);

        assertThat(PriceChange.of(Optional.empty(), saved))
                .contains(new PriceChange(ID, euros("12.50"), FIRST));
    }

    @Test
    void a_different_amount_is_a_price_change_dated_when_the_listing_was_seen() {
        Listing saved = Listing.seen(aListing(101, "Pull", "10", ACTIVE), SECOND);

        assertThat(PriceChange.of(Optional.of(new PriceChange(ID, euros("12.50"), FIRST)), saved))
                .contains(new PriceChange(ID, euros("10.00"), SECOND));
    }

    @Test
    void an_unchanged_price_is_no_change() {
        Listing saved = Listing.seen(aListing(101, "Pull", "12.5", ACTIVE), SECOND).absent();

        assertThat(PriceChange.of(Optional.of(new PriceChange(ID, euros("12.50"), FIRST)), saved)).isEmpty();
    }

    @Test
    void an_absent_listing_is_no_price_reading() {
        // US-53 : l'ancienne annonce marquée absente après la fusion garde son vieux prix, qui n'est pas un changement
        Listing absent = Listing.seen(aListing(101, "Pull", "18", ACTIVE), SECOND).absent();

        assertThat(PriceChange.of(Optional.of(new PriceChange(ID, euros("20.00"), FIRST)), absent)).isEmpty();
    }

    @Test
    void a_new_currency_is_a_price_change() {
        Listing saved = Listing.seen(aListing(101, "Pull", "12.50", ACTIVE), SECOND);
        PriceChange inPounds = new PriceChange(ID, new Price(new BigDecimal("12.50"), "GBP"), FIRST);

        assertThat(PriceChange.of(Optional.of(inPounds), saved))
                .contains(new PriceChange(ID, euros("12.50"), SECOND));
    }

    private static Price euros(String amount) {
        return new Price(new BigDecimal(amount), "EUR");
    }
}
