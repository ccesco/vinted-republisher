package fr.vinted.republisher.domain.inventory;

import fr.vinted.republisher.domain.deletion.DeletionOutcome;
import fr.vinted.republisher.domain.deletion.ListingDeletion;
import fr.vinted.republisher.domain.deletion.ListingDeletionId;
import fr.vinted.republisher.domain.republication.ListingRepublication;
import fr.vinted.republisher.domain.republication.ListingRepublicationId;
import fr.vinted.republisher.domain.republication.RepublicationOutcome;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static fr.vinted.republisher.domain.inventory.LifecycleEventType.DELETED;
import static fr.vinted.republisher.domain.inventory.LifecycleEventType.FIRST_SEEN;
import static fr.vinted.republisher.domain.inventory.LifecycleEventType.LEFT_WARDROBE;
import static fr.vinted.republisher.domain.inventory.LifecycleEventType.REPUBLISHED;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.anActiveListing;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.seenSince;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ListingLifecycleTest {

    private static final Instant FIRST_SEEN_AT = Instant.parse("2026-11-01T09:00:00Z");
    private static final Instant LAST_SEEN_AT = Instant.parse("2026-11-05T09:00:00Z");
    private static final Instant CLICKED_AT = Instant.parse("2026-11-10T09:00:00Z");
    private static final Instant PUBLISHED_AT = Instant.parse("2026-11-10T09:10:00Z");
    private static final VintedListingId ONE = new VintedListingId(1);
    private static final VintedListingId TWO = new VintedListingId(2);

    @Test
    void a_present_listing_was_only_first_seen() {
        assertThat(ListingLifecycle.eventsOf(present(), Optional.empty(), Optional.empty()))
                .containsExactly(event(FIRST_SEEN, FIRST_SEEN_AT));
    }

    @Test
    void a_confirmed_deletion_is_dated_by_its_click() {
        assertThat(ListingLifecycle.eventsOf(present().absent(), Optional.of(deletion(DeletionOutcome.CONFIRMED)),
                Optional.empty()))
                .containsExactly(event(FIRST_SEEN, FIRST_SEEN_AT), event(DELETED, CLICKED_AT));
    }

    @Test
    void a_published_republication_names_the_new_listing() {
        assertThat(ListingLifecycle.eventsOf(present().absent(), Optional.of(deletion(DeletionOutcome.CONFIRMED)),
                Optional.of(published())))
                .containsExactly(event(FIRST_SEEN, FIRST_SEEN_AT), event(DELETED, CLICKED_AT),
                        new LifecycleEvent(REPUBLISHED, ONE, PUBLISHED_AT, Optional.of(TWO)));
    }

    @Test
    void an_absent_listing_not_deleted_by_the_tool_left_the_wardrobe_when_last_seen() {
        assertThat(ListingLifecycle.eventsOf(present().absent(), Optional.of(deletion(DeletionOutcome.NOT_DONE)),
                Optional.empty()))
                .containsExactly(event(FIRST_SEEN, FIRST_SEEN_AT), event(LEFT_WARDROBE, LAST_SEEN_AT));
    }

    @Test
    void uncertain_attempts_are_no_events() {
        ListingRepublication failed = new ListingRepublication(new ListingRepublicationId(UUID.randomUUID()), ONE,
                FIRST_SEEN_AT, PUBLISHED_AT, RepublicationOutcome.FAILED, Optional.empty(), Optional.empty(),
                Optional.empty());

        assertThat(ListingLifecycle.eventsOf(present(), Optional.of(deletion(DeletionOutcome.UNCONFIRMED)),
                Optional.of(failed)))
                .containsExactly(event(FIRST_SEEN, FIRST_SEEN_AT));
    }

    @Test
    void a_lifecycle_has_at_least_one_event() {
        assertThatThrownBy(() -> new ListingLifecycle(List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    /** Seen first on the 1st, last on the 5th. */
    private static Listing present() {
        return seenSince(anActiveListing(1), FIRST_SEEN_AT, LAST_SEEN_AT);
    }

    private static LifecycleEvent event(LifecycleEventType type, Instant at) {
        return new LifecycleEvent(type, ONE, at, Optional.empty());
    }

    private static ListingDeletion deletion(DeletionOutcome outcome) {
        return new ListingDeletion(new ListingDeletionId(UUID.randomUUID()), ONE, FIRST_SEEN_AT, CLICKED_AT, outcome,
                outcome == DeletionOutcome.UNCONFIRMED ? Optional.empty() : Optional.of(CLICKED_AT.plusSeconds(30)));
    }

    private static ListingRepublication published() {
        return new ListingRepublication(new ListingRepublicationId(UUID.randomUUID()), ONE, FIRST_SEEN_AT,
                CLICKED_AT, RepublicationOutcome.PUBLISHED, Optional.of(PUBLISHED_AT), Optional.of(TWO),
                Optional.empty());
    }
}
