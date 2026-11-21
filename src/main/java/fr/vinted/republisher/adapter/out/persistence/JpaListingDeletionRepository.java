package fr.vinted.republisher.adapter.out.persistence;

import fr.vinted.republisher.domain.cadence.WriteOrigin;
import fr.vinted.republisher.domain.deletion.ListingDeletion;
import fr.vinted.republisher.domain.deletion.ListingDeletionRepository;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
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

    @Override
    public List<ListingDeletion> findAllLatest() {
        return repository.findAllLatest().stream().map(ListingDeletionJpaEntity::toListingDeletion).toList();
    }

    @Override
    public List<Instant> findAttemptedSince(WriteOrigin origin, Instant since) {
        return repository.findAttemptedAtAfter(origin, since);
    }

    /** The attempt is the key: settling it updates the same row. */
    @Override
    public void save(ListingDeletion deletion) {
        repository.saveAndFlush(new ListingDeletionJpaEntity(deletion));
    }
}
