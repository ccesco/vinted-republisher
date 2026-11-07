package fr.vinted.republisher.domain.backup;

/** A listing form does not look as expected: Vinted may have changed, so its reading cannot be trusted. */
public class ListingUnreadable extends RuntimeException {

    public ListingUnreadable(String reason) {
        super("The listing cannot be read: " + reason);
    }

    public ListingUnreadable(String reason, Throwable cause) {
        super("The listing cannot be read: " + reason, cause);
    }
}
