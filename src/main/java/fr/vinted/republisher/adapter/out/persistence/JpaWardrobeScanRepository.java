package fr.vinted.republisher.adapter.out.persistence;

import fr.vinted.republisher.domain.wardrobe.WardrobeScan;
import fr.vinted.republisher.domain.wardrobe.WardrobeScanRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
class JpaWardrobeScanRepository implements WardrobeScanRepository {

    private final WardrobeScanJpaRepository repository;

    JpaWardrobeScanRepository(WardrobeScanJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<WardrobeScan> findUnfinished() {
        return repository.findByFinishedAtIsNull().map(WardrobeScanJpaEntity::toWardrobeScan);
    }

    @Override
    public Optional<WardrobeScan> findLastFinished() {
        return repository.findFirstByFinishedAtIsNotNullOrderByFinishedAtDesc().map(WardrobeScanJpaEntity::toWardrobeScan);
    }

    @Override
    public void save(WardrobeScan scan) {
        repository.save(new WardrobeScanJpaEntity(scan));
    }
}
