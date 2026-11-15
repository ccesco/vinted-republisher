package fr.vinted.republisher.domain.journal;

import java.util.List;

/** Entries most recent first, and whether older ones remain. */
public record JournalPage(List<JournalEntry> entries, boolean hasMore) {

    public static final int SIZE = 50;

    public JournalPage {
        entries = List.copyOf(entries);
    }
}
