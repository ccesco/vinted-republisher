package fr.vinted.republisher.adapter.out.persistence;

import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.ListingRepository;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
class JpaListingRepository implements ListingRepository {

    private final ListingJpaRepository repository;

    JpaListingRepository(ListingJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<Listing> findAll() {
        return repository.findAll().stream().map(ListingJpaEntity::toListing).toList();
    }

    @Override
    public Optional<Listing> find(VintedListingId listing) {
        return repository.findById(listing.value()).map(ListingJpaEntity::toListing);
    }

    /** Spring Data runs saveAll in one transaction. */
    @Override
    public void saveAll(List<Listing> listings) {
        repository.saveAll(listings.stream().map(ListingJpaEntity::new).toList());
    }
}
