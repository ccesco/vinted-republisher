package fr.vinted.republisher.application.session;

import fr.vinted.republisher.domain.session.BrowserAction;
import fr.vinted.republisher.domain.session.VintedAccount;
import fr.vinted.republisher.domain.session.VintedSession;

import java.util.concurrent.Executor;

public class CheckVintedSession {

    private final VintedSession session;
    private final VintedAccount account;
    private final Executor background;

    public CheckVintedSession(VintedSession session, VintedAccount account, Executor background) {
        this.session = session;
        this.account = account;
        this.background = background;
    }

    public void check() {
        BrowserActions.perform(session, BrowserAction.CHECKING, background, account::checkSession);
    }
}
