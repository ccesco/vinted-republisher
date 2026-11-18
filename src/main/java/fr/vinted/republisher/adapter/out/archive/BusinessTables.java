package fr.vinted.republisher.adapter.out.archive;

import java.io.OutputStream;
import java.time.Instant;

/** The database part of a data archive: the content of {@code data.json}. */
public interface BusinessTables {

    void writeJson(Instant createdAt, OutputStream json);
}
