package fr.vinted.republisher.adapter.out.persistence;

import fr.vinted.republisher.domain.backup.BackupValidation;
import fr.vinted.republisher.domain.backup.BackupValidationRepository;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
class JpaBackupValidationRepository implements BackupValidationRepository {

    private final BackupValidationJpaRepository repository;

    JpaBackupValidationRepository(BackupValidationJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<BackupValidation> find(VintedListingId listing) {
        return repository.findById(listing.value()).map(BackupValidationJpaEntity::toBackupValidation);
    }

    @Override
    public List<BackupValidation> findAll() {
        return repository.findAll().stream().map(BackupValidationJpaEntity::toBackupValidation).toList();
    }

    /** The listing is the key: saving again replaces its previous validation. */
    @Override
    public void save(BackupValidation validation) {
        repository.save(new BackupValidationJpaEntity(validation));
    }
}
