package fr.vinted.republisher.domain.republication;

/** The listing form is not the one expected: nothing has been published. */
public class ListingFormUnreadable extends RuntimeException {

    public ListingFormUnreadable(String reason) {
        super("The listing form is unreadable: " + reason);
    }
}
