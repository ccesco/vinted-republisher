package fr.vinted.republisher.adapter.out.persistence;

import fr.vinted.republisher.domain.republication.ListingRepublication;
import fr.vinted.republisher.domain.republication.ListingRepublicationRepository;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
class JpaListingRepublicationRepository implements ListingRepublicationRepository {

    private final ListingRepublicationJpaRepository repository;

    JpaListingRepublicationRepository(ListingRepublicationJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<ListingRepublication> findLatest(VintedListingId listing) {
        return repository.findFirstByListingIdOrderByAttemptedAtDesc(listing.value())
                .map(ListingRepublicationJpaEntity::toListingRepublication);
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
    public List<Instant> findAttemptedSince(Instant since) {
        return repository.findAttemptedAtAfter(since);
    }

    /** Flushed at once: the attempt must be in the database before Publish is clicked. */
    @Override
    public void save(ListingRepublication republication) {
        repository.saveAndFlush(new ListingRepublicationJpaEntity(republication));
    }
}
