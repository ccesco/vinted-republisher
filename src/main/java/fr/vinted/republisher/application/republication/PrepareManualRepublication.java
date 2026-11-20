package fr.vinted.republisher.application.republication;

import fr.vinted.republisher.application.journal.JournalWriter;
import fr.vinted.republisher.application.republication.RepublicationChecks.RepublishableBackup;
import fr.vinted.republisher.domain.cadence.WriteOrigin;
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
 * Fills the listing form from the backup for the member to complete and publish, when the member wants it. Never
 * clicks Publish.
 */
public class PrepareManualRepublication {

    /** Journal reason of a prepared manual republication: in doubt until the member's publication is verified. */
    private static final String PREPARED = "MANUAL_REPUBLICATION_PREPARED";

    private final VintedSession session;
    private final RepublicationChecks checks;
    private final ListingRepublicationRepository republications;
    private final ListingPublisher publisher;
    private final RepublicationMode mode;
    private final Clock clock;
    private final ShowListingRepublication showListingRepublication;
    private final JournalWriter journal;

    public PrepareManualRepublication(VintedSession session, RepublicationChecks checks,
                                      ListingRepublicationRepository republications, ListingPublisher publisher,
                                      RepublicationMode mode, Clock clock,
                                      ShowListingRepublication showListingRepublication, JournalWriter journal) {
        this.session = session;
        this.checks = checks;
        this.republications = republications;
        this.publisher = publisher;
        this.mode = mode;
        this.clock = clock;
        this.showListingRepublication = showListingRepublication;
        this.journal = journal;
    }

    /**
     * {@code seen} is the date of the backup the member saw. Checked as a republication, except that neither the
     * republication date nor the write cadence holds it back; then refused in dry-run, before Chrome opens. Once the
     * form is filled, a republication in error is recorded, by the member's hand: it does not count in the
     * run's cadence (US-49), and the member's publication is then verified or validated as any other (US-13).
     */
    public ManualRepublicationReport prepare(VintedListingId id, Instant seen) {
        return journal.journaled(JournaledAction.REPUBLICATION, id, () -> prepareNow(id, seen),
                report -> ActionOutcome.IN_DOUBT, Optional.of(PREPARED), Optional.of("REPUBLICATION_FAILED"));
    }

    private ManualRepublicationReport prepareNow(VintedListingId id, Instant seen) {
        RepublishableBackup republishable = checks.checkWithoutDelay(id, seen);
        if (mode == RepublicationMode.DRY_RUN) {
            throw new ManualRepublicationInDryRun();
        }
        if (!session.start(BrowserAction.REPUBLISHING, id)) {
            throw new BrowserBusy();
        }
        try {
            List<UnfilledField> unfilled = publisher.prepare(republishable.backup().content(), republishable.photos());
            republications.save(ListingRepublication.attempted(republishable.backup(), clock.instant(),
                    WriteOrigin.MANUAL));
            return new ManualRepublicationReport(showListingRepublication.report(id, false), unfilled);
        } finally {
            session.finish(id);
        }
    }
}
