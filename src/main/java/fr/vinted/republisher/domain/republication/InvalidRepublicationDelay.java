package fr.vinted.republisher.domain.republication;

import java.time.Duration;

public class InvalidRepublicationDelay extends IllegalArgumentException {

    public InvalidRepublicationDelay(Duration delay) {
        super("Invalid republication delay: " + delay + " (must be strictly positive)");
    }
}
