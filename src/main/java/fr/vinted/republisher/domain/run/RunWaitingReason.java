package fr.vinted.republisher.domain.run;

/** Why a worker waits: the cadence of its kind of write, a republication date, or a browser taken by others. */
public enum RunWaitingReason {
    CADENCE,
    REPUBLICATION_DELAY,
    BROWSER_BUSY
}
