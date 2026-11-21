package fr.vinted.republisher.domain.inventory;

import fr.vinted.republisher.domain.cadence.WriteOrigin;
import fr.vinted.republisher.domain.republication.ListingRepublication;
import fr.vinted.republisher.domain.republication.ListingRepublicationId;
import fr.vinted.republisher.domain.republication.RepublicationOutcome;
import fr.vinted.republisher.domain.wardrobe.ListingStatistics;
import fr.vinted.republisher.domain.wardrobe.StatisticsReading;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StatisticsComparisonTest {

    private static final Instant READ_AT = Instant.parse("2026-11-09T09:00:00Z");
    private static final Instant PUBLISHED_AT = Instant.parse("2026-11-10T09:00:00Z");

    @Test
    void before_is_the_replaced_listing_and_the_total_adds_up_the_whole_chain() {
        StatisticsComparison comparison = StatisticsComparison.of(published(2, 3),
                List.of(Optional.of(reading(2, 40, 3)), Optional.of(reading(1, 100, 5))),
                Optional.of(reading(3, 10, 1)));

        assertThat(comparison).isEqualTo(new StatisticsComparison(new VintedListingId(2), PUBLISHED_AT,
                Optional.of(reading(2, 40, 3)), Optional.of(reading(3, 10, 1)), new ListingStatistics(150, 9), 2));
    }

    @Test
    void a_listing_never_read_counts_for_nothing() {
        StatisticsComparison comparison = StatisticsComparison.of(published(1, 2),
                List.of(Optional.empty()), Optional.of(reading(2, 10, 1)));

        assertThat(comparison.before()).isEmpty();
        assertThat(comparison.cumulated()).isEqualTo(new ListingStatistics(10, 1));
        assertThat(comparison.previousListingCount()).isEqualTo(1);
    }

    @Test
    void only_a_published_republication_with_a_previous_listing_is_compared() {
        ListingRepublication failed = new ListingRepublication(new ListingRepublicationId(UUID.randomUUID()),
                new VintedListingId(1), READ_AT, READ_AT, RepublicationOutcome.FAILED, Optional.empty(),
                Optional.empty(), Optional.empty(), WriteOrigin.RUN);

        assertThatThrownBy(() -> StatisticsComparison.of(failed, List.of(Optional.empty()), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StatisticsComparison.of(published(1, 2), List.of(), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static ListingRepublication published(long listing, long newListing) {
        return new ListingRepublication(new ListingRepublicationId(UUID.randomUUID()), new VintedListingId(listing),
                READ_AT, PUBLISHED_AT.minusSeconds(60), RepublicationOutcome.PUBLISHED, Optional.of(PUBLISHED_AT),
                Optional.of(new VintedListingId(newListing)), Optional.empty(), WriteOrigin.RUN);
    }

    private static StatisticsReading reading(long listing, int views, int favourites) {
        return new StatisticsReading(new VintedListingId(listing), new ListingStatistics(views, favourites), READ_AT);
    }
}
