package fr.vinted.republisher.fakes;

import fr.vinted.republisher.domain.backup.BackupVerificationRepository;
import fr.vinted.republisher.domain.backup.VerificationResult;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemoryBackupVerificationRepository implements BackupVerificationRepository {

    private final Map<VintedListingId, VerificationResult> results = new LinkedHashMap<>();
    private final InMemoryBackupValidationRepository validations;

    /** For a reading only: no validation to cancel. */
    public InMemoryBackupVerificationRepository() {
        this(new InMemoryBackupValidationRepository());
    }

    /** A failed result cancels the validation of its listing among {@code validations}. */
    public InMemoryBackupVerificationRepository(InMemoryBackupValidationRepository validations) {
        this.validations = validations;
    }

    @Override
    public Optional<VerificationResult> find(VintedListingId listing) {
        return Optional.ofNullable(results.get(listing));
    }

    @Override
    public List<VerificationResult> findAll() {
        return List.copyOf(results.values());
    }

    @Override
    public void save(VerificationResult result) {
        results.put(result.listingId(), result);
        if (!result.passed()) {
            validations.cancel(result.listingId());
        }
    }
}
