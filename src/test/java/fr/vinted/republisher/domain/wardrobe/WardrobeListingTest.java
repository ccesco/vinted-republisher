package fr.vinted.republisher.domain.wardrobe;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.aLink;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WardrobeListingTest {

    private static final Instant READ_AT = Instant.parse("2026-11-11T10:00:00Z");

    @Test
    void statistics_shown_on_the_cards_become_readings() {
        ListingStatistics statistics = new ListingStatistics(12, 3);
        WardrobeListing wardrobe = new WardrobeListing(2, List.of(aLink(1).showing(statistics), aLink(2)));

        assertThat(wardrobe.statisticsReadAt(READ_AT))
                .containsExactly(new StatisticsReading(new VintedListingId(1), statistics, READ_AT));
    }

    @Test
    void complete_when_every_listing_announced_was_found() {
        WardrobeListing wardrobe = new WardrobeListing(2, List.of(aLink(1), aLink(2)));

        assertThatCode(wardrobe::requireComplete).doesNotThrowAnyException();
    }

    @Test
    void unreadable_when_fewer_links_than_announced_were_found() {
        WardrobeListing wardrobe = new WardrobeListing(3, List.of(aLink(1), aLink(2)));

        assertThatThrownBy(wardrobe::requireComplete)
                .isInstanceOf(WardrobeUnreadable.class)
                .hasMessage("The wardrobe cannot be read: 2 links found, 3 announced");
    }

    @Test
    void unreadable_when_more_links_than_announced_were_found() {
        WardrobeListing wardrobe = new WardrobeListing(1, List.of(aLink(1), aLink(2)));

        assertThatThrownBy(wardrobe::requireComplete)
                .isInstanceOf(WardrobeUnreadable.class)
                .hasMessage("The wardrobe cannot be read: 2 links found, 1 announced");
    }

    @Test
    void unreadable_when_no_link_was_found_even_if_none_was_announced() {
        WardrobeListing wardrobe = new WardrobeListing(0, List.of());

        assertThatThrownBy(wardrobe::requireComplete)
                .isInstanceOf(WardrobeUnreadable.class)
                .hasMessage("The wardrobe cannot be read: no link found");
    }

    @Test
    void link_found_twice_counts_once() {
        assertThat(new WardrobeListing(1, List.of(aLink(1), aLink(1))).links()).containsExactly(aLink(1));
    }

    @Test
    void same_listing_at_two_addresses_counts_once() {
        ListingLink otherAddress = new ListingLink(aLink(1).id(), URI.create("https://vinted.test/items/1-pull"));

        assertThat(new WardrobeListing(2, List.of(aLink(1), otherAddress)).links()).containsExactly(aLink(1));
    }

    @Test
    void further_links_add_up_without_duplicates() {
        WardrobeListing wardrobe = new WardrobeListing(2, List.of(aLink(1))).with(List.of(aLink(1), aLink(2)));

        assertThat(wardrobe.links()).containsExactly(aLink(1), aLink(2));
    }

    @Test
    void knows_when_all_the_links_announced_were_found() {
        assertThat(new WardrobeListing(2, List.of(aLink(1))).allLinksFound()).isFalse();
        assertThat(new WardrobeListing(1, List.of(aLink(1))).allLinksFound()).isTrue();
    }
}
