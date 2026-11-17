package fr.vinted.republisher.domain.republication;

/** The member's link is missing, does not lead to a listing, or to one that cannot be the new listing. */
public class NewListingLinkInvalid extends RuntimeException {

    public NewListingLinkInvalid() {
        super("The link given is not the one of a new listing");
    }
}
