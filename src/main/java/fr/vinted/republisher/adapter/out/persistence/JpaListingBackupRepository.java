package fr.vinted.republisher.adapter.out.persistence;

import fr.vinted.republisher.domain.backup.ListingBackup;
import fr.vinted.republisher.domain.backup.ListingBackupRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
class JpaListingBackupRepository implements ListingBackupRepository {

    private final ListingBackupJpaRepository repository;

    JpaListingBackupRepository(ListingBackupJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<ListingBackup> findAll() {
        return repository.findAll().stream().map(ListingBackupJpaEntity::toListingBackup).toList();
    }

    /** The listing is the key: saving again replaces its previous backup. */
    @Override
    public void save(ListingBackup backup) {
        repository.save(new ListingBackupJpaEntity(backup));
    }
}
