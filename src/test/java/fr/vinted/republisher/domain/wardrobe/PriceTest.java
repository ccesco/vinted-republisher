package fr.vinted.republisher.domain.wardrobe;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PriceTest {

    @Test
    void same_amount_written_differently_is_the_same_price() {
        assertThat(new Price(new BigDecimal("1.5"), "EUR")).isEqualTo(new Price(new BigDecimal("1.50"), "EUR"));
    }

    @Test
    void refuses_a_negative_amount() {
        assertThatThrownBy(() -> new Price(new BigDecimal("-0.01"), "EUR")).isInstanceOf(InvalidListing.class);
    }

    @Test
    void refuses_more_than_two_decimals() {
        assertThatThrownBy(() -> new Price(new BigDecimal("1.505"), "EUR")).isInstanceOf(InvalidListing.class);
    }

    @Test
    void refuses_more_digits_than_the_inventory_can_hold() {
        assertThatThrownBy(() -> new Price(new BigDecimal("100000000"), "EUR")).isInstanceOf(InvalidListing.class);
    }

    @Test
    void refuses_a_currency_that_is_not_a_three_letter_code() {
        assertThatThrownBy(() -> new Price(BigDecimal.ONE, "euro")).isInstanceOf(InvalidListing.class);
    }
}
