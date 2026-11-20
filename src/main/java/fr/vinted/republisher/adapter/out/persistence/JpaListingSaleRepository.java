package fr.vinted.republisher.adapter.out.persistence;

import fr.vinted.republisher.domain.sale.ListingAlreadySold;
import fr.vinted.republisher.domain.sale.ListingSale;
import fr.vinted.republisher.domain.sale.ListingSaleRepository;
import fr.vinted.republisher.domain.sale.SaleOrigin;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
class JpaListingSaleRepository implements ListingSaleRepository {

    private final ListingSaleJpaRepository repository;

    JpaListingSaleRepository(ListingSaleJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<ListingSale> findAll() {
        return repository.findAll().stream().map(ListingSaleJpaEntity::toListingSale).toList();
    }

    @Override
    public Optional<ListingSale> find(VintedListingId listing) {
        return repository.findById(listing.value()).map(ListingSaleJpaEntity::toListingSale);
    }

    @Override
    @Transactional
    public void addAll(List<ListingSale> sales) {
        repository.saveAll(sales.stream().map(ListingSaleJpaEntity::new).toList());
    }

    @Override
    @Transactional
    public void add(ListingSale sale) {
        if (repository.insertIfAbsent(sale.listingId().value(), sale.noticedAt(), sale.origin().name()) == 0) {
            throw new ListingAlreadySold(sale.listingId());
        }
    }

    @Override
    @Transactional
    public void removeNoted(VintedListingId listing) {
        repository.deleteByListingIdAndOrigin(listing.value(), SaleOrigin.NOTED);
    }
}
