package fr.vinted.republisher.domain.republication;

import fr.vinted.republisher.domain.backup.ListingBackup;
import fr.vinted.republisher.domain.backup.ListingContent;
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
 * listing is found in the wardrobe. Its new listing links the old listing to the one replacing it.
 */
public record ListingRepublication(ListingRepublicationId id, VintedListingId listingId, Instant backupSavedAt,
                                   Instant attemptedAt, RepublicationOutcome outcome, Optional<Instant> publishedAt,
                                   Optional<VintedListingId> newListingId) {

    public ListingRepublication {
        Objects.requireNonNull(id);
        Objects.requireNonNull(listingId);
        Objects.requireNonNull(backupSavedAt);
        Objects.requireNonNull(attemptedAt);
        Objects.requireNonNull(outcome);
        Objects.requireNonNull(publishedAt);
        Objects.requireNonNull(newListingId);
    }

    public static ListingRepublication attempted(ListingBackup backup, Instant attemptedAt) {
        return new ListingRepublication(new ListingRepublicationId(UUID.randomUUID()), backup.id(), backup.savedAt(),
                attemptedAt, RepublicationOutcome.FAILED, Optional.empty(), Optional.empty());
    }

    /** Among the listings the tool did not know, the only one with the saved title and price. */
    public static Optional<ScannedListing> newListingAmong(List<ScannedListing> unknown, ListingContent saved) {
        List<ScannedListing> matching = unknown.stream()
                .filter(listing -> listing.title().equals(saved.title()) && listing.price().equals(saved.price()))
                .toList();
        return matching.size() == 1 ? Optional.of(matching.getFirst()) : Optional.empty();
    }

    public ListingRepublication published(VintedListingId newListing, Instant provenAt) {
        if (outcome == RepublicationOutcome.PUBLISHED) {
            throw new ListingAlreadyRepublished(listingId);
        }
        return new ListingRepublication(id, listingId, backupSavedAt, attemptedAt, RepublicationOutcome.PUBLISHED,
                Optional.of(provenAt), Optional.of(newListing));
    }

    /** A listing is republished once at most, and never again after an attempt left in error. */
    public void allowNewAttempt() {
        switch (outcome) {
            case PUBLISHED -> throw new ListingAlreadyRepublished(listingId);
            case FAILED -> throw new RepublicationInError(listingId);
        }
    }
}
