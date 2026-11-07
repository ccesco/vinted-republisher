package fr.vinted.republisher.domain.session;

/** Vinted sent the tool to its login page: the member is not logged in any more. */
public class SessionDisconnected extends RuntimeException {

    public SessionDisconnected(String reason) {
        super("The Vinted session is disconnected: " + reason);
    }
}
