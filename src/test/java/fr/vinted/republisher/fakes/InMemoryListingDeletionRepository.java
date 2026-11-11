package fr.vinted.republisher.fakes;

import fr.vinted.republisher.domain.deletion.ListingDeletion;
import fr.vinted.republisher.domain.deletion.ListingDeletionId;
import fr.vinted.republisher.domain.deletion.ListingDeletionRepository;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemoryListingDeletionRepository implements ListingDeletionRepository {

    private final Map<ListingDeletionId, ListingDeletion> deletions = new LinkedHashMap<>();
    private boolean databaseDown;

    public InMemoryListingDeletionRepository whereDatabaseIsDown() {
        databaseDown = true;
        return this;
    }

    public List<ListingDeletion> findAll() {
        return List.copyOf(deletions.values());
    }

    @Override
    public Optional<ListingDeletion> findLatest(VintedListingId listing) {
        return deletions.values().stream().filter(deletion -> deletion.listingId().equals(listing))
                .max(Comparator.comparing(ListingDeletion::attemptedAt));
    }

    @Override
    public void save(ListingDeletion deletion) {
        if (databaseDown) {
            throw new IllegalStateException("database down");
        }
        deletions.put(deletion.id(), deletion);
    }
}
