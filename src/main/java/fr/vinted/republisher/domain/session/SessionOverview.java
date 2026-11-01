package fr.vinted.republisher.domain.session;

import java.util.Objects;
import java.util.Optional;

public record SessionOverview(
        Optional<SessionStatus> status,
        Optional<BrowserAction> currentAction,
        Optional<SessionProblem> problem) {

    public SessionOverview {
        Objects.requireNonNull(status);
        Objects.requireNonNull(currentAction);
        Objects.requireNonNull(problem);
    }

    public static SessionOverview idle(SessionStatus status) {
        return new SessionOverview(Optional.of(status), Optional.empty(), Optional.empty());
    }

    public static SessionOverview unchecked() {
        return new SessionOverview(Optional.empty(), Optional.empty(), Optional.empty());
    }
}
