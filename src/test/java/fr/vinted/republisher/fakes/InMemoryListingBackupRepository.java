package fr.vinted.republisher.fakes;

import fr.vinted.republisher.domain.backup.BackupSummary;
import fr.vinted.republisher.domain.backup.ListingBackup;
import fr.vinted.republisher.domain.backup.ListingBackupRepository;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public class InMemoryListingBackupRepository implements ListingBackupRepository {

    private final Map<VintedListingId, ListingBackup> backups = new LinkedHashMap<>();
    private boolean databaseDown;
    private Consumer<ListingBackup> onSave = backup -> { };

    public synchronized InMemoryListingBackupRepository whereDatabaseIsDown() {
        databaseDown = true;
        return this;
    }

    /** Runs when a backup is about to be saved: to look at the state of the world at that moment. */
    public synchronized InMemoryListingBackupRepository whenSaving(Consumer<ListingBackup> action) {
        onSave = action;
        return this;
    }

    public synchronized Optional<ListingBackup> find(long listing) {
        return Optional.ofNullable(backups.get(new VintedListingId(listing)));
    }

    /** As the eraser of duplicates does (US-47). */
    public synchronized void remove(VintedListingId listing) {
        backups.remove(listing);
    }

    @Override
    public synchronized List<ListingBackup> findAll() {
        return List.copyOf(backups.values());
    }

    @Override
    public synchronized List<BackupSummary> findAllSummaries() {
        return backups.values().stream().map(ListingBackup::summary).toList();
    }

    @Override
    public synchronized Optional<ListingBackup> find(VintedListingId listing) {
        return Optional.ofNullable(backups.get(listing));
    }

    @Override
    public synchronized void save(ListingBackup backup) {
        if (databaseDown) {
            throw new IllegalStateException("database down");
        }
        onSave.accept(backup);
        backups.put(backup.id(), backup);
    }
}
