package fr.vinted.republisher.domain.wardrobe;

/** The wardrobe does not look as expected: Vinted may have changed, so nothing read can be trusted. */
public class WardrobeUnreadable extends RuntimeException {

    public WardrobeUnreadable(String reason) {
        super("The wardrobe cannot be read: " + reason);
    }

    public WardrobeUnreadable(String reason, Throwable cause) {
        super("The wardrobe cannot be read: " + reason, cause);
    }
}
