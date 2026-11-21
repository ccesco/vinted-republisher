package fr.vinted.republisher.application.inventory;

import fr.vinted.republisher.domain.backup.BackupValidationRepository;
import fr.vinted.republisher.domain.backup.BackupVerificationRepository;
import fr.vinted.republisher.domain.backup.ListingBackup;
import fr.vinted.republisher.domain.backup.ListingBackupRepository;
import fr.vinted.republisher.domain.backup.VerificationResult;
import fr.vinted.republisher.domain.deletion.ListingDeletionRepository;
import fr.vinted.republisher.domain.inventory.InventoryEntry;
import fr.vinted.republisher.domain.inventory.ListingDetail;
import fr.vinted.republisher.domain.inventory.ListingInventory;
import fr.vinted.republisher.domain.inventory.ListingLifecycle;
import fr.vinted.republisher.domain.inventory.ListingNotFound;
import fr.vinted.republisher.domain.inventory.RepublicationAdvice;
import fr.vinted.republisher.domain.inventory.StatisticsComparison;
import fr.vinted.republisher.domain.journal.ActionJournal;
import fr.vinted.republisher.domain.republication.ListingRepublication;
import fr.vinted.republisher.domain.republication.ListingRepublicationRepository;
import fr.vinted.republisher.domain.republication.RepublicationDelay;
import fr.vinted.republisher.domain.republication.RepublicationOutcome;
import fr.vinted.republisher.domain.sale.ListingSale;
import fr.vinted.republisher.domain.sale.ListingSaleRepository;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.ListingRepository;
import fr.vinted.republisher.domain.wardrobe.ListingStatisticsRepository;
import fr.vinted.republisher.domain.wardrobe.StatisticsReading;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Reads what the tool keeps about one listing, never Vinted, with the life of the article across the chain of
 * published republications it belongs to and, for a listing put on sale by a republication, its statistics next to
 * those of the listing it replaces.
 */
public class ShowListingDetail {

    private final ListingRepository listings;
    private final ListingStatisticsRepository statistics;
    private final ListingBackupRepository backups;
    private final BackupValidationRepository validations;
    private final ListingDeletionRepository deletions;
    private final ListingRepublicationRepository republications;
    private final RepublicationDelay delay;
    private final Clock clock;
    private final ActionJournal journal;
    private final BackupVerificationRepository verifications;
    private final ListingSaleRepository sales;

    public ShowListingDetail(ListingRepository listings, ListingStatisticsRepository statistics,
                             ListingBackupRepository backups, BackupValidationRepository validations,
                             ListingDeletionRepository deletions, ListingRepublicationRepository republications,
                             RepublicationDelay delay, Clock clock, ActionJournal journal,
                             BackupVerificationRepository verifications, ListingSaleRepository sales) {
        this.listings = listings;
        this.statistics = statistics;
        this.backups = backups;
        this.validations = validations;
        this.deletions = deletions;
        this.republications = republications;
        this.delay = delay;
        this.clock = clock;
        this.journal = journal;
        this.verifications = verifications;
        this.sales = sales;
    }

    public ListingDetail show(VintedListingId id) {
        Listing listing = listings.find(id).orElseThrow(() -> new ListingNotFound(id));
        List<StatisticsReading> history = statistics.historyOf(id);
        Instant now = clock.instant();
        Instant baselineAsOf = RepublicationAdvice.baselineAsOf(now);
        Optional<VerificationResult> verification = verifications.find(id);
        Optional<ListingSale> sale = sales.find(id);
        ListingInventory inventory = ListingInventory.of(List.of(listing), history.stream().limit(1).toList(),
                history.stream().filter(reading -> !reading.readAt().isAfter(baselineAsOf)).limit(1).toList(),
                backups.find(id).map(ListingBackup::summary).stream().toList(), validations.find(id).stream().toList(),
                verification.stream().toList(),
                deletions.findLatest(id).stream().toList(), republications.findLatest(id).stream().toList(),
                sale.stream().toList(), delay, now);
        Set<VintedListingId> seen = new HashSet<>(Set.of(id));
        List<VintedListingId> previous = previousListings(id, seen);
        List<VintedListingId> next = nextListings(id, seen);
        InventoryEntry entry = inventory.entries().getFirst();
        Optional<VerificationResult> lastVerification = verification
                .filter(result -> entry.backup().filter(result::holdsFor).isPresent());
        return new ListingDetail(entry, history, journal.of(id),
                comparison(id, previous, history.stream().findFirst()), listings.priceHistoryOf(id),
                lifecycle(previous, id, next), lastVerification, sale);
    }

    /** Walks back the published republications that led to this listing, the one replaced directly first. */
    private List<VintedListingId> previousListings(VintedListingId id, Set<VintedListingId> seen) {
        List<VintedListingId> previous = new ArrayList<>();
        Optional<ListingRepublication> step = republications.findPublishedAs(id);
        while (step.isPresent() && seen.add(step.get().listingId())) {
            previous.add(step.get().listingId());
            step = republications.findPublishedAs(step.get().listingId());
        }
        return previous;
    }

    /** Walks forward the published republications of this listing, the one replacing it directly first. */
    private List<VintedListingId> nextListings(VintedListingId id, Set<VintedListingId> seen) {
        List<VintedListingId> next = new ArrayList<>();
        Optional<VintedListingId> step = newListingOf(id);
        while (step.isPresent() && seen.add(step.get())) {
            next.add(step.get());
            step = newListingOf(step.get());
        }
        return next;
    }

    private Optional<VintedListingId> newListingOf(VintedListingId id) {
        return republications.findLatest(id)
                .filter(republication -> republication.outcome() == RepublicationOutcome.PUBLISHED)
                .flatMap(ListingRepublication::newListingId);
    }

    private Optional<StatisticsComparison> comparison(VintedListingId id, List<VintedListingId> previous,
                                                      Optional<StatisticsReading> latest) {
        if (previous.isEmpty()) {
            return Optional.empty();
        }
        List<Optional<StatisticsReading>> previousLatest = previous.stream()
                .map(listing -> statistics.historyOf(listing).stream().findFirst()).toList();
        return Optional.of(StatisticsComparison.of(republications.findPublishedAs(id).orElseThrow(), previousLatest,
                latest));
    }

    /** A listing of the chain unknown to the tool (never expected) is skipped. */
    private ListingLifecycle lifecycle(List<VintedListingId> previous, VintedListingId id,
                                       List<VintedListingId> next) {
        List<VintedListingId> chain = new ArrayList<>(previous.reversed());
        chain.add(id);
        chain.addAll(next);
        return new ListingLifecycle(chain.stream()
                .flatMap(listing -> listings.find(listing).stream())
                .flatMap(listing -> ListingLifecycle.eventsOf(listing, deletions.findLatest(listing.id()),
                        republications.findLatest(listing.id())).stream())
                .toList());
    }
}
