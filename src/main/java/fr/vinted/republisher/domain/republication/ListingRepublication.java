package fr.vinted.republisher.domain.republication;

import fr.vinted.republisher.domain.backup.ListingBackup;
import fr.vinted.republisher.domain.backup.ListingContent;
import fr.vinted.republisher.domain.deletion.DeletionOutcome;
import fr.vinted.republisher.domain.deletion.ListingDeletion;
import fr.vinted.republisher.domain.wardrobe.ScannedListing;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * An attempt to put a deleted listing on sale again, from the click on Publish on. It starts in error, so that a
 * crash right after the click blocks any new attempt rather than risk a duplicate, and is published once its new
 * listing is found on Vinted, or not published once Vinted is checked and shows nothing. Its new listing links the
 * old listing to the one replacing it.
 */
public record ListingRepublication(ListingRepublicationId id, VintedListingId listingId, Instant backupSavedAt,
                                   Instant attemptedAt, RepublicationOutcome outcome, Optional<Instant> publishedAt,
                                   Optional<VintedListingId> newListingId, Optional<Instant> checkedAt) {

    public ListingRepublication {
        Objects.requireNonNull(id);
        Objects.requireNonNull(listingId);
        Objects.requireNonNull(backupSavedAt);
        Objects.requireNonNull(attemptedAt);
        Objects.requireNonNull(outcome);
        Objects.requireNonNull(publishedAt);
        Objects.requireNonNull(newListingId);
        Objects.requireNonNull(checkedAt);
    }

    public static ListingRepublication attempted(ListingBackup backup, Instant attemptedAt) {
        return new ListingRepublication(new ListingRepublicationId(UUID.randomUUID()), backup.id(), backup.savedAt(),
                attemptedAt, RepublicationOutcome.FAILED, Optional.empty(), Optional.empty(), Optional.empty());
    }

    /**
     * A listing to link (US-48): its deletion is confirmed and no republication is in force, so a new listing put on
     * Vinted outside the tool can be linked to it.
     */
    public static boolean toLink(Optional<ListingDeletion> lastDeletion,
                                 Optional<ListingRepublication> lastRepublication) {
        return lastDeletion.filter(deletion -> deletion.outcome() == DeletionOutcome.CONFIRMED).isPresent()
                && lastRepublication.filter(last -> last.outcome() != RepublicationOutcome.NOT_PUBLISHED).isEmpty();
    }

    /** A new listing found on Vinted for a listing to link, published as of the link, from the current backup. */
    public static ListingRepublication linked(ListingBackup backup, VintedListingId newListing, Instant linkedAt) {
        return new ListingRepublication(new ListingRepublicationId(UUID.randomUUID()), backup.id(), backup.savedAt(),
                linkedAt, RepublicationOutcome.PUBLISHED, Optional.of(linkedAt), Optional.of(newListing),
                Optional.empty());
    }

    /** Among the listings the tool did not know, the only one with the saved title and price. */
    public static Optional<ScannedListing> newListingAmong(List<ScannedListing> unknown, ListingContent saved) {
        List<ScannedListing> matching = matching(unknown, saved);
        return matching.size() == 1 ? Optional.of(matching.getFirst()) : Optional.empty();
    }

    /** The listings with the saved title and price. */
    public static List<ScannedListing> matching(List<ScannedListing> listings, ListingContent saved) {
        return listings.stream()
                .filter(listing -> listing.title().equals(saved.title()) && listing.price().equals(saved.price()))
                .toList();
    }

    /** Only an attempt in error can be proven: once checked not published, a new attempt is made instead. */
    public ListingRepublication published(VintedListingId newListing, Instant provenAt) {
        if (outcome == RepublicationOutcome.PUBLISHED) {
            throw new ListingAlreadyRepublished(listingId);
        }
        requireFailed();
        return new ListingRepublication(id, listingId, backupSavedAt, attemptedAt, RepublicationOutcome.PUBLISHED,
                Optional.of(provenAt), Optional.of(newListing), Optional.empty());
    }

    /** Vinted was checked and shows no listing with the saved title and price. */
    public ListingRepublication notPublished(Instant checked) {
        requireFailed();
        return new ListingRepublication(id, listingId, backupSavedAt, attemptedAt, RepublicationOutcome.NOT_PUBLISHED,
                Optional.empty(), Optional.empty(), Optional.of(checked));
    }

    /** Only a republication in error or published has something to verify on Vinted. */
    public void requireToVerify() {
        if (outcome == RepublicationOutcome.NOT_PUBLISHED) {
            throw new NoRepublicationToVerify(listingId);
        }
    }

    /** A listing is republished once at most, and never again after an attempt left in error. */
    public void allowNewAttempt() {
        switch (outcome) {
            case PUBLISHED -> throw new ListingAlreadyRepublished(listingId);
            case FAILED -> throw new RepublicationInError(listingId);
            case NOT_PUBLISHED -> {
            }
        }
    }

    /** Only a republication in error waits for the member to say what Vinted shows. */
    public void requireFailed() {
        if (outcome != RepublicationOutcome.FAILED) {
            throw new NoRepublicationToVerify(listingId);
        }
    }
}
