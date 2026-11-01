package fr.vinted.republisher.domain.session;

public class NoBrowserActionInProgress extends IllegalStateException {

    public NoBrowserActionInProgress() {
        super("No browser action is in progress on the Vinted session");
    }
}
