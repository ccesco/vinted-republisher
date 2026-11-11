package fr.vinted.republisher.domain.session;

public enum BrowserAction {
    CHECKING(true),
    LOGGING_IN(true),
    SCANNING(false),
    BACKING_UP(false),
    DELETING(false);

    private final boolean aboutTheSession;

    BrowserAction(boolean aboutTheSession) {
        this.aboutTheSession = aboutTheSession;
    }

    /** Only an action meant to find out the session's state replaces its previous problem. */
    boolean isAboutTheSession() {
        return aboutTheSession;
    }
}
