package fr.vinted.republisher.domain.archive;

import java.io.OutputStream;
import java.time.Instant;

/** Writes a data archive: every business table and every kept photo, never the browser profile. Changes nothing. */
public interface DataArchiveWriter {

    /** Fails with an unchecked exception when the archive cannot be written whole. */
    void write(Instant createdAt, OutputStream archive);
}
