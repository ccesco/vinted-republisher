package fr.vinted.republisher.adapter.out.persistence;

import fr.vinted.republisher.domain.tag.ListingTag;
import fr.vinted.republisher.domain.tag.ListingTagRepository;
import fr.vinted.republisher.domain.tag.Tag;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
class JpaListingTagRepository implements ListingTagRepository {

    private final ListingTagJpaRepository repository;

    JpaListingTagRepository(ListingTagJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<ListingTag> findAll() {
        return repository.findAll().stream().map(ListingTagJpaEntity::toListingTag).toList();
    }

    /** One transaction: an unknown listing (foreign key) rolls back every row added before it. */
    @Override
    @Transactional
    public void add(Tag tag, List<VintedListingId> listings) {
        listings.forEach(listing -> repository.addIfAbsent(listing.value(), tag.name()));
    }

    @Override
    @Transactional
    public void remove(Tag tag, List<VintedListingId> listings) {
        repository.removeFrom(tag.name(), listings.stream().map(VintedListingId::value).toList());
    }
}
