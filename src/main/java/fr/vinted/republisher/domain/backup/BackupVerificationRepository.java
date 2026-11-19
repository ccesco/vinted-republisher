package fr.vinted.republisher.domain.backup;

import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.util.List;
import java.util.Optional;

/** The last verification on Vinted of each listing, whichever version of its backup it compared. */
public interface BackupVerificationRepository {

    Optional<VerificationResult> find(VintedListingId listing);

    List<VerificationResult> findAll();

    /**
     * Replaces the previous result of the listing, if any. A failed result also cancels the validation of the listing,
     * all at once: a backup found different on Vinted is never left validated.
     */
    void save(VerificationResult result);
}
