package fr.vinted.republisher.domain.wardrobe;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record WardrobeOverview(
        boolean scanInProgress,
        Optional<Instant> lastScanAt,
        Optional<ScanProblem> problem,
        List<Listing> listings) {

    public WardrobeOverview {
        Objects.requireNonNull(lastScanAt);
        Objects.requireNonNull(problem);
        listings = List.copyOf(listings);
    }
}
