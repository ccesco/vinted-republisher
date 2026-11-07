package fr.vinted.republisher.domain.wardrobe;

import java.util.Objects;
import java.util.UUID;

public record WardrobeScanId(UUID value) {

    public WardrobeScanId {
        Objects.requireNonNull(value);
    }
}
