package fr.vinted.republisher.fakes;

import fr.vinted.republisher.domain.journal.ActionJournal;
import fr.vinted.republisher.domain.journal.ActionRecord;
import fr.vinted.republisher.domain.journal.JournalEntry;
import fr.vinted.republisher.domain.journal.JournalPage;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

public class InMemoryActionJournal implements ActionJournal {

    private final List<JournalEntry> entries = new ArrayList<>();
    private boolean databaseDown;

    public InMemoryActionJournal whereDatabaseIsDown() {
        databaseDown = true;
        return this;
    }

    /** What was written, oldest first. */
    public synchronized List<ActionRecord> records() {
        return entries.stream().map(JournalEntry::record).toList();
    }

    @Override
    public synchronized void write(ActionRecord record) {
        if (databaseDown) {
            throw new IllegalStateException("database down");
        }
        entries.add(new JournalEntry(entries.size() + 1L, record));
    }

    @Override
    public synchronized JournalPage page(OptionalLong before) {
        List<JournalEntry> older = entries.reversed().stream()
                .filter(entry -> before.isEmpty() || entry.id() < before.getAsLong())
                .toList();
        return new JournalPage(older.stream().limit(JournalPage.SIZE).toList(), older.size() > JournalPage.SIZE);
    }

    @Override
    public synchronized List<JournalEntry> lastOfEachListing() {
        Map<VintedListingId, JournalEntry> last = new HashMap<>();
        entries.stream().filter(entry -> !entry.record().isWaitingRefusal())
                .forEach(entry -> entry.record().listingId().ifPresent(listing -> last.put(listing, entry)));
        return List.copyOf(last.values());
    }

    @Override
    public synchronized List<JournalEntry> of(VintedListingId listing) {
        return entries.reversed().stream()
                .filter(entry -> entry.record().listingId().filter(listing::equals).isPresent())
                .toList();
    }
}
