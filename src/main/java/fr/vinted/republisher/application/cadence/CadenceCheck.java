package fr.vinted.republisher.application.cadence;

import fr.vinted.republisher.domain.cadence.CadenceForecast;
import fr.vinted.republisher.domain.cadence.CadenceLimitReached;
import fr.vinted.republisher.domain.cadence.VintedWrite;
import fr.vinted.republisher.domain.cadence.WriteCadence;
import fr.vinted.republisher.domain.cadence.WriteOrigin;
import fr.vinted.republisher.domain.deletion.ListingDeletionRepository;
import fr.vinted.republisher.domain.republication.ListingRepublicationRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Checks the write cadence of the run against its attempts recorded in the database, for the whole account: the
 * member's clicks are neither held to it nor counted by it (US-49). Several tabs may write at once (US-15): a write
 * that passes holds the cadence as if made at the time of the check until its action releases it, so that two writes
 * never pass together. Reservations are kept in memory, attempts in the database.
 */
public class CadenceCheck {

    private final ListingDeletionRepository deletions;
    private final ListingRepublicationRepository republications;
    private final WriteCadence cadence;
    private final Clock clock;
    private final List<WriteReservation> reservations = new ArrayList<>();

    public CadenceCheck(ListingDeletionRepository deletions, ListingRepublicationRepository republications,
                        WriteCadence cadence, Clock clock) {
        this.deletions = deletions;
        this.republications = republications;
        this.cadence = cadence;
        this.clock = clock;
    }

    /** Throws {@code CadenceLimitReached} when the write is not allowed now; otherwise holds the cadence for it. */
    public synchronized WriteReservation reserve(VintedWrite write) {
        Instant now = clock.instant();
        Instant allowedAt = allowedAt(write, now);
        if (now.isBefore(allowedAt)) {
            throw new CadenceLimitReached(allowedAt);
        }
        WriteReservation reservation = new WriteReservation(write, now);
        reservations.add(reservation);
        return reservation;
    }

    /** From when the write would pass {@link #reserve}, counting the writes in progress; holds nothing. */
    public synchronized Instant allowedAt(VintedWrite write) {
        return allowedAt(write, clock.instant());
    }

    /** The cadence with the writes it counts now, those in progress included, to foresee the next writes. */
    public synchronized CadenceForecast forecast() {
        return forecast(clock.instant());
    }

    private Instant allowedAt(VintedWrite write, Instant now) {
        return forecast(now).allowedAt(write, now);
    }

    private CadenceForecast forecast(Instant now) {
        Instant since = cadence.windowStart(now);
        return new CadenceForecast(cadence, withReserved(VintedWrite.DELETION,
                deletions.findAttemptedSince(WriteOrigin.RUN, since)),
                withReserved(VintedWrite.REPUBLICATION, republications.findAttemptedSince(WriteOrigin.RUN, since)));
    }

    /** Once its action is over, a write only counts by its attempt, if recorded. */
    public synchronized void release(WriteReservation reservation) {
        reservations.remove(reservation);
    }

    private List<Instant> withReserved(VintedWrite write, List<Instant> attempts) {
        return Stream.concat(attempts.stream(), reservations.stream()
                .filter(reservation -> reservation.write() == write)
                .map(WriteReservation::at)).toList();
    }
}
