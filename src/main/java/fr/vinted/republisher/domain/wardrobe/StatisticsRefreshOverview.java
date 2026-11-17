package fr.vinted.republisher.domain.wardrobe;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Where the refresh of the statistics stands, and when the views and favourites were last read, by a scan or a refresh. */
public record StatisticsRefreshOverview(boolean inProgress, Optional<Instant> lastReadAt,
                                        Optional<StatisticsRefreshProblem> problem) {

    public StatisticsRefreshOverview {
        Objects.requireNonNull(lastReadAt);
        Objects.requireNonNull(problem);
    }
}
