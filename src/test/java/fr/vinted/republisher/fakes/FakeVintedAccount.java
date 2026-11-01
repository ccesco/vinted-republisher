package fr.vinted.republisher.fakes;

import fr.vinted.republisher.domain.session.BrowserUnavailable;
import fr.vinted.republisher.domain.session.SessionStatus;
import fr.vinted.republisher.domain.session.VintedAccount;
import fr.vinted.republisher.domain.session.VintedUnreachable;

import java.time.Duration;

public class FakeVintedAccount implements VintedAccount {

    private SessionStatus vintedSide = SessionStatus.DISCONNECTED;
    private SessionStatus manualLoginResult = SessionStatus.CONNECTED;
    private boolean browserAvailable = true;
    private boolean vintedReachable = true;
    private boolean failsUnexpectedly;
    private Runnable duringManualLogin = () -> { };
    private int openedBrowsers;
    private Duration manualLoginTimeout;

    public static FakeVintedAccount withMemberConnected() {
        FakeVintedAccount account = new FakeVintedAccount();
        account.vintedSide = SessionStatus.CONNECTED;
        return account;
    }

    public static FakeVintedAccount withMemberDisconnected() {
        return new FakeVintedAccount();
    }

    public FakeVintedAccount whereMemberAbandonsManualLogin() {
        manualLoginResult = SessionStatus.DISCONNECTED;
        return this;
    }

    public FakeVintedAccount whereBrowserCannotStart() {
        browserAvailable = false;
        return this;
    }

    public FakeVintedAccount whereVintedIsUnreachable() {
        vintedReachable = false;
        return this;
    }

    public FakeVintedAccount whereBrowserFailsUnexpectedly() {
        failsUnexpectedly = true;
        return this;
    }

    public FakeVintedAccount duringManualLogin(Runnable action) {
        duringManualLogin = action;
        return this;
    }

    public Duration manualLoginTimeout() {
        return manualLoginTimeout;
    }

    public int openedBrowsers() {
        return openedBrowsers;
    }

    @Override
    public SessionStatus checkSession() {
        openBrowser();
        return vintedSide;
    }

    @Override
    public SessionStatus awaitManualLogin(Duration timeout) {
        openBrowser();
        manualLoginTimeout = timeout;
        duringManualLogin.run();
        vintedSide = manualLoginResult;
        return vintedSide;
    }

    private void openBrowser() {
        if (!browserAvailable) {
            throw new BrowserUnavailable("Chrome not found");
        }
        openedBrowsers++;
        if (failsUnexpectedly) {
            throw new IllegalStateException("unexpected browser failure");
        }
        if (!vintedReachable) {
            throw new VintedUnreachable("HTTP 403");
        }
    }
}
