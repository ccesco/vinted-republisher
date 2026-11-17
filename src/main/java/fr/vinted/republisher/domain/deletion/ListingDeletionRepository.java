package fr.vinted.republisher.domain.deletion;

import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Every deletion attempt, never removed. */
public interface ListingDeletionRepository {

    /** The most recent attempt on the listing. */
    Optional<ListingDeletion> findLatest(VintedListingId listing);

    /** The most recent attempt on each listing. */
    List<ListingDeletion> findAllLatest();

    /** The dates of the attempts made after {@code since}, whatever their outcome. */
    List<Instant> findAttemptedSince(Instant since);

    /** Records a new attempt, or the settlement of an existing one. */
    void save(ListingDeletion deletion);
}
