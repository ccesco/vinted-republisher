package fr.vinted.republisher.domain.session;

import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static fr.vinted.republisher.domain.session.BrowserAction.LOGGING_IN;
import static fr.vinted.republisher.domain.session.SessionProblem.MANUAL_LOGIN_NOT_COMPLETED;
import static fr.vinted.republisher.domain.session.SessionStatus.DISCONNECTED;

/**
 * The tool's connection to the member's Vinted account, and the browser lock. Synchronized because browser actions
 * run in the background while the web page reads the overview.
 */
public final class VintedSession {

    private final int parallelListingActions;
    private final List<ActionInProgress> inProgress = new ArrayList<>();
    private Optional<SessionStatus> status = Optional.empty();
    private Optional<SessionProblem> problem = Optional.empty();

    /** An action of the browser, on a listing or on the browser alone. */
    private record ActionInProgress(BrowserAction action, Optional<VintedListingId> listing) {
    }

    /** One browser action at a time. */
    public VintedSession() {
        this(1);
    }

    /** Up to {@code parallelListingActions} actions on listings at once (US-15), each in its own tab. */
    public VintedSession(int parallelListingActions) {
        if (parallelListingActions < 1) {
            throw new IllegalArgumentException("at least one listing action at a time: " + parallelListingActions);
        }
        this.parallelListingActions = parallelListingActions;
    }

    /** An action that needs the browser alone, to keep Vinted visits few and sequential: refused during any other. */
    public synchronized boolean start(BrowserAction action) {
        if (!inProgress.isEmpty()) {
            return false;
        }
        inProgress.add(new ActionInProgress(action, Optional.empty()));
        if (action.isAboutTheSession()) {
            problem = Optional.empty();
        }
        return true;
    }

    /**
     * An action on a listing, alongside others on other listings up to the parallelism: refused during an action that
     * needs the browser alone, when every place is taken, or when the listing already has an action in progress.
     */
    public synchronized boolean start(BrowserAction action, VintedListingId listing) {
        if (!action.isOnAListing()) {
            throw new IllegalArgumentException(action + " needs the browser alone");
        }
        boolean shared = inProgress.stream().allMatch(other -> other.listing().isPresent());
        boolean listingFree = inProgress.stream().noneMatch(other -> other.listing().equals(Optional.of(listing)));
        if (!shared || !listingFree || inProgress.size() >= parallelListingActions) {
            return false;
        }
        inProgress.add(new ActionInProgress(action, Optional.of(listing)));
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

    /** Ends the action on this listing. */
    public synchronized void finish(VintedListingId listing) {
        ActionInProgress action = inProgress.stream()
                .filter(candidate -> candidate.listing().equals(Optional.of(listing)))
                .findFirst()
                .orElseThrow(NoBrowserActionInProgress::new);
        inProgress.remove(action);
    }

    /** Shows the oldest action in progress. */
    public synchronized SessionOverview overview() {
        return new SessionOverview(status, inProgress.stream().findFirst().map(ActionInProgress::action), problem);
    }

    private BrowserAction endCurrentAction() {
        // An action on a listing ends by its listing, never in place of an action that needs the browser alone.
        if (inProgress.isEmpty() || inProgress.getFirst().listing().isPresent()) {
            throw new NoBrowserActionInProgress();
        }
        return inProgress.removeFirst().action();
    }
}
