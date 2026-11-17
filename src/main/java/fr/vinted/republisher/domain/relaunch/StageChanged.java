package fr.vinted.republisher.domain.relaunch;

import fr.vinted.republisher.domain.inventory.ListingStage;

import java.util.Objects;

/** The listing is no longer at the stage the member saw: nothing is launched. */
public class StageChanged extends RuntimeException {

    private final transient ListingStage current;

    public StageChanged(ListingStage seen, ListingStage current) {
        super("Listing seen at stage " + seen + " is now at stage " + current);
        this.current = Objects.requireNonNull(current);
    }

    public ListingStage current() {
        return current;
    }
}
