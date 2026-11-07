package fr.vinted.republisher.domain.session;

import java.util.Optional;

import static fr.vinted.republisher.domain.session.BrowserAction.LOGGING_IN;
import static fr.vinted.republisher.domain.session.SessionProblem.MANUAL_LOGIN_NOT_COMPLETED;
import static fr.vinted.republisher.domain.session.SessionStatus.DISCONNECTED;

/**
 * The tool's connection to the member's Vinted account. Synchronized because browser actions
 * run in the background while the web page reads the overview.
 */
public final class VintedSession {

    private Optional<SessionStatus> status = Optional.empty();
    private Optional<BrowserAction> currentAction = Optional.empty();
    private Optional<SessionProblem> problem = Optional.empty();

    /** One browser action at a time, to keep Vinted visits few and sequential: a second one is refused. */
    public synchronized boolean start(BrowserAction action) {
        if (currentAction.isPresent()) {
            return false;
        }
        currentAction = Optional.of(action);
        if (action.isAboutTheSession()) {
            problem = Optional.empty();
        }
        return true;
    }

    public synchronized void complete(SessionStatus result) {
        BrowserAction action = endCurrentAction();
        if (action == LOGGING_IN && result == DISCONNECTED) {
            problem = Optional.of(MANUAL_LOGIN_NOT_COMPLETED);
        }
        status = Optional.of(result);
    }

    public synchronized void fail(SessionProblem reason) {
        endCurrentAction();
        status = Optional.of(DISCONNECTED);
        problem = Optional.of(reason);
    }

    /** Ends an action that says nothing about the session, such as a scan of the public wardrobe. */
    public synchronized void finish() {
        endCurrentAction();
    }

    public synchronized SessionOverview overview() {
        return new SessionOverview(status, currentAction, problem);
    }

    private BrowserAction endCurrentAction() {
        BrowserAction action = currentAction.orElseThrow(NoBrowserActionInProgress::new);
        currentAction = Optional.empty();
        return action;
    }
}
