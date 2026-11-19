package fr.vinted.republisher.domain.run;

/**
 * How the automatic run relaunches listings (US-46): one at a time, or by three workers each assigned to its own
 * kind of action, each in its own tab of Chrome.
 */
public enum ParallelismMode {

    SEQUENTIAL(1),
    WORKERS(3);

    private final int listingActions;

    ParallelismMode(int listingActions) {
        this.listingActions = listingActions;
    }

    /** How many listing actions may run at the same time. */
    public int listingActions() {
        return listingActions;
    }
}
