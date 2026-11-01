package fr.vinted.republisher.domain.session;

import java.time.Duration;

/**
 * The member's Vinted account, reached through a browser that keeps the session between launches.
 * Both methods throw {@link BrowserUnavailable} when the browser cannot be reached.
 */
public interface VintedAccount {

    SessionStatus checkSession();

    /** Opens a visible tab and waits for the member to log in by hand. */
    SessionStatus awaitManualLogin(Duration timeout);
}
