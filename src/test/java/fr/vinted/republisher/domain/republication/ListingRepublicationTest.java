package fr.vinted.republisher.domain.republication;

import fr.vinted.republisher.domain.backup.ListingContent;
import fr.vinted.republisher.domain.cadence.WriteOrigin;
import fr.vinted.republisher.domain.deletion.ListingDeletion;
import fr.vinted.republisher.domain.wardrobe.ScannedListing;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static fr.vinted.republisher.domain.backup.BackupFixtures.aBackup;
import static fr.vinted.republisher.domain.backup.BackupFixtures.aContent;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.ACTIVE;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.aListing;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ListingRepublicationTest {

    private static final Instant SAVED_AT = Instant.parse("2026-11-10T10:00:00Z");
    private static final Instant CLICKED_AT = Instant.parse("2026-11-11T10:06:00Z");
    private static final Instant PROVEN_AT = Instant.parse("2026-11-11T10:06:20Z");
    private static final VintedListingId NEW_LISTING = new VintedListingId(2);

    private final ListingRepublication attempt = ListingRepublication.attempted(aBackup(1, SAVED_AT), CLICKED_AT,
            WriteOrigin.RUN);

    @Test
    void an_attempt_is_in_error_until_its_new_listing_is_found() {
        assertThat(attempt.listingId()).isEqualTo(new VintedListingId(1));
        assertThat(attempt.backupSavedAt()).isEqualTo(SAVED_AT);
        assertThat(attempt.attemptedAt()).isEqualTo(CLICKED_AT);
        assertThat(attempt.outcome()).isEqualTo(RepublicationOutcome.FAILED);
        assertThat(attempt.publishedAt()).isEmpty();
        assertThat(attempt.newListingId()).isEmpty();
    }

    @Test
    void a_proven_attempt_is_published_with_its_new_listing() {
        ListingRepublication published = attempt.published(NEW_LISTING, PROVEN_AT);

        assertThat(published.id()).isEqualTo(attempt.id());
        assertThat(published.outcome()).isEqualTo(RepublicationOutcome.PUBLISHED);
        assertThat(published.newListingId()).contains(NEW_LISTING);
        assertThat(published.publishedAt()).contains(PROVEN_AT);
    }

    @Test
    void a_published_republication_cannot_be_published_again() {
        ListingRepublication published = attempt.published(NEW_LISTING, PROVEN_AT);

        assertThatThrownBy(() -> published.published(new VintedListingId(3), PROVEN_AT))
                .isInstanceOf(ListingAlreadyRepublished.class);
    }

    @Test
    void a_failed_attempt_missing_from_vinted_is_not_published_once_checked() {
        ListingRepublication notPublished = attempt.notPublished(PROVEN_AT);

        assertThat(notPublished.id()).isEqualTo(attempt.id());
        assertThat(notPublished.outcome()).isEqualTo(RepublicationOutcome.NOT_PUBLISHED);
        assertThat(notPublished.checkedAt()).contains(PROVEN_AT);
        assertThat(notPublished.publishedAt()).isEmpty();
        assertThat(notPublished.newListingId()).isEmpty();
    }

    @Nested
    class Verification {

        @Test
        void applies_to_a_failed_or_published_republication() {
            attempt.requireToVerify();
            attempt.published(NEW_LISTING, PROVEN_AT).requireToVerify();
        }

        @Test
        void is_refused_once_not_published() {
            assertThatThrownBy(() -> attempt.notPublished(PROVEN_AT).requireToVerify())
                    .isInstanceOf(NoRepublicationToVerify.class);
        }

        @Test
        void cannot_settle_a_published_republication_as_not_published() {
            assertThatThrownBy(() -> attempt.published(NEW_LISTING, PROVEN_AT).notPublished(PROVEN_AT))
                    .isInstanceOf(NoRepublicationToVerify.class);
        }

        @Test
        void cannot_publish_a_republication_checked_not_published() {
            assertThatThrownBy(() -> attempt.notPublished(PROVEN_AT).published(NEW_LISTING, PROVEN_AT))
                    .isInstanceOf(NoRepublicationToVerify.class);
        }
    }

    @Nested
    class New_attempt {

        @Test
        void is_refused_once_republished() {
            assertThatThrownBy(() -> attempt.published(NEW_LISTING, PROVEN_AT).allowNewAttempt())
                    .isInstanceOf(ListingAlreadyRepublished.class);
        }

        @Test
        void is_refused_while_in_error() {
            assertThatThrownBy(attempt::allowNewAttempt).isInstanceOf(RepublicationInError.class);
        }

        @Test
        void is_allowed_once_checked_not_published() {
            attempt.notPublished(PROVEN_AT).allowNewAttempt();
        }
    }

    @Nested
    class Link {

        private final ListingDeletion deleted =
                ListingDeletion.attempted(aBackup(1, SAVED_AT), CLICKED_AT, WriteOrigin.RUN).confirmed(PROVEN_AT);

        @Test
        void a_deleted_listing_never_republished_is_to_link() {
            assertThat(ListingRepublication.toLink(Optional.of(deleted), Optional.empty())).isTrue();
        }

        @Test
        void a_deleted_listing_whose_republication_was_checked_not_published_is_to_link() {
            assertThat(ListingRepublication.toLink(Optional.of(deleted), Optional.of(attempt.notPublished(PROVEN_AT))))
                    .isTrue();
        }

        @Test
        void a_listing_without_confirmed_deletion_is_not_to_link() {
            ListingDeletion unconfirmed = ListingDeletion.attempted(aBackup(1, SAVED_AT), CLICKED_AT, WriteOrigin.RUN);

            assertThat(ListingRepublication.toLink(Optional.empty(), Optional.empty())).isFalse();
            assertThat(ListingRepublication.toLink(Optional.of(unconfirmed), Optional.empty())).isFalse();
            assertThat(ListingRepublication.toLink(Optional.of(unconfirmed.notDone(PROVEN_AT)), Optional.empty()))
                    .isFalse();
        }

        @Test
        void a_listing_with_a_republication_in_error_or_published_is_not_to_link() {
            assertThat(ListingRepublication.toLink(Optional.of(deleted), Optional.of(attempt))).isFalse();
            assertThat(ListingRepublication.toLink(Optional.of(deleted),
                    Optional.of(attempt.published(NEW_LISTING, PROVEN_AT)))).isFalse();
        }

        @Test
        void a_linked_republication_is_published_at_the_link_date_with_the_current_backup() {
            ListingRepublication linked = ListingRepublication.linked(aBackup(1, SAVED_AT), NEW_LISTING, PROVEN_AT);

            assertThat(linked.listingId()).isEqualTo(new VintedListingId(1));
            assertThat(linked.backupSavedAt()).isEqualTo(SAVED_AT);
            assertThat(linked.attemptedAt()).isEqualTo(PROVEN_AT);
            assertThat(linked.outcome()).isEqualTo(RepublicationOutcome.PUBLISHED);
            assertThat(linked.publishedAt()).contains(PROVEN_AT);
            assertThat(linked.newListingId()).contains(NEW_LISTING);
            assertThat(linked.checkedAt()).isEmpty();
            // US-49 : aucune écriture sur Vinted, elle ne compte pas dans la cadence de la série
            assertThat(linked.origin()).isEqualTo(WriteOrigin.MANUAL);
        }
    }

    @Nested
    class New_listing {

        private final ListingContent saved = aContent(1, "Robe bleue", "Portée une fois", "12.00");

        @Test
        void is_the_only_unknown_listing_with_the_saved_title_and_price() {
            ScannedListing republished = aListing(2, "Robe bleue", "12.00", ACTIVE);

            Optional<ScannedListing> found = ListingRepublication.newListingAmong(List.of(
                    aListing(3, "Robe bleue", "15.00", ACTIVE),
                    republished,
                    aListing(4, "Robe rouge", "12.00", ACTIVE)), saved);

            assertThat(found).contains(republished);
        }

        @Test
        void is_not_found_without_a_matching_listing() {
            assertThat(ListingRepublication.newListingAmong(List.of(aListing(3, "Robe rouge", "12.00", ACTIVE)),
                    saved)).isEmpty();
        }

        @Test
        void is_not_found_when_two_listings_match() {
            assertThat(ListingRepublication.newListingAmong(List.of(
                    aListing(2, "Robe bleue", "12.00", ACTIVE),
                    aListing(3, "Robe bleue", "12.00", ACTIVE)), saved)).isEmpty();
        }
    }
}
