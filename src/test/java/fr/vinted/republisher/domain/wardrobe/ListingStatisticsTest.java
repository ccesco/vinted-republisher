package fr.vinted.republisher.domain.wardrobe;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ListingStatisticsTest {

    @Test
    void card_shows_views_and_favourites() {
        assertThat(ListingStatistics.shownBy(List.of("12 vues", "3 favoris")))
                .contains(new ListingStatistics(12, 3));
    }

    @Test
    void singular_and_zero_are_read() {
        assertThat(ListingStatistics.shownBy(List.of("1 vue", "1 favori"))).contains(new ListingStatistics(1, 1));
        assertThat(ListingStatistics.shownBy(List.of("0 vues", "0 favoris"))).contains(new ListingStatistics(0, 0));
    }

    @Test
    void thousands_may_be_separated_by_spaces() {
        assertThat(ListingStatistics.shownBy(List.of("1 234 vues", "2 345 favoris", "Booster")))
                .contains(new ListingStatistics(1234, 2345));
        assertThat(ListingStatistics.shownBy(List.of("1 234 vues", " 2 favoris ")))
                .contains(new ListingStatistics(1234, 2));
    }

    @Test
    void texts_are_recognised_whatever_their_order() {
        assertThat(ListingStatistics.shownBy(List.of("3 favoris", "Nike", "12 vues")))
                .contains(new ListingStatistics(12, 3));
    }

    @Test
    void card_without_both_numbers_shows_no_statistics() {
        assertThat(ListingStatistics.shownBy(List.of("12 vues"))).isEmpty();
        assertThat(ListingStatistics.shownBy(List.of("3 favoris"))).isEmpty();
        assertThat(ListingStatistics.shownBy(List.of("Nike", "19 · Très bon état", "5,00 €"))).isEmpty();
        assertThat(ListingStatistics.shownBy(List.of())).isEmpty();
    }

    @Test
    void numbers_in_another_form_are_not_read() {
        assertThat(ListingStatistics.shownBy(List.of("1,2 k vues", "3 favoris"))).isEmpty();
        assertThat(ListingStatistics.shownBy(List.of("12 vues", "-3 favoris"))).isEmpty();
        assertThat(ListingStatistics.shownBy(List.of("12 vues au total", "3 favoris"))).isEmpty();
        assertThat(ListingStatistics.shownBy(List.of("99999999999 vues", "3 favoris"))).isEmpty();
    }

    @Test
    void two_different_counts_of_views_cannot_be_trusted() {
        assertThat(ListingStatistics.shownBy(List.of("12 vues", "13 vues", "3 favoris"))).isEmpty();
    }

    @Test
    void negative_counts_are_refused() {
        assertThatThrownBy(() -> new ListingStatistics(-1, 0)).isInstanceOf(InvalidListing.class);
        assertThatThrownBy(() -> new ListingStatistics(0, -1)).isInstanceOf(InvalidListing.class);
    }

    @Test
    void no_statistics_is_an_empty_reading() {
        assertThat(ListingStatistics.shownBy(List.of("", " "))).isEqualTo(Optional.empty());
    }
}
