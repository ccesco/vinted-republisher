package fr.vinted.republisher.domain.run;

import fr.vinted.republisher.domain.cadence.VintedWrite;
import fr.vinted.republisher.domain.deletion.DeletionMode;
import fr.vinted.republisher.domain.relaunch.RelaunchAction;
import fr.vinted.republisher.domain.republication.RepublicationMode;

import java.util.Objects;
import java.util.Optional;

/** Whether deletions and republications write on Vinted for real or in dry run. */
public record WriteModes(DeletionMode deletion, RepublicationMode republication) {

    public WriteModes {
        Objects.requireNonNull(deletion);
        Objects.requireNonNull(republication);
    }

    /** The write the action makes on Vinted for real; empty for a check or a dry run. */
    Optional<VintedWrite> realWriteOf(RelaunchAction action) {
        return switch (action) {
            case DELETION -> deletion == DeletionMode.REAL ? Optional.of(VintedWrite.DELETION) : Optional.empty();
            case REPUBLICATION -> republication == RepublicationMode.REAL
                    ? Optional.of(VintedWrite.REPUBLICATION) : Optional.empty();
            case DELETION_RECHECK, REPUBLICATION_VERIFICATION -> Optional.empty();
        };
    }

    boolean dryRun(RelaunchAction action) {
        return switch (action) {
            case DELETION -> deletion == DeletionMode.DRY_RUN;
            case REPUBLICATION -> republication == RepublicationMode.DRY_RUN;
            case DELETION_RECHECK, REPUBLICATION_VERIFICATION -> false;
        };
    }
}
