package fr.vinted.republisher.adapter.out.persistence;

import fr.vinted.republisher.domain.deletion.ListingDeletion;
import fr.vinted.republisher.domain.deletion.ListingDeletionRepository;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
class JpaListingDeletionRepository implements ListingDeletionRepository {

    private final ListingDeletionJpaRepository repository;

    JpaListingDeletionRepository(ListingDeletionJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<ListingDeletion> findLatest(VintedListingId listing) {
        return repository.findFirstByListingIdOrderByAttemptedAtDesc(listing.value())
                .map(ListingDeletionJpaEntity::toListingDeletion);
    }

    /** The attempt is the key: settling it updates the same row. */
    @Override
    public void save(ListingDeletion deletion) {
        repository.saveAndFlush(new ListingDeletionJpaEntity(deletion));
    }
}
