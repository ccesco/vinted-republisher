package fr.vinted.republisher.domain.deletion;

/** The page of the listing is not what the tool expects, such as a missing delete button: nothing was clicked. */
public class ListingPageUnreadable extends RuntimeException {

    public ListingPageUnreadable(String reason) {
        super(reason);
    }
}
