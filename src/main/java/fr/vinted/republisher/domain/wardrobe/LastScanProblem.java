package fr.vinted.republisher.domain.wardrobe;

import java.util.Optional;

/** What went wrong with the last scan, if anything: shown until the next scan, not kept across restarts. Synchronized because a scan runs in the background while the web page reads it. */
public final class LastScanProblem {

    private Optional<ScanProblem> problem = Optional.empty();

    public synchronized void clear() {
        problem = Optional.empty();
    }

    public synchronized void report(ScanProblem reason) {
        problem = Optional.of(reason);
    }

    public synchronized Optional<ScanProblem> current() {
        return problem;
    }
}
