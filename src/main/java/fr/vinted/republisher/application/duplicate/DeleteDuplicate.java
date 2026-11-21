package fr.vinted.republisher.application.duplicate;

import fr.vinted.republisher.application.journal.JournalWriter;
import fr.vinted.republisher.domain.backup.PhotoStorage;
import fr.vinted.republisher.domain.deletion.DeletionMode;
import fr.vinted.republisher.domain.deletion.DeletionVisit;
import fr.vinted.republisher.domain.deletion.ListingChanged;
import fr.vinted.republisher.domain.deletion.ListingDeleter;
import fr.vinted.republisher.domain.deletion.ListingDifference;
import fr.vinted.republisher.domain.duplicate.DuplicateDeletionOutcome;
import fr.vinted.republisher.domain.duplicate.ListingEraser;
import fr.vinted.republisher.domain.duplicate.TwinNotConfirmed;
import fr.vinted.republisher.domain.inventory.ListingNotFound;
import fr.vinted.republisher.domain.journal.ActionOutcome;
import fr.vinted.republisher.domain.journal.JournaledAction;
import fr.vinted.republisher.domain.session.BrowserAction;
import fr.vinted.republisher.domain.session.BrowserBusy;
import fr.vinted.republisher.domain.session.VintedSession;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.ListingLink;
import fr.vinted.republisher.domain.wardrobe.ListingRepository;
import fr.vinted.republisher.domain.wardrobe.ScannedListing;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * Deletes a duplicate on Vinted, then erases it from the tool (US-47), only while a twin is read on sale on Vinted
 * with the same title and price. Every check is made before Chrome opens, then on Vinted before any click.
 */
public class DeleteDuplicate {

    private static final Logger LOG = LoggerFactory.getLogger(DeleteDuplicate.class);

    private final VintedSession session;
    private final ShowDuplicates showDuplicates;
    private final ListingRepository listings;
    private final ListingDeleter deleter;
    private final ListingEraser eraser;
    private final PhotoStorage photos;
    private final DeletionMode mode;
    private final JournalWriter journal;

    public DeleteDuplicate(VintedSession session, ShowDuplicates showDuplicates, ListingRepository listings,
                           ListingDeleter deleter, ListingEraser eraser, PhotoStorage photos, DeletionMode mode,
                           JournalWriter journal) {
        this.session = session;
        this.showDuplicates = showDuplicates;
        this.listings = listings;
        this.deleter = deleter;
        this.eraser = eraser;
        this.photos = photos;
        this.mode = mode;
        this.journal = journal;
    }

    /**
     * An unknown listing is not an action: nothing is journaled. Always the member's click: the write cadence does not
     * apply, and the deletion does not count in the run's (US-49).
     */
    public DuplicateDeletionReport delete(VintedListingId id) {
        Listing duplicate = listings.find(id).orElseThrow(() -> new ListingNotFound(id));
        return journal.journaled(JournaledAction.DUPLICATE_DELETION, id, () -> deleteNow(duplicate),
                DeleteDuplicate::outcomeOf, Optional.of("DELETION_FAILED"));
    }

    private static ActionOutcome outcomeOf(DuplicateDeletionReport report) {
        return switch (report.outcome()) {
            case DELETED, ALREADY_GONE -> ActionOutcome.SUCCEEDED;
            case DRY_RUN_PASSED -> ActionOutcome.DRY_RUN;
            case UNCONFIRMED -> ActionOutcome.IN_DOUBT;
        };
    }

    private DuplicateDeletionReport deleteNow(Listing duplicate) {
        VintedListingId id = duplicate.id();
        showDuplicates.show().requireDeletable(id);
        if (!session.start(BrowserAction.DELETING, id)) {
            throw new BrowserBusy();
        }
        try {
            // Checked again under the lock: another action may have given the listing a history meanwhile.
            List<Listing> twins = showDuplicates.show().requireDeletable(id);
            VintedListingId twin = confirmedTwin(duplicate, twins);
            ListingLink link = new ListingLink(id, duplicate.url());
            if (deleter.show(link).isEmpty()) {
                erase(id);
                return new DuplicateDeletionReport(DuplicateDeletionOutcome.ALREADY_GONE, twin);
            }
            return new DuplicateDeletionReport(deleteOnVinted(duplicate, link), twin);
        } finally {
            session.finish(id);
        }
    }

    /** The first twin, by identifier, that Vinted shows on sale with the title and the price of the duplicate. */
    private VintedListingId confirmedTwin(Listing duplicate, List<Listing> twins) {
        for (Listing twin : twins) {
            Optional<ScannedListing> read = deleter.show(new ListingLink(twin.id(), twin.url()));
            if (read.filter(shown -> ListingDifference.between(duplicate.title(), duplicate.price(), shown).isEmpty())
                    .isPresent()) {
                return twin.id();
            }
        }
        throw new TwinNotConfirmed(duplicate.id());
    }

    private DuplicateDeletionOutcome deleteOnVinted(Listing duplicate, ListingLink link) {
        Visit visit = new Visit(duplicate);
        Optional<ScannedListing> shownAfterwards;
        try {
            shownAfterwards = deleter.delete(link, visit);
        } catch (RuntimeException failure) {
            if (!visit.confirming) {
                throw failure;
            }
            // Past the confirmation, the deletion may be done: a new request reads the page before any click.
            LOG.warn("Deletion of duplicate {} failed after the confirmation", duplicate.id().value(), failure);
            return DuplicateDeletionOutcome.UNCONFIRMED;
        }
        if (visit.dryRunPassed) {
            return DuplicateDeletionOutcome.DRY_RUN_PASSED;
        }
        if (shownAfterwards.isPresent()) {
            return DuplicateDeletionOutcome.UNCONFIRMED;
        }
        try {
            erase(duplicate.id());
        } catch (RuntimeException failure) {
            // Deleted on Vinted but still in the tool: a new request finds it gone and erases it without a click.
            LOG.error("Duplicate {} deleted on Vinted but not erased", duplicate.id().value(), failure);
            return DuplicateDeletionOutcome.UNCONFIRMED;
        }
        return DuplicateDeletionOutcome.DELETED;
    }

    /** Orphan files are harmless: a failure to remove them is only logged. */
    private void erase(VintedListingId id) {
        eraser.erase(id);
        try {
            photos.removeAllBut(id, List.of());
        } catch (RuntimeException failure) {
            LOG.error("Photo files of erased listing {} not removed", id.value(), failure);
        }
    }

    /** Compares the duplicate on its page with its last scan, before any click. */
    private final class Visit implements DeletionVisit {

        private final Listing duplicate;
        private boolean dryRunPassed;
        private boolean confirming;

        private Visit(Listing duplicate) {
            this.duplicate = duplicate;
        }

        @Override
        public boolean agreesToDelete(ScannedListing read) {
            List<ListingDifference> differences =
                    ListingDifference.between(duplicate.title(), duplicate.price(), read);
            if (!differences.isEmpty()) {
                throw new ListingChanged(differences);
            }
            if (mode == DeletionMode.DRY_RUN) {
                dryRunPassed = true;
                return false;
            }
            return true;
        }

        @Override
        public void aboutToConfirm() {
            confirming = true;
        }
    }
}
