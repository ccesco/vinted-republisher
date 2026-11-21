package fr.vinted.republisher.adapter.out.persistence;

import fr.vinted.republisher.domain.wardrobe.ArticleId;
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
    private final Articles articles;

    JpaListingRepository(ListingJpaRepository repository, ListingPriceChangeJpaRepository priceChanges,
                         Articles articles) {
        this.repository = repository;
        this.priceChanges = priceChanges;
        this.articles = articles;
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
     * One transaction for the listings, their new articles and their price changes: all or nothing. Articles, then
     * listings, then price changes, for the foreign keys. A listing already known, this batch included, keeps its
     * first-seen date and its article, read back here; a new one brings a new article (US-53). Each listing is
     * compared with the latest price of its article, this batch included.
     */
    @Override
    @Transactional
    public void saveAll(List<Listing> listings) {
        Map<Long, ListingJpaEntity> kept = new HashMap<>(knownOf(listings));
        List<ListingJpaEntity> entities = new ArrayList<>();
        for (Listing listing : listings) {
            ListingJpaEntity known = kept.get(listing.id().value());
            if (known == null) {
                articles.add(listing.articleId(), listing.firstSeenAt());
            }
            ListingJpaEntity entity = known == null
                    ? new ListingJpaEntity(listing, listing.firstSeenAt(), listing.articleId())
                    : new ListingJpaEntity(listing, known.firstSeenAt(), known.articleId());
            kept.put(listing.id().value(), entity);
            entities.add(entity);
        }
        repository.saveAllAndFlush(entities);
        Map<ArticleId, PriceChange> latestPrices = new HashMap<>(latestPricesOf(entities));
        List<ListingPriceChangeJpaEntity> changes = new ArrayList<>();
        for (int index = 0; index < listings.size(); index++) {
            ArticleId article = entities.get(index).articleId();
            PriceChange.of(Optional.ofNullable(latestPrices.get(article)), listings.get(index)).ifPresent(change -> {
                latestPrices.put(article, change);
                changes.add(new ListingPriceChangeJpaEntity(change, article));
            });
        }
        priceChanges.saveAll(changes);
    }

    private Map<Long, ListingJpaEntity> knownOf(List<Listing> listings) {
        return repository.findAllById(listings.stream().map(listing -> listing.id().value()).toList()).stream()
                .collect(Collectors.toMap(ListingJpaEntity::vintedId, Function.identity()));
    }

    private Map<ArticleId, PriceChange> latestPricesOf(List<ListingJpaEntity> listings) {
        if (listings.isEmpty()) {
            return Map.of();
        }
        return priceChanges.findLatestOf(listings.stream().map(listing -> listing.articleId().value()).toList())
                .stream().collect(Collectors.toMap(ListingPriceChangeJpaEntity::articleId,
                        ListingPriceChangeJpaEntity::toPriceChange));
    }

    @Override
    public List<PriceChange> priceHistoryOf(VintedListingId listing) {
        return priceChanges.findHistoryOfArticleOf(listing.value()).stream()
                .map(ListingPriceChangeJpaEntity::toPriceChange).toList();
    }
}
