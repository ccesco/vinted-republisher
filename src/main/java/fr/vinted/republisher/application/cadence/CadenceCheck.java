package fr.vinted.republisher.application.cadence;

import fr.vinted.republisher.domain.cadence.CadenceLimitReached;
import fr.vinted.republisher.domain.cadence.VintedWrite;
import fr.vinted.republisher.domain.cadence.WriteCadence;
import fr.vinted.republisher.domain.deletion.ListingDeletionRepository;
import fr.vinted.republisher.domain.republication.ListingRepublicationRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Checks the write cadence against the attempts recorded in the database, for the whole account. Several tabs may
 * write at once (US-15): a write that passes holds the cadence as if made at the time of the check until its action
 * releases it, so that two writes never pass together. Reservations are kept in memory, attempts in the database.
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

    private Instant allowedAt(VintedWrite write, Instant now) {
        Instant since = cadence.windowStart(now);
        return cadence.allowedAt(write, withReserved(VintedWrite.DELETION, deletions.findAttemptedSince(since)),
                withReserved(VintedWrite.REPUBLICATION, republications.findAttemptedSince(since)), now);
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
