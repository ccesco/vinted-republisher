package fr.vinted.republisher.domain.backup;

import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.util.List;
import java.util.Optional;

/** The last validation of each listing, whichever version of its backup it was given to. */
public interface BackupValidationRepository {

    Optional<BackupValidation> find(VintedListingId listing);

    List<BackupValidation> findAll();

    /** Replaces the previous validation of the listing, if any. */
    void save(BackupValidation validation);
}
