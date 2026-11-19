package fr.vinted.republisher.domain.run;

import fr.vinted.republisher.domain.inventory.ListingStage;

import java.util.List;

/**
 * The stages a worker of the run takes, in the order it takes them (US-46). The sequential worker takes them all, in
 * the run's order (US-19); the verification worker helps with deletion rechecks once it has no republication to
 * verify; writes stay with their own worker.
 */
public enum RunWorkerKind {

    ALL_ACTIONS(List.of(ListingStage.REPUBLICATION_TO_CHECK, ListingStage.DELETION_TO_CHECK,
            ListingStage.TO_REPUBLISH, ListingStage.TO_DELETE, ListingStage.WAITING)),
    REPUBLICATION(List.of(ListingStage.TO_REPUBLISH, ListingStage.WAITING)),
    DELETION(List.of(ListingStage.DELETION_TO_CHECK, ListingStage.TO_DELETE)),
    VERIFICATION(List.of(ListingStage.REPUBLICATION_TO_CHECK, ListingStage.DELETION_TO_CHECK));

    private final List<ListingStage> stages;

    RunWorkerKind(List<ListingStage> stages) {
        this.stages = stages;
    }

    public List<ListingStage> stages() {
        return stages;
    }
}
