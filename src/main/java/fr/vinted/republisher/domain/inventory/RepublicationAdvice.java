package fr.vinted.republisher.domain.inventory;

import fr.vinted.republisher.domain.wardrobe.StatisticsReading;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The tool advises republishing a listing on sale that is hardly seen any more: fewer views gained over the
 * observation period than the minimum, and not enough favourites to be worth keeping. It advises, it never acts.
 */
public record RepublicationAdvice(int viewsGained, Instant since) {

    private static final Duration OBSERVATION_PERIOD = Duration.ofDays(7);
    private static final int MINIMUM_VIEWS_GAINED = 10;
    private static final int PROTECTING_FAVOURITE_COUNT = 5;

    private static final Set<ListingStage> ON_SALE =
            Set.of(ListingStage.TO_BACK_UP, ListingStage.TO_VALIDATE, ListingStage.TO_DELETE);

    public RepublicationAdvice {
        if (viewsGained < 0) {
            throw new IllegalArgumentException("Views gained cannot be negative: " + viewsGained);
        }
        Objects.requireNonNull(since);
    }

    /** The date before which the baseline reading of the advice is taken, at the latest. */
    public static Instant baselineAsOf(Instant now) {
        return now.minus(OBSERVATION_PERIOD);
    }

    /**
     * {@code latest} is the latest reading of the listing, {@code baseline} its latest reading made at the latest
     * {@link #baselineAsOf(Instant) one observation period ago}; without a reading made since, nothing is known of
     * how the listing is seen now.
     */
    public static Optional<RepublicationAdvice> of(ListingStage stage, Optional<StatisticsReading> latest,
                                                   Optional<StatisticsReading> baseline, Instant now) {
        if (!ON_SALE.contains(stage) || latest.isEmpty() || baseline.isEmpty()
                || !latest.get().readAt().isAfter(baselineAsOf(now))
                || latest.get().statistics().favouriteCount() >= PROTECTING_FAVOURITE_COUNT) {
            return Optional.empty();
        }
        int viewsGained = Math.max(0,
                latest.get().statistics().viewCount() - baseline.get().statistics().viewCount());
        return viewsGained < MINIMUM_VIEWS_GAINED
                ? Optional.of(new RepublicationAdvice(viewsGained, baseline.get().readAt()))
                : Optional.empty();
    }
}
