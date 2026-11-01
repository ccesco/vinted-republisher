package fr.vinted.republisher.domain.session;

public class BrowserUnavailable extends RuntimeException {

    public BrowserUnavailable(String reason) {
        super("The browser is unavailable: " + reason);
    }

    public BrowserUnavailable(String reason, Throwable cause) {
        super("The browser is unavailable: " + reason, cause);
    }
}
