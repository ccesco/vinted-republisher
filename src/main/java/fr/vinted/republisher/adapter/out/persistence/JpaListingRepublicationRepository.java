package fr.vinted.republisher.adapter.out.persistence;

import fr.vinted.republisher.domain.republication.ListingRepublication;
import fr.vinted.republisher.domain.republication.ListingRepublicationRepository;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
class JpaListingRepublicationRepository implements ListingRepublicationRepository {

    private final ListingRepublicationJpaRepository repository;

    JpaListingRepublicationRepository(ListingRepublicationJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<ListingRepublication> find(VintedListingId listing) {
        return repository.findByListingId(listing.value()).map(ListingRepublicationJpaEntity::toListingRepublication);
    }

    /** Flushed at once: the attempt must be in the database before Publish is clicked. */
    @Override
    public void save(ListingRepublication republication) {
        repository.saveAndFlush(new ListingRepublicationJpaEntity(republication));
    }
}
