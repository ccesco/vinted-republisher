package fr.vinted.republisher.domain.inventory;

import fr.vinted.republisher.domain.wardrobe.ListingStatistics;
import fr.vinted.republisher.domain.wardrobe.StatisticsReading;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RepublicationAdviceTest {

    private static final Instant NOW = Instant.parse("2026-11-17T10:00:00Z");
    private static final Instant WEEK_AGO = NOW.minus(Duration.ofDays(7));
    private static final Instant NOV_8 = Instant.parse("2026-11-08T10:00:00Z");
    private static final Instant NOV_16 = Instant.parse("2026-11-16T10:00:00Z");

    @Test
    void advises_a_listing_that_gained_fewer_than_ten_views_in_a_week() {
        assertThat(advice(ListingStage.TO_BACK_UP, reading(49, 4, NOV_16), reading(40, 4, NOV_8)))
                .contains(new RepublicationAdvice(9, NOV_8));
    }

    @Test
    void does_not_advise_a_listing_that_gained_ten_views() {
        assertThat(advice(ListingStage.TO_BACK_UP, reading(50, 0, NOV_16), reading(40, 0, NOV_8))).isEmpty();
    }

    @Test
    void five_favourites_protect_the_listing() {
        assertThat(advice(ListingStage.TO_BACK_UP, reading(40, 5, NOV_16), reading(40, 5, NOV_8))).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = ListingStage.class, names = {"TO_BACK_UP", "TO_VALIDATE", "TO_DELETE"})
    void advises_a_listing_on_sale(ListingStage stage) {
        assertThat(advice(stage, reading(40, 0, NOV_16), reading(40, 0, NOV_8))).isPresent();
    }

    @ParameterizedTest
    @EnumSource(value = ListingStage.class, names = {"TO_BACK_UP", "TO_VALIDATE", "TO_DELETE"},
            mode = EnumSource.Mode.EXCLUDE)
    void does_not_advise_a_listing_not_on_sale(ListingStage stage) {
        assertThat(advice(stage, reading(40, 0, NOV_16), reading(40, 0, NOV_8))).isEmpty();
    }

    @Test
    void does_not_advise_without_a_baseline_or_a_latest_reading() {
        assertThat(RepublicationAdvice.of(ListingStage.TO_BACK_UP, Optional.of(reading(40, 0, NOV_16)),
                Optional.empty(), NOW)).isEmpty();
        assertThat(RepublicationAdvice.of(ListingStage.TO_BACK_UP, Optional.empty(), Optional.empty(), NOW))
                .isEmpty();
    }

    @Test
    void does_not_advise_on_a_latest_reading_a_week_old() {
        assertThat(advice(ListingStage.TO_BACK_UP, reading(40, 0, WEEK_AGO), reading(40, 0, WEEK_AGO))).isEmpty();
        assertThat(advice(ListingStage.TO_BACK_UP, reading(40, 0, WEEK_AGO.plusSeconds(1)), reading(40, 0, WEEK_AGO)))
                .contains(new RepublicationAdvice(0, WEEK_AGO));
    }

    @Test
    void views_going_down_count_as_none_gained() {
        assertThat(advice(ListingStage.TO_BACK_UP, reading(30, 0, NOV_16), reading(40, 0, NOV_8)))
                .contains(new RepublicationAdvice(0, NOV_8));
    }

    @Test
    void rejects_negative_views_gained() {
        assertThatThrownBy(() -> new RepublicationAdvice(-1, NOV_8)).isInstanceOf(IllegalArgumentException.class);
    }

    private static Optional<RepublicationAdvice> advice(ListingStage stage, StatisticsReading latest,
                                                        StatisticsReading baseline) {
        return RepublicationAdvice.of(stage, Optional.of(latest), Optional.of(baseline), NOW);
    }

    private static StatisticsReading reading(int views, int favourites, Instant readAt) {
        return new StatisticsReading(new VintedListingId(1), new ListingStatistics(views, favourites), readAt);
    }
}
