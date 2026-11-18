package fr.vinted.republisher.acceptance;

import fr.vinted.republisher.application.inventory.ShowListingDetail;
import fr.vinted.republisher.domain.deletion.DeletionOutcome;
import fr.vinted.republisher.domain.deletion.ListingDeletion;
import fr.vinted.republisher.domain.deletion.ListingDeletionId;
import fr.vinted.republisher.domain.inventory.LifecycleEvent;
import fr.vinted.republisher.domain.inventory.LifecycleEventType;
import fr.vinted.republisher.domain.inventory.ListingDetail;
import fr.vinted.republisher.domain.republication.ListingRepublication;
import fr.vinted.republisher.domain.republication.ListingRepublicationId;
import fr.vinted.republisher.domain.republication.RepublicationDelay;
import fr.vinted.republisher.domain.republication.RepublicationOutcome;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import fr.vinted.republisher.fakes.AdjustableClock;
import fr.vinted.republisher.fakes.InMemoryActionJournal;
import fr.vinted.republisher.fakes.InMemoryBackupValidationRepository;
import fr.vinted.republisher.fakes.InMemoryListingBackupRepository;
import fr.vinted.republisher.fakes.InMemoryListingDeletionRepository;
import fr.vinted.republisher.fakes.InMemoryListingRepository;
import fr.vinted.republisher.fakes.InMemoryListingRepublicationRepository;
import fr.vinted.republisher.fakes.InMemoryListingStatisticsRepository;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static fr.vinted.republisher.domain.inventory.LifecycleEventType.DELETED;
import static fr.vinted.republisher.domain.inventory.LifecycleEventType.FIRST_SEEN;
import static fr.vinted.republisher.domain.inventory.LifecycleEventType.LEFT_WARDROBE;
import static fr.vinted.republisher.domain.inventory.LifecycleEventType.REPUBLISHED;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.ACTIVE;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.aListing;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.anActiveListing;
import static org.assertj.core.api.Assertions.assertThat;

/** US-32: the detail of a listing shows the life of the article across all its listings. */
class ListingLifecycleAcceptanceTest {

    private static final Instant NOV_1 = Instant.parse("2026-11-01T09:00:00Z");
    private static final Instant NOV_5 = Instant.parse("2026-11-05T09:00:00Z");
    private static final Instant NOV_10 = Instant.parse("2026-11-10T09:00:00Z");
    private static final Instant NOV_20 = Instant.parse("2026-11-20T09:00:00Z");
    private static final Instant NOV_25 = Instant.parse("2026-11-25T09:00:00Z");

    private final InMemoryListingRepository listings = new InMemoryListingRepository();
    private final InMemoryListingDeletionRepository deletions = new InMemoryListingDeletionRepository();
    private final InMemoryListingRepublicationRepository republications = new InMemoryListingRepublicationRepository();
    private final ShowListingDetail showListingDetail = new ShowListingDetail(listings,
            new InMemoryListingStatisticsRepository(), new InMemoryListingBackupRepository(),
            new InMemoryBackupValidationRepository(), deletions, republications,
            new RepublicationDelay(Duration.ofMinutes(5)), new AdjustableClock(NOV_25), new InMemoryActionJournal());

    @Test
    void keeps_the_first_seen_date() {
        // Étant donné l'annonce 101 vue pour la première fois le 1er novembre
        listings.saveAll(List.of(Listing.seen(aListing(101, "Pull rayé", "12.50", ACTIVE), NOV_1)));

        // Quand elle est relue le 5 novembre, avec un autre prix
        listings.saveAll(List.of(Listing.seen(aListing(101, "Pull rayé", "10.00", ACTIVE), NOV_5)));

        // Alors sa première détection reste le 1er novembre, sa dernière vue est le 5 novembre
        Listing listing = showListingDetail.show(new VintedListingId(101)).entry().listing();
        assertThat(listing.firstSeenAt()).isEqualTo(NOV_1);
        assertThat(listing.lastSeenAt()).isEqualTo(NOV_5);
    }

    @Test
    void a_listing_never_handled_has_only_its_first_sighting() {
        // Étant donné l'annonce 101 présente, vue pour la première fois le 1er novembre
        seen(101, NOV_1);

        // Quand j'ouvre sa fiche, alors son cycle de vie a un seul événement
        assertThat(lifecycleOf(101)).containsExactly(event(FIRST_SEEN, 101, NOV_1));
    }

    @Test
    void shows_the_whole_article_from_any_listing_of_the_chain() {
        // Étant donné l'annonce 1 vue le 1er, supprimée le 10, republiée le 10 sous l'annonce 2
        // Et l'annonce 2 vue le 10, supprimée le 20 et republiée le 20 sous l'annonce 3
        seen(1, NOV_1);
        deletions.save(confirmedDeletion(1, NOV_10));
        republications.save(published(1, 2, NOV_10));
        seen(2, NOV_10);
        deletions.save(confirmedDeletion(2, NOV_20));
        republications.save(published(2, 3, NOV_20));
        seen(3, NOV_20);

        // Quand j'ouvre la fiche de l'annonce 1, de la 2 ou de la 3
        // Alors chacune montre les mêmes événements dans l'ordre
        List<LifecycleEvent> article = List.of(event(FIRST_SEEN, 1, NOV_1), event(DELETED, 1, NOV_10),
                republishedEvent(1, 2, NOV_10), event(FIRST_SEEN, 2, NOV_10), event(DELETED, 2, NOV_20),
                republishedEvent(2, 3, NOV_20), event(FIRST_SEEN, 3, NOV_20));
        assertThat(lifecycleOf(1)).isEqualTo(article);
        assertThat(lifecycleOf(2)).isEqualTo(article);
        assertThat(lifecycleOf(3)).isEqualTo(article);
    }

