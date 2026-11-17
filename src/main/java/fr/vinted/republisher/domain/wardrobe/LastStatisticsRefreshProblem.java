package fr.vinted.republisher.domain.wardrobe;

import java.util.Optional;

/**
 * What went wrong with the last refresh of the statistics, if anything: shown until the next refresh, not kept across
 * restarts. Synchronized because a refresh runs in the background while the web page reads it.
 */
public final class LastStatisticsRefreshProblem {

    private Optional<StatisticsRefreshProblem> problem = Optional.empty();

    public synchronized void clear() {
        problem = Optional.empty();
    }

    public synchronized void report(StatisticsRefreshProblem reason) {
        problem = Optional.of(reason);
    }

    public synchronized Optional<StatisticsRefreshProblem> current() {
        return problem;
    }
}
