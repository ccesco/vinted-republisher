package fr.vinted.republisher.adapter.out.persistence;

import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.ListingRepository;
import fr.vinted.republisher.domain.wardrobe.PriceChange;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Repository
class JpaListingRepository implements ListingRepository {

    private final ListingJpaRepository repository;
    private final ListingPriceChangeJpaRepository priceChanges;

    JpaListingRepository(ListingJpaRepository repository, ListingPriceChangeJpaRepository priceChanges) {
        this.repository = repository;
        this.priceChanges = priceChanges;
    }

    @Override
    public List<Listing> findAll() {
        return repository.findAll().stream().map(ListingJpaEntity::toListing).toList();
    }

    @Override
    public Optional<Listing> find(VintedListingId listing) {
        return repository.findById(listing.value()).map(ListingJpaEntity::toListing);
    }

    /**
     * One transaction for the listings and their price changes: all or nothing. The listings are written first, for
     * the foreign key of their price changes; each listing is compared with the latest price, this batch included.
     */
    @Override
    @Transactional
    public void saveAll(List<Listing> listings) {
        Map<VintedListingId, PriceChange> latestPrices = new HashMap<>(latestPricesOf(listings));
        repository.saveAllAndFlush(listings.stream().map(ListingJpaEntity::new).toList());
        List<ListingPriceChangeJpaEntity> changes = new ArrayList<>();
        for (Listing listing : listings) {
            PriceChange.of(Optional.ofNullable(latestPrices.get(listing.id())), listing).ifPresent(change -> {
                latestPrices.put(change.listingId(), change);
                changes.add(new ListingPriceChangeJpaEntity(change));
            });
        }
        priceChanges.saveAll(changes);
    }

    private Map<VintedListingId, PriceChange> latestPricesOf(List<Listing> listings) {
        if (listings.isEmpty()) {
            return Map.of();
        }
        return priceChanges.findLatestOf(listings.stream().map(listing -> listing.id().value()).toList()).stream()
                .map(ListingPriceChangeJpaEntity::toPriceChange)
                .collect(Collectors.toMap(PriceChange::listingId, Function.identity()));
    }

    @Override
    public List<PriceChange> priceHistoryOf(VintedListingId listing) {
        return priceChanges.findByListingIdOrderByChangedAtDescIdDesc(listing.value()).stream()
                .map(ListingPriceChangeJpaEntity::toPriceChange).toList();
    }
}
