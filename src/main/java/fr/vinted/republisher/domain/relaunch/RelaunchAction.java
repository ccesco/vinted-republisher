package fr.vinted.republisher.domain.relaunch;

import fr.vinted.republisher.domain.inventory.ListingStage;

import java.util.Optional;

/** What relaunching a listing does, deduced from its stage: one step, by the use case of that step. */
public enum RelaunchAction {
    DELETION,
    DELETION_RECHECK,
    REPUBLICATION,
    REPUBLICATION_VERIFICATION;

    /** Empty for a stage that waits for the member, for time, or for nothing. */
    public static Optional<RelaunchAction> after(ListingStage stage) {
        return switch (stage) {
            case TO_DELETE -> Optional.of(DELETION);
            case DELETION_TO_CHECK -> Optional.of(DELETION_RECHECK);
            case TO_REPUBLISH -> Optional.of(REPUBLICATION);
            case REPUBLICATION_TO_CHECK -> Optional.of(REPUBLICATION_VERIFICATION);
            case TO_BACK_UP, TO_VALIDATE, VERIFICATION_FAILED, WAITING, GONE, REPUBLISHED -> Optional.empty();
        };
    }
}
