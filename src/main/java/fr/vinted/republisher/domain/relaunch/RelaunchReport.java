package fr.vinted.republisher.domain.relaunch;

import fr.vinted.republisher.domain.inventory.InventoryEntry;

import java.util.Objects;

/** The action a relaunch launched, whether it was a dry run, and the listing read again afterwards. */
public record RelaunchReport(RelaunchAction action, boolean dryRun, InventoryEntry entry) {

    public RelaunchReport {
        Objects.requireNonNull(action);
        Objects.requireNonNull(entry);
    }
}
