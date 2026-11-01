package fr.vinted.republisher.application.session;

import fr.vinted.republisher.domain.session.SessionOverview;
import fr.vinted.republisher.domain.session.VintedSession;

public class ShowVintedSession {

    private final VintedSession session;

    public ShowVintedSession(VintedSession session) {
        this.session = session;
    }

    public SessionOverview show() {
        return session.overview();
    }
}
