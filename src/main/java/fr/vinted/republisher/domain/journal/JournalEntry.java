package fr.vinted.republisher.domain.journal;

import java.util.Objects;

/** A line of the journal, numbered in the order it was written; never changed nor removed. */
public record JournalEntry(long id, ActionRecord record) {

    public JournalEntry {
        Objects.requireNonNull(record);
    }
}
