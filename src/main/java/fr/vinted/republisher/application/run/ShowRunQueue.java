package fr.vinted.republisher.application.run;

import fr.vinted.republisher.application.cadence.CadenceCheck;
import fr.vinted.republisher.application.inventory.ShowListingInventory;
import fr.vinted.republisher.domain.deletion.ListingDeletion;
import fr.vinted.republisher.domain.deletion.ListingDeletionRepository;
import fr.vinted.republisher.domain.inventory.InventoryCriteria;
import fr.vinted.republisher.domain.inventory.InventoryEntry;
import fr.vinted.republisher.domain.inventory.ListingStage;
import fr.vinted.republisher.domain.republication.RepublicationDelay;
import fr.vinted.republisher.domain.run.AutomaticRun;
import fr.vinted.republisher.domain.run.QueueCandidate;
import fr.vinted.republisher.domain.run.RunQueue;
import fr.vinted.republisher.domain.run.WriteModes;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** Foresees what the automatic run would launch (US-41). Reads what the tool keeps, never Vinted; stores nothing. */
public class ShowRunQueue {

    private final ShowListingInventory inventory;
    private final ListingDeletionRepository deletions;
    private final RepublicationDelay delay;
    private final CadenceCheck cadence;
    private final WriteModes modes;
    private final Supplier<AutomaticRun> currentRun;
    private final Clock clock;

    public ShowRunQueue(ShowListingInventory inventory, ListingDeletionRepository deletions, RepublicationDelay delay,
                        CadenceCheck cadence, WriteModes modes, Supplier<AutomaticRun> currentRun, Clock clock) {
        this.inventory = inventory;
        this.deletions = deletions;
        this.delay = delay;
        this.cadence = cadence;
        this.modes = modes;
        this.currentRun = currentRun;
        this.clock = clock;
    }

    /**
     * Listings set aside by a run in progress, or relaunched in its round under way, are left out: a new run starts
     * without any.
     */
    public RunQueue show() {
        Instant now = clock.instant();
        AutomaticRun run = currentRun.get();
        Map<VintedListingId, ListingDeletion> latestDeletions = deletions.findAllLatest().stream()
                .collect(Collectors.toMap(ListingDeletion::listingId, Function.identity()));
        List<QueueCandidate> candidates = inventory.show(InventoryCriteria.everyListing()).stream()
                .filter(entry -> !run.leavesOut(entry.listing().id()))
                .map(entry -> candidateOf(entry, Optional.ofNullable(latestDeletions.get(entry.listing().id()))))
                .toList();
        return RunQueue.forecast(candidates, cadence.forecast(), delay, modes, now);
    }

    private QueueCandidate candidateOf(InventoryEntry entry, Optional<ListingDeletion> latestDeletion) {
        Optional<Instant> republishableAt = entry.stage() == ListingStage.WAITING
                ? latestDeletion.flatMap(delay::republishableAt)
                : Optional.empty();
        return new QueueCandidate(entry.listing().id(), entry.listing().title(), entry.stage(), republishableAt);
    }
}
