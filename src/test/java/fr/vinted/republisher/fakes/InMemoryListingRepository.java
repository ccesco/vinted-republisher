package fr.vinted.republisher.fakes;

import fr.vinted.republisher.domain.wardrobe.ArticleId;
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

    /** As the eraser of duplicates does (US-47). */
    public synchronized void remove(VintedListingId listing) {
        listings.remove(listing);
        priceChanges.removeIf(change -> change.listingId().equals(listing));
    }

    @Override
    public synchronized List<Listing> findAll() {
        return List.copyOf(listings.values());
    }

    @Override
    public synchronized Optional<Listing> find(VintedListingId listing) {
        return Optional.ofNullable(listings.get(listing));
    }

    @Override
    public synchronized void saveAll(List<Listing> changed) {
        if (databaseDown) {
            throw new IllegalStateException("database down");
        }
        changed.forEach(listing -> {
            listings.merge(listing.id(), listing, (known, seen) -> new Listing(seen.id(), known.articleId(),
                    seen.url(), seen.title(), seen.price(), seen.present(), known.firstSeenAt(), seen.lastSeenAt()));
            PriceChange.of(priceHistoryOf(listing.id()).stream().findFirst(), listing)
                    .ifPresent(priceChanges::addFirst);
        });
    }

    /** The history of the listing's article (US-53), as the store keeps it. */
    @Override
    public synchronized List<PriceChange> priceHistoryOf(VintedListingId listing) {
        if (!listings.containsKey(listing)) {
            return List.of();
        }
        ArticleId article = listings.get(listing).articleId();
        return priceChanges.stream()
                .filter(change -> listings.get(change.listingId()).articleId().equals(article)).toList();
    }
}
