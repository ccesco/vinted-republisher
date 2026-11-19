package fr.vinted.republisher.domain.cadence;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * The write cadence with the writes it counts, done or planned: tells from when a write would be allowed, and takes
 * in planned writes to foresee the next ones (US-41).
 */
public record CadenceForecast(WriteCadence cadence, List<Instant> deletions, List<Instant> republications) {

    public CadenceForecast {
        Objects.requireNonNull(cadence);
        deletions = List.copyOf(deletions);
        republications = List.copyOf(republications);
    }

    /** {@code at} if the write is allowed then, otherwise the date from which it is. */
    public Instant allowedAt(VintedWrite write, Instant at) {
        return cadence.allowedAt(write, deletions, republications, at);
    }

    public CadenceForecast withWriteAt(VintedWrite write, Instant at) {
        return write == VintedWrite.DELETION
                ? new CadenceForecast(cadence, plus(deletions, at), republications)
                : new CadenceForecast(cadence, deletions, plus(republications, at));
    }

    private static List<Instant> plus(List<Instant> writes, Instant at) {
        return Stream.concat(writes.stream(), Stream.of(at)).toList();
    }
}
