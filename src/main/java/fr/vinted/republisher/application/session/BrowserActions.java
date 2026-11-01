package fr.vinted.republisher.application.session;

import fr.vinted.republisher.domain.session.BrowserAction;
import fr.vinted.republisher.domain.session.BrowserUnavailable;
import fr.vinted.republisher.domain.session.SessionProblem;
import fr.vinted.republisher.domain.session.SessionStatus;
import fr.vinted.republisher.domain.session.VintedSession;
import fr.vinted.republisher.domain.session.VintedUnreachable;

import java.util.concurrent.Executor;
import java.util.function.Supplier;

/** Runs one browser action on the Vinted session and records its outcome, failures included. */
final class BrowserActions {

    private BrowserActions() {
    }

    /** Starts the action right away, so that it shows as in progress, then visits Vinted in the background. */
    static void perform(VintedSession session, BrowserAction action, Executor background,
                        Supplier<SessionStatus> onVinted) {
        if (!session.start(action)) {
            return;
        }
        background.execute(() -> complete(session, onVinted));
    }

    private static void complete(VintedSession session, Supplier<SessionStatus> onVinted) {
        try {
            session.complete(onVinted.get());
        } catch (BrowserUnavailable unavailable) {
            session.fail(SessionProblem.BROWSER_UNAVAILABLE);
        } catch (VintedUnreachable unreachable) {
            session.fail(SessionProblem.VINTED_UNREACHABLE);
        } catch (RuntimeException unexpected) {
            // The action must end whatever happens; the failure is rethrown for the executor to log it.
            session.fail(SessionProblem.BROWSER_UNAVAILABLE);
            throw unexpected;
        }
    }
}
