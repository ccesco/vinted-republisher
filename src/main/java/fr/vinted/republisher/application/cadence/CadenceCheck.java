package fr.vinted.republisher.application.cadence;

import fr.vinted.republisher.domain.cadence.WriteCadence;
import fr.vinted.republisher.domain.cadence.VintedWrite;
import fr.vinted.republisher.domain.deletion.ListingDeletionRepository;
import fr.vinted.republisher.domain.republication.ListingRepublicationRepository;

import java.time.Clock;
import java.time.Instant;

/**
 * Checks the write cadence against the attempts recorded in the database. Called under the browser lock, so that two
 * requests cannot pass it together.
 */
public class CadenceCheck {

    private final ListingDeletionRepository deletions;
    private final ListingRepublicationRepository republications;
    private final WriteCadence cadence;
    private final Clock clock;

    public CadenceCheck(ListingDeletionRepository deletions, ListingRepublicationRepository republications,
                        WriteCadence cadence, Clock clock) {
        this.deletions = deletions;
        this.republications = republications;
        this.cadence = cadence;
        this.clock = clock;
    }

    /** Throws {@code CadenceLimitReached} when the write is not allowed now. */
    public void check(VintedWrite write) {
        Instant now = clock.instant();
        Instant since = cadence.windowStart(now);
        cadence.check(write, deletions.findAttemptedSince(since), republications.findAttemptedSince(since), now);
    }
}
