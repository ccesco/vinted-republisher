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
    private int savesBeforeTheDatabaseGoesDown = Integer.MAX_VALUE;

    public InMemoryListingDeletionRepository whereDatabaseIsDown() {
        return whereDatabaseGoesDownAfter(0);
    }

    public InMemoryListingDeletionRepository whereDatabaseGoesDownAfter(int saves) {
        savesBeforeTheDatabaseGoesDown = saves;
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
        if (savesBeforeTheDatabaseGoesDown == 0) {
            throw new IllegalStateException("database down");
        }
        savesBeforeTheDatabaseGoesDown--;
        deletions.put(deletion.id(), deletion);
    }
}
