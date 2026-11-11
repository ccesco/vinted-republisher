package fr.vinted.republisher.domain.session;

/** Another browser action is in progress: one at a time. */
public class BrowserBusy extends RuntimeException {

    public BrowserBusy() {
        super("Another browser action is in progress");
    }
}
