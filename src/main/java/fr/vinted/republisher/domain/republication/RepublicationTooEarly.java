package fr.vinted.republisher.domain.republication;

import java.time.Instant;

public class RepublicationTooEarly extends RuntimeException {

    private final Instant republishableAt;

    public RepublicationTooEarly(Instant republishableAt) {
        super("The listing may be republished from " + republishableAt);
        this.republishableAt = republishableAt;
    }

    public Instant republishableAt() {
        return republishableAt;
    }
}
