package fr.vinted.republisher.domain.relaunch;

import fr.vinted.republisher.domain.inventory.ListingStage;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** The listing's stage has no next step to launch; a waiting listing tells when it can be republished. */
public class NotRelaunchable extends RuntimeException {

    private final transient ListingStage stage;
    private final transient Optional<Instant> allowedAt;

    public NotRelaunchable(ListingStage stage, Optional<Instant> allowedAt) {
        super("Listing at stage " + stage + " cannot be relaunched");
        this.stage = Objects.requireNonNull(stage);
        this.allowedAt = Objects.requireNonNull(allowedAt);
    }

    public ListingStage stage() {
        return stage;
    }

    public Optional<Instant> allowedAt() {
        return allowedAt;
    }
}
