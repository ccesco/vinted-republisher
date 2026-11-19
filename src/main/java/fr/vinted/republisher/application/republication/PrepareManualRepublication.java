package fr.vinted.republisher.application.republication;

import fr.vinted.republisher.application.cadence.CadenceCheck;
import fr.vinted.republisher.application.cadence.WriteReservation;
import fr.vinted.republisher.application.journal.JournalWriter;
import fr.vinted.republisher.application.republication.RepublicationChecks.RepublishableBackup;
import fr.vinted.republisher.domain.cadence.VintedWrite;
import fr.vinted.republisher.domain.journal.ActionOutcome;
import fr.vinted.republisher.domain.journal.JournaledAction;
import fr.vinted.republisher.domain.republication.ListingPublisher;
import fr.vinted.republisher.domain.republication.ListingRepublication;
import fr.vinted.republisher.domain.republication.ListingRepublicationRepository;
import fr.vinted.republisher.domain.republication.ManualRepublicationInDryRun;
import fr.vinted.republisher.domain.republication.ManualRepublicationReport;
import fr.vinted.republisher.domain.republication.RepublicationMode;
import fr.vinted.republisher.domain.republication.UnfilledField;
import fr.vinted.republisher.domain.session.BrowserAction;
import fr.vinted.republisher.domain.session.BrowserBusy;
import fr.vinted.republisher.domain.session.VintedSession;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Fills the listing form from the backup for the member to complete and publish, when the automatic republication is
 * refused. Never clicks Publish.
 */
public class PrepareManualRepublication {

    /** Journal reason of a prepared manual republication: in doubt until the member's publication is verified. */
    private static final String PREPARED = "MANUAL_REPUBLICATION_PREPARED";

    private final VintedSession session;
    private final RepublicationChecks checks;
    private final CadenceCheck cadence;
    private final ListingRepublicationRepository republications;
    private final ListingPublisher publisher;
    private final RepublicationMode mode;
    private final Clock clock;
    private final ShowListingRepublication showListingRepublication;
    private final JournalWriter journal;

    public PrepareManualRepublication(VintedSession session, RepublicationChecks checks, CadenceCheck cadence,
                                      ListingRepublicationRepository republications, ListingPublisher publisher,
                                      RepublicationMode mode, Clock clock,
                                      ShowListingRepublication showListingRepublication, JournalWriter journal) {
        this.session = session;
        this.checks = checks;
        this.cadence = cadence;
        this.republications = republications;
        this.publisher = publisher;
        this.mode = mode;
        this.clock = clock;
        this.showListingRepublication = showListingRepublication;
        this.journal = journal;
    }

    /**
     * {@code seen} is the date of the backup the member saw. Checked as a republication, then refused in dry-run,
     * then the cadence, before Chrome opens. Once the form is filled, a republication in error is recorded: the
     * member's publication is then verified or validated as any other (US-13).
     */
    public ManualRepublicationReport prepare(VintedListingId id, Instant seen) {
        return journal.journaled(JournaledAction.REPUBLICATION, id, () -> prepareNow(id, seen),
                report -> ActionOutcome.IN_DOUBT, Optional.of(PREPARED), Optional.of("REPUBLICATION_FAILED"));
    }

    private ManualRepublicationReport prepareNow(VintedListingId id, Instant seen) {
        RepublishableBackup republishable = checks.check(id, seen);
        if (mode == RepublicationMode.DRY_RUN) {
            throw new ManualRepublicationInDryRun();
        }
        if (!session.start(BrowserAction.REPUBLISHING, id)) {
            throw new BrowserBusy();
        }
        Optional<WriteReservation> reservation = Optional.empty();
        try {
            reservation = Optional.of(cadence.reserve(VintedWrite.REPUBLICATION));
            List<UnfilledField> unfilled = publisher.prepare(republishable.backup().content(), republishable.photos());
            republications.save(ListingRepublication.attempted(republishable.backup(), clock.instant()));
            return new ManualRepublicationReport(showListingRepublication.report(id, false), unfilled);
        } finally {
            reservation.ifPresent(cadence::release);
            session.finish(id);
        }
    }
}
