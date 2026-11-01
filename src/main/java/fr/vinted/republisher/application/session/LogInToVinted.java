package fr.vinted.republisher.application.session;

import fr.vinted.republisher.domain.session.BrowserAction;
import fr.vinted.republisher.domain.session.VintedAccount;
import fr.vinted.republisher.domain.session.VintedSession;

import java.time.Duration;
import java.util.concurrent.Executor;

public class LogInToVinted {

    private static final Duration MANUAL_LOGIN_TIMEOUT = Duration.ofMinutes(5);

    private final VintedSession session;
    private final VintedAccount account;
    private final Executor background;

    public LogInToVinted(VintedSession session, VintedAccount account, Executor background) {
        this.session = session;
        this.account = account;
        this.background = background;
    }

    public void logIn() {
        BrowserActions.perform(session, BrowserAction.LOGGING_IN, background,
                () -> account.awaitManualLogin(MANUAL_LOGIN_TIMEOUT));
    }
}
