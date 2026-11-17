package fr.vinted.republisher.domain.run;

import java.time.Instant;

/** Lets the automatic run wait: a republication date, the end of a cadence limit, a busy browser. */
public interface RunWaiting {

    /** Returns at {@code instant}, or at once if it is already past. */
    void until(Instant instant);
}
