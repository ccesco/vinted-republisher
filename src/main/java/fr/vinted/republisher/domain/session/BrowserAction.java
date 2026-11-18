package fr.vinted.republisher.domain.session;

public enum BrowserAction {
    CHECKING(true, false),
    LOGGING_IN(true, false),
    SCANNING(false, false),
    BACKING_UP(false, false),
    VERIFYING_BACKUPS(false, false),
    DELETING(false, true),
    REPUBLISHING(false, true),
    REFRESHING_STATISTICS(false, false),
    ARCHIVING(false, false);

    private final boolean aboutTheSession;
    private final boolean onAListing;

    BrowserAction(boolean aboutTheSession, boolean onAListing) {
        this.aboutTheSession = aboutTheSession;
        this.onAListing = onAListing;
    }

    /** Only an action meant to find out the session's state replaces its previous problem. */
    boolean isAboutTheSession() {
        return aboutTheSession;
    }

    /** Only an action on a listing may share the browser with others (US-15). */
    boolean isOnAListing() {
        return onAListing;
    }
}
