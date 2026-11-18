package fr.vinted.republisher.fakes;

import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.ListingRepository;
import fr.vinted.republisher.domain.wardrobe.PriceChange;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemoryListingRepository implements ListingRepository {

    private final Map<VintedListingId, Listing> listings = new LinkedHashMap<>();
    private final List<PriceChange> priceChanges = new ArrayList<>();
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
        changed.forEach(listing -> {
            listings.merge(listing.id(), listing, (known, seen) -> new Listing(seen.id(), seen.url(), seen.title(),
                    seen.price(), seen.present(), known.firstSeenAt(), seen.lastSeenAt()));
            PriceChange.of(priceHistoryOf(listing.id()).stream().findFirst(), listing)
                    .ifPresent(priceChanges::addFirst);
        });
    }

    @Override
    public List<PriceChange> priceHistoryOf(VintedListingId listing) {
        return priceChanges.stream().filter(change -> change.listingId().equals(listing)).toList();
    }
}