    @Test
    void a_listing_gone_without_the_tool_left_the_wardrobe() {
        // Étant donné l'annonce 101 vue le 1er, revue le 5, puis absente au scan suivant, sans suppression confirmée
        seen(101, NOV_1);
        listings.saveAll(List.of(Listing.seen(anActiveListing(101), NOV_5).absent()));

        // Quand j'ouvre sa fiche, alors : vue pour la première fois le 1er, sortie du dressing (dernière vue le 5)
        assertThat(lifecycleOf(101)).containsExactly(event(FIRST_SEEN, 101, NOV_1), event(LEFT_WARDROBE, 101, NOV_5));
    }

    @Test
    void uncertain_facts_are_no_events() {
        // Étant donné l'annonce 101 dont la suppression n'est pas confirmée
        // Et l'annonce 102 supprimée dont la republication est en erreur
        seen(101, NOV_1);
        deletions.save(deletion(101, NOV_10, DeletionOutcome.UNCONFIRMED));
        seen(102, NOV_1);
        deletions.save(confirmedDeletion(102, NOV_10));
        republications.save(failedRepublication(102, NOV_20));

        // Quand j'ouvre leur fiche
        // Alors la 101 n'a que sa première détection, la 102 sa première détection et sa suppression
        assertThat(lifecycleOf(101)).containsExactly(event(FIRST_SEEN, 101, NOV_1));
        assertThat(lifecycleOf(102)).containsExactly(event(FIRST_SEEN, 102, NOV_1), event(DELETED, 102, NOV_10));
    }

    @Test
    void a_chain_that_loops_stops_on_a_listing_already_seen() {
        // Cas limite (impossible sur Vinted) : 1 republiée sous 2, et 2 republiée sous 1
        seen(1, NOV_1);
        seen(2, NOV_10);
        republications.save(published(1, 2, NOV_10));
        republications.save(published(2, 1, NOV_20));

        assertThat(lifecycleOf(2)).containsExactly(event(FIRST_SEEN, 1, NOV_1), republishedEvent(1, 2, NOV_10),
                event(FIRST_SEEN, 2, NOV_10), republishedEvent(2, 1, NOV_20));
        assertThat(lifecycleOf(1)).containsExactly(event(FIRST_SEEN, 2, NOV_10), republishedEvent(2, 1, NOV_20),
                event(FIRST_SEEN, 1, NOV_1), republishedEvent(1, 2, NOV_10));
    }

    private List<LifecycleEvent> lifecycleOf(long id) {
        ListingDetail detail = showListingDetail.show(new VintedListingId(id));
        return detail.lifecycle().events();
    }

    private void seen(long id, Instant at) {
        listings.saveAll(List.of(Listing.seen(anActiveListing(id), at)));
    }

    private static LifecycleEvent event(LifecycleEventType type, long listing, Instant at) {
        return new LifecycleEvent(type, new VintedListingId(listing), at, Optional.empty());
    }

    private static LifecycleEvent republishedEvent(long listing, long newListing, Instant at) {
        return new LifecycleEvent(REPUBLISHED, new VintedListingId(listing), at,
                Optional.of(new VintedListingId(newListing)));
    }

    private static ListingDeletion confirmedDeletion(long listing, Instant clickedAt) {
        return deletion(listing, clickedAt, DeletionOutcome.CONFIRMED);
    }

    private static ListingDeletion deletion(long listing, Instant clickedAt, DeletionOutcome outcome) {
        return new ListingDeletion(new ListingDeletionId(UUID.randomUUID()), new VintedListingId(listing), NOV_1,
                clickedAt, outcome, outcome == DeletionOutcome.UNCONFIRMED
                ? Optional.empty() : Optional.of(clickedAt.plusSeconds(30)));
    }

    private static ListingRepublication published(long listing, long newListing, Instant publishedAt) {
        return new ListingRepublication(new ListingRepublicationId(UUID.randomUUID()), new VintedListingId(listing),
                NOV_1, publishedAt.minusSeconds(60), RepublicationOutcome.PUBLISHED, Optional.of(publishedAt),
                Optional.of(new VintedListingId(newListing)), Optional.empty());
    }

    private static ListingRepublication failedRepublication(long listing, Instant attemptedAt) {
        return new ListingRepublication(new ListingRepublicationId(UUID.randomUUID()), new VintedListingId(listing),
                NOV_1, attemptedAt, RepublicationOutcome.FAILED, Optional.empty(), Optional.empty(), Optional.empty());
    }
}
