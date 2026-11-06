package fr.vinted.republisher.domain.wardrobe;

public class InvalidListing extends IllegalArgumentException {

    public InvalidListing(String reason) {
        super("Invalid listing: " + reason);
    }
}
