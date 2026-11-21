package fr.vinted.republisher.domain.republication;

import fr.vinted.republisher.domain.cadence.WriteOrigin;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Every republication that reached the click on Publish, never removed: several per listing once checked not published. */
public interface ListingRepublicationRepository {

    /** The last attempt for the listing: the one that decides whether it can be republished. */
    Optional<ListingRepublication> findLatest(VintedListingId listing);

    /** The last attempt of every listing. */
    List<ListingRepublication> findAllLatest();

    /** The number of attempts found not published, per listing that has any, counted by the store in one read. */
    Map<VintedListingId, Integer> findNotPublishedCounts();

    /** The dates of the attempts of this origin made after {@code since}, whatever their outcome. */
    List<Instant> findAttemptedSince(WriteOrigin origin, Instant since);

    /** The new listings of every published republication. */
    Set<VintedListingId> findNewListings();

    /** The published republication whose new listing is this one, if any. */
    Optional<ListingRepublication> findPublishedAs(VintedListingId newListing);

    /** Records a new attempt, or how it was settled. */
    void save(ListingRepublication republication);
}
