package fr.vinted.republisher.domain.inventory;

import fr.vinted.republisher.domain.deletion.DeletionOutcome;
import fr.vinted.republisher.domain.deletion.ListingDeletion;
import fr.vinted.republisher.domain.republication.ListingRepublication;
import fr.vinted.republisher.domain.republication.RepublicationOutcome;
import fr.vinted.republisher.domain.wardrobe.Listing;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The life of an article: the established facts of every listing linked to it by published republications, the
 * oldest listing first. Doubts (unconfirmed deletion, republication in error) are no facts: the stage shows them.
 */
public record ListingLifecycle(List<LifecycleEvent> events) {

    public ListingLifecycle {
        events = List.copyOf(events);
        if (events.isEmpty()) {
            throw new IllegalArgumentException("A known listing was at least first seen");
        }
    }

    /** The facts of one listing, in the order they happen; leaving the wardrobe is dated by its last sighting. */
    public static List<LifecycleEvent> eventsOf(Listing listing, Optional<ListingDeletion> lastDeletion,
                                                Optional<ListingRepublication> lastRepublication) {
        List<LifecycleEvent> events = new ArrayList<>();
        events.add(new LifecycleEvent(LifecycleEventType.FIRST_SEEN, listing.id(), listing.firstSeenAt(),
                Optional.empty()));
        Optional<ListingDeletion> confirmed =
                lastDeletion.filter(deletion -> deletion.outcome() == DeletionOutcome.CONFIRMED);
        confirmed.ifPresent(deletion -> events.add(new LifecycleEvent(LifecycleEventType.DELETED, listing.id(),
                deletion.attemptedAt(), Optional.empty())));
        lastRepublication.filter(republication -> republication.outcome() == RepublicationOutcome.PUBLISHED)
                .ifPresent(republication -> events.add(new LifecycleEvent(LifecycleEventType.REPUBLISHED,
                        listing.id(), republication.publishedAt().orElseThrow(), republication.newListingId())));
        if (!listing.present() && confirmed.isEmpty()) {
            events.add(new LifecycleEvent(LifecycleEventType.LEFT_WARDROBE, listing.id(), listing.lastSeenAt(),
                    Optional.empty()));
        }
        return events;
    }
}
