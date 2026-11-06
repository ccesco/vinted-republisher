package fr.vinted.republisher.domain.wardrobe;

import java.util.Optional;

/** How the last scan ended. Synchronized because scans run in the background while the web page reads it. */
public final class WardrobeScan {

    private Optional<ScanProblem> problem = Optional.empty();

    public synchronized void start() {
        problem = Optional.empty();
    }

    public synchronized void fail(ScanProblem reason) {
        problem = Optional.of(reason);
    }

    public synchronized Optional<ScanProblem> problem() {
        return problem;
    }
}
