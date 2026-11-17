package fr.vinted.republisher.domain.run;

import java.time.Instant;
import java.util.function.BooleanSupplier;

/** Lets the automatic run wait: a republication date, the end of a cadence limit, a busy browser, a pause. */
public interface RunWaiting {

    /**
     * Returns at {@code instant}, at once if it is already past, or within a second once {@code interrupted} says so
     * (a pause or a stop asked for by the member).
     */
    void until(Instant instant, BooleanSupplier interrupted);
}
