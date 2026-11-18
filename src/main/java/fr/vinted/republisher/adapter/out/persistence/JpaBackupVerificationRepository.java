package fr.vinted.republisher.adapter.out.persistence;

import fr.vinted.republisher.domain.backup.BackupVerificationRepository;
import fr.vinted.republisher.domain.backup.VerificationResult;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
class JpaBackupVerificationRepository implements BackupVerificationRepository {

    private final BackupVerificationJpaRepository repository;
    private final BackupValidationJpaRepository validations;

    JpaBackupVerificationRepository(BackupVerificationJpaRepository repository,
                                    BackupValidationJpaRepository validations) {
        this.repository = repository;
        this.validations = validations;
    }

    @Override
    public Optional<VerificationResult> find(VintedListingId listing) {
        return repository.findById(listing.value()).map(BackupVerificationJpaEntity::toVerificationResult);
    }

    @Override
    public List<VerificationResult> findAll() {
        return repository.findAll().stream().map(BackupVerificationJpaEntity::toVerificationResult).toList();
    }

    /** The listing is the key: saving again replaces its previous result. */
    @Override
    @Transactional
    public void save(VerificationResult result) {
        repository.save(new BackupVerificationJpaEntity(result));
        if (!result.passed()) {
            validations.deleteById(result.listingId().value());
        }
    }
}
