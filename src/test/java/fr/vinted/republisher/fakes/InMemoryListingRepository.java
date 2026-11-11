package fr.vinted.republisher.fakes;

import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.ListingRepository;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemoryListingRepository implements ListingRepository {

    private final Map<VintedListingId, Listing> listings = new LinkedHashMap<>();
    private boolean databaseDown;

    public InMemoryListingRepository whereDatabaseIsDown() {
        databaseDown = true;
        return this;
    }

    @Override
    public List<Listing> findAll() {
        return List.copyOf(listings.values());
    }

    @Override
    public Optional<Listing> find(VintedListingId listing) {
        return Optional.ofNullable(listings.get(listing));
    }

    @Override
    public void saveAll(List<Listing> changed) {
        if (databaseDown) {
            throw new IllegalStateException("database down");
        }
        changed.forEach(listing -> listings.put(listing.id(), listing));
    }
}
