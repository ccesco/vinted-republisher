package fr.vinted.republisher.domain.inventory;

/** What the member has to do about a failed listing, from the outcome of its failure. */
public enum FailureKind {
    /** A doubt: checking on Vinted settles it. */
    IN_DOUBT,
    /** A refusal that does not only ask to wait: the same action would be refused again. */
    REFUSED,
    /** A failure or a recheck found not done: relaunch once the problem is solved. */
    OTHER
}
