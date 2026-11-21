package fr.vinted.republisher.adapter.out.persistence;

import fr.vinted.republisher.domain.cadence.WriteOrigin;
import fr.vinted.republisher.domain.republication.ListingRepublication;
import fr.vinted.republisher.domain.republication.ListingRepublicationRepository;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
class JpaListingRepublicationRepository implements ListingRepublicationRepository {

    private final ListingRepublicationJpaRepository repository;
    private final Articles articles;

    JpaListingRepublicationRepository(ListingRepublicationJpaRepository repository, Articles articles) {
        this.repository = repository;
        this.articles = articles;
    }

    @Override
    public Optional<ListingRepublication> findLatest(VintedListingId listing) {
        return repository.findFirstByListingIdOrderByAttemptedAtDesc(listing.value())
                .map(ListingRepublicationJpaEntity::toListingRepublication);
    }

    @Override
    public Map<VintedListingId, Integer> findNotPublishedCounts() {
        return repository.countNotPublishedPerListing().stream().collect(Collectors.toUnmodifiableMap(
                count -> new VintedListingId(count.listingId()), count -> Math.toIntExact(count.attempts())));
    }

    @Override
    public Set<VintedListingId> findNewListings() {
        return repository.findNewListingIds().stream().map(VintedListingId::new)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public List<ListingRepublication> findAllLatest() {
        return repository.findAllLatest().stream().map(ListingRepublicationJpaEntity::toListingRepublication).toList();
    }

    @Override
    public List<Instant> findAttemptedSince(WriteOrigin origin, Instant since) {
        return repository.findAttemptedAtAfter(origin, since);
    }

    /**
     * Flushed at once: the attempt must be in the database before Publish is clicked. Published, it first merges the
     * article of the new listing into the one of the listing (US-53), in the same transaction.
     */
    @Override
    @Transactional
    public void save(ListingRepublication republication) {
        republication.newListingId().ifPresent(newListing -> articles.merge(republication.listingId(), newListing));
        repository.saveAndFlush(new ListingRepublicationJpaEntity(republication,
                articles.of(republication.listingId()).orElseThrow(() -> new DataIntegrityViolationException(
                        "Unknown listing " + republication.listingId().value()))));
    }

    @Override
    public Optional<ListingRepublication> findPublishedAs(VintedListingId newListing) {
        return repository.findByNewListingId(newListing.value()).map(ListingRepublicationJpaEntity::toListingRepublication);
    }
}
