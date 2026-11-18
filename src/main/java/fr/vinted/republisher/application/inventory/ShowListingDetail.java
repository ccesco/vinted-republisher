package fr.vinted.republisher.application.inventory;

import fr.vinted.republisher.domain.backup.BackupValidationRepository;
import fr.vinted.republisher.domain.backup.ListingBackupRepository;
import fr.vinted.republisher.domain.deletion.ListingDeletionRepository;
import fr.vinted.republisher.domain.inventory.ListingDetail;
import fr.vinted.republisher.domain.inventory.ListingInventory;
import fr.vinted.republisher.domain.inventory.ListingNotFound;
import fr.vinted.republisher.domain.inventory.StatisticsComparison;
import fr.vinted.republisher.domain.journal.ActionJournal;
import fr.vinted.republisher.domain.republication.ListingRepublication;
import fr.vinted.republisher.domain.republication.ListingRepublicationRepository;
import fr.vinted.republisher.domain.republication.RepublicationDelay;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.ListingRepository;
import fr.vinted.republisher.domain.wardrobe.ListingStatisticsRepository;
import fr.vinted.republisher.domain.wardrobe.StatisticsReading;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Reads what the tool keeps about one listing, never Vinted, with, for a listing put on sale by a republication, its
 * statistics next to those of the listing it replaces.
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

    public ShowListingDetail(ListingRepository listings, ListingStatisticsRepository statistics,
                             ListingBackupRepository backups, BackupValidationRepository validations,
                             ListingDeletionRepository deletions, ListingRepublicationRepository republications,
                             RepublicationDelay delay, Clock clock, ActionJournal journal) {
        this.listings = listings;
        this.statistics = statistics;
        this.backups = backups;
        this.validations = validations;
        this.deletions = deletions;
        this.republications = republications;
        this.delay = delay;
        this.clock = clock;
        this.journal = journal;
    }

    public ListingDetail show(VintedListingId id) {
        Listing listing = listings.find(id).orElseThrow(() -> new ListingNotFound(id));
        List<StatisticsReading> history = statistics.historyOf(id);
        ListingInventory inventory = ListingInventory.of(List.of(listing), history.stream().limit(1).toList(),
                backups.find(id).stream().toList(), validations.find(id).stream().toList(),
                deletions.findLatest(id).stream().toList(), republications.findLatest(id).stream().toList(), delay,
                clock.instant());
        return new ListingDetail(inventory.entries().getFirst(), history, journal.of(id),
                comparison(id, history.stream().findFirst()), listings.priceHistoryOf(id));
    }

    /** Walks back the chain of republications that led to this listing, stopping on a listing already seen. */
    private Optional<StatisticsComparison> comparison(VintedListingId id, Optional<StatisticsReading> latest) {
        Optional<ListingRepublication> republication = republications.findPublishedAs(id);
        if (republication.isEmpty()) {
            return Optional.empty();
        }
        List<Optional<StatisticsReading>> previousLatest = new ArrayList<>();
        Set<VintedListingId> seen = new HashSet<>(Set.of(id));
        Optional<ListingRepublication> step = republication;
        while (step.isPresent() && seen.add(step.get().listingId())) {
            VintedListingId previous = step.get().listingId();
            previousLatest.add(statistics.historyOf(previous).stream().findFirst());
            step = republications.findPublishedAs(previous);
        }
        return Optional.of(StatisticsComparison.of(republication.get(), previousLatest, latest));
    }
}
