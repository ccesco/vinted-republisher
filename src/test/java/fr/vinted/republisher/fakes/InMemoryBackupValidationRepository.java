package fr.vinted.republisher.fakes;

import fr.vinted.republisher.domain.backup.BackupValidation;
import fr.vinted.republisher.domain.backup.BackupValidationRepository;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemoryBackupValidationRepository implements BackupValidationRepository {

    private final Map<VintedListingId, BackupValidation> validations = new LinkedHashMap<>();

    @Override
    public Optional<BackupValidation> find(VintedListingId listing) {
        return Optional.ofNullable(validations.get(listing));
    }

    @Override
    public List<BackupValidation> findAll() {
        return List.copyOf(validations.values());
    }

    @Override
    public void save(BackupValidation validation) {
        validations.put(validation.listingId(), validation);
    }
}
