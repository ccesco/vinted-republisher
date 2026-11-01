package fr.vinted.republisher.domain.session;

/** Vinted does not answer, or answers with an HTTP error such as its anti-bot block page. */
public class VintedUnreachable extends RuntimeException {

    public VintedUnreachable(String reason) {
        super("Vinted is unreachable: " + reason);
    }

    public VintedUnreachable(String reason, Throwable cause) {
        super("Vinted is unreachable: " + reason, cause);
    }
}
