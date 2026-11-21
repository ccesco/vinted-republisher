package fr.vinted.republisher.domain.cadence;

/** Who asked for a write on Vinted: only the run's writes are held to the write cadence and counted by it (US-49). */
public enum WriteOrigin {
    /** A click of the member: neither the write cadence nor the republication delay applies. */
    MANUAL,
    /** The run (US-19): held to the write cadence and the republication delay. */
    RUN
}
