package fr.vinted.republisher.domain.wardrobe;

import java.util.Optional;

public interface WardrobeScanRepository {

    Optional<WardrobeScan> findUnfinished();

    Optional<WardrobeScan> findLastFinished();

    void save(WardrobeScan scan);
}
