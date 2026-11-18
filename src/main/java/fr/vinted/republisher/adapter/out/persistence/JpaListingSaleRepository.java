package fr.vinted.republisher.adapter.out.persistence;

import fr.vinted.republisher.domain.sale.ListingSale;
import fr.vinted.republisher.domain.sale.ListingSaleRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
    @Transactional
    public void addAll(List<ListingSale> sales) {
        repository.saveAll(sales.stream().map(ListingSaleJpaEntity::new).toList());
    }
}
