package fr.vinted.republisher.fakes;

import fr.vinted.republisher.domain.wardrobe.WardrobeScan;
import fr.vinted.republisher.domain.wardrobe.WardrobeScanId;
import fr.vinted.republisher.domain.wardrobe.WardrobeScanRepository;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class InMemoryWardrobeScanRepository implements WardrobeScanRepository {

    private final Map<WardrobeScanId, WardrobeScan> scans = new LinkedHashMap<>();

    @Override
    public Optional<WardrobeScan> findUnfinished() {
        return scans.values().stream().filter(scan -> scan.finishedAt().isEmpty()).findFirst();
    }

    @Override
    public Optional<WardrobeScan> findLastFinished() {
        return scans.values().stream().filter(scan -> scan.finishedAt().isPresent())
                .max(Comparator.comparing((WardrobeScan scan) -> scan.finishedAt().orElse(Instant.MIN)));
    }

    @Override
    public void save(WardrobeScan scan) {
        scans.put(scan.id(), scan);
    }
}
