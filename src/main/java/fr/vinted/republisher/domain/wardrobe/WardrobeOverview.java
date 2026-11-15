package fr.vinted.republisher.domain.wardrobe;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Where the scan stands; the listings themselves are in the inventory. */
public record WardrobeOverview(
        boolean scanInProgress,
        Optional<Instant> lastScanAt,
        Optional<ScanProblem> problem) {

    public WardrobeOverview {
        Objects.requireNonNull(lastScanAt);
        Objects.requireNonNull(problem);
    }
}
