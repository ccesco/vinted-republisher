package fr.vinted.republisher.domain.cadence;

/** An interval or a daily limit the write cadence cannot hold; in the configuration, the tool does not start. */
public class InvalidWriteCadence extends IllegalArgumentException {

    public InvalidWriteCadence(String reason) {
        super("Invalid write cadence: " + reason);
    }
}
