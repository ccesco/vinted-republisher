package fr.vinted.republisher.domain.cadence;

import java.time.Instant;

/** A real write on Vinted refused because it would exceed the write cadence; nothing was touched on Vinted. */
public class CadenceLimitReached extends RuntimeException {

    private final Instant allowedAt;

    public CadenceLimitReached(Instant allowedAt) {
        super("Writing on Vinted is allowed again from " + allowedAt);
        this.allowedAt = allowedAt;
    }

    public Instant allowedAt() {
        return allowedAt;
    }
}
