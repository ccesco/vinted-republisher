package fr.vinted.republisher.application.republication;

import fr.vinted.republisher.application.cadence.CadenceCheck;
import fr.vinted.republisher.application.cadence.WriteReservation;
import fr.vinted.republisher.application.journal.JournalWriter;
import fr.vinted.republisher.application.republication.RepublicationChecks.RepublishableBackup;
import fr.vinted.republisher.domain.backup.ListingBackup;
import fr.vinted.republisher.domain.cadence.VintedWrite;
import fr.vinted.republisher.domain.journal.ActionOutcome;
import fr.vinted.republisher.domain.journal.JournaledAction;
import fr.vinted.republisher.domain.republication.ListingPublisher;
import fr.vinted.republisher.domain.republication.ListingRepublication;
import fr.vinted.republisher.domain.republication.ListingRepublicationRepository;
import fr.vinted.republisher.domain.republication.RepublicationMode;
import fr.vinted.republisher.domain.republication.RepublicationOutcome;
import fr.vinted.republisher.domain.republication.RepublicationReport;
import fr.vinted.republisher.domain.republication.RepublicationVisit;
import fr.vinted.republisher.domain.run.RunWaiting;
import fr.vinted.republisher.domain.session.BrowserAction;
import fr.vinted.republisher.domain.session.BrowserBusy;
import fr.vinted.republisher.domain.session.VintedSession;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.ListingRepository;
import fr.vinted.republisher.domain.wardrobe.ScannedListing;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Puts a deleted listing on sale again from its backup, as the member asked, once its republication date is reached. */
public class RepublishListing {

    private static final Logger LOG = LoggerFactory.getLogger(RepublishListing.class);

    private final VintedSession session;
    private final RepublicationChecks checks;
    private final CadenceCheck cadence;
    private final ListingRepublicationRepository republications;
    private final ListingPublisher publisher;
    private final NewListingSearch newListingSearch;
    private final ListingRepository listings;
    private final RepublicationMode mode;
    private final Clock clock;
    private final JournalWriter journal;
    private final ShowListingRepublication showListingRepublication;
    private final RunWaiting waiting;
    private final Duration proofDelay;

    /** {@code proofDelay}: how long Vinted is given, once Publish is clicked, to show the new listing in the wardrobe. */
    public RepublishListing(VintedSession session, RepublicationChecks checks, CadenceCheck cadence,
                            ListingRepublicationRepository republications, ListingPublisher publisher,
                            NewListingSearch newListingSearch, ListingRepository listings, RepublicationMode mode,
                            Clock clock, ShowListingRepublication showListingRepublication,
                            JournalWriter journal, RunWaiting waiting, Duration proofDelay) {
        this.waiting = waiting;
        this.proofDelay = proofDelay;
        this.session = session;
        this.checks = checks;
        this.cadence = cadence;
        this.republications = republications;
        this.publisher = publisher;
        this.newListingSearch = newListingSearch;
        this.listings = listings;
        this.mode = mode;
        this.clock = clock;
        this.journal = journal;
        this.showListingRepublication = showListingRepublication;
    }

    /** From when the cadence allows a real republication; empty in dry run, where nothing is published. */
    public Optional<Instant> realWriteAllowedAt() {
        return mode == RepublicationMode.REAL
                ? Optional.of(cadence.allowedAt(VintedWrite.REPUBLICATION)) : Optional.empty();
    }

    /**
     * {@code seen} is the date of the backup the member saw. Every check is made before Chrome opens; past the click on
     * Publish, any failure leaves the republication in error rather than risk a duplicate. The write cadence is
     * checked under the browser lock, and only for a real republication.
     */
    public RepublicationReport republish(VintedListingId id, Instant seen) {
        return journal.journaled(JournaledAction.REPUBLICATION, id, () -> republishNow(id, seen),
                RepublishListing::outcomeOf, Optional.of("REPUBLICATION_FAILED"));
    }

    /** A dry run publishes nothing; otherwise the attempt just made is found in the wardrobe or in error. */
    private static ActionOutcome outcomeOf(RepublicationReport report) {
        if (report.dryRunPassed()) {
            return ActionOutcome.DRY_RUN;
        }
        return report.lastRepublication().filter(republication -> republication.outcome() == RepublicationOutcome.PUBLISHED)
                .isPresent() ? ActionOutcome.SUCCEEDED : ActionOutcome.IN_DOUBT;
    }

    private RepublicationReport republishNow(VintedListingId id, Instant seen) {
        RepublishableBackup republishable = checks.check(id, seen);
        if (!session.start(BrowserAction.REPUBLISHING, id)) {
            throw new BrowserBusy();
        }
        Attempt attempt = new Attempt(republishable.backup());
        Optional<WriteReservation> reservation = Optional.empty();
        try {
            if (mode == RepublicationMode.REAL) {
                reservation = Optional.of(cadence.reserve(VintedWrite.REPUBLICATION));
            }
            publisher.publish(republishable.backup().content(), republishable.photos(), attempt);
            attempt.started().ifPresent(republication -> prove(republication, republishable.backup()));
        } catch (RuntimeException failure) {
            if (attempt.started().isEmpty()) {
                throw failure;
            }
            LOG.warn("Republication of listing {} failed after Publish was clicked", id.value(), failure);
        } finally {
            reservation.ifPresent(cadence::release);
            session.finish(id);
        }
        return showListingRepublication.report(id, attempt.dryRunPassed);
    }

    private void prove(ListingRepublication republication, ListingBackup backup) {
        waiting.until(republication.attemptedAt().plus(proofDelay), () -> false);
        Optional<ScannedListing> found = newListingSearch.find(backup.content());
        if (found.isEmpty()) {
            LOG.warn("Republication of listing {}: no single new listing found in the wardrobe", backup.id().value());
            return;
        }
        Instant provenAt = clock.instant();
        listings.saveAll(List.of(Listing.seen(found.get(), provenAt)));
        republications.save(republication.published(found.get().id(), provenAt));
    }

    /** Records the attempt right before Publish is clicked, unless in dry-run. */
    private final class Attempt implements RepublicationVisit {

        private final ListingBackup backup;
        private Optional<ListingRepublication> started = Optional.empty();
        private boolean dryRunPassed;

        private Attempt(ListingBackup backup) {
            this.backup = backup;
        }

        @Override
        public boolean readyToPublish() {
            if (mode == RepublicationMode.DRY_RUN) {
                dryRunPassed = true;
                return false;
            }
            ListingRepublication republication = ListingRepublication.attempted(backup, clock.instant());
            republications.save(republication);
            started = Optional.of(republication);
            return true;
        }

        private Optional<ListingRepublication> started() {
            return started;
        }
    }
}
