package fr.vinted.republisher.domain.archive;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/** A complete copy of the tool's data, downloaded by the member: every business table and every kept photo. */
public record DataArchive(Instant createdAt, String fileName) {

    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss");

    public DataArchive {
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(fileName);
    }

    /** Named after the member's local date and time, to the second, so that archives sort by date. */
    public static DataArchive createdAt(ZonedDateTime now) {
        return new DataArchive(now.toInstant(), "vinted-republisher-archive-"
                + now.truncatedTo(ChronoUnit.SECONDS).format(FILE_DATE) + ".zip");
    }
}
