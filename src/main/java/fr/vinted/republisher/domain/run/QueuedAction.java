package fr.vinted.republisher.domain.run;

import fr.vinted.republisher.domain.relaunch.RelaunchAction;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.time.Instant;
import java.util.Objects;

/** An action the run would launch, at the earliest at {@code earliestAt}; a dry-run write touches nothing on Vinted. */
public record QueuedAction(VintedListingId listingId, String title, RelaunchAction action, Instant earliestAt,
                           boolean dryRun) {

    public QueuedAction {
        Objects.requireNonNull(listingId);
        Objects.requireNonNull(title);
        Objects.requireNonNull(action);
        Objects.requireNonNull(earliestAt);
    }
}
