package fr.vinted.republisher.domain.run;

import java.util.List;

/**
 * How the automatic run relaunches listings (US-46): one at a time, or by three workers each assigned to its own
 * kind of action, each in its own tab of Chrome.
 */
public enum ParallelismMode {

    SEQUENTIAL(List.of(RunWorkerKind.ALL_ACTIONS)),
    WORKERS(List.of(RunWorkerKind.REPUBLICATION, RunWorkerKind.DELETION, RunWorkerKind.VERIFICATION));

    private final List<RunWorkerKind> workerKinds;

    ParallelismMode(List<RunWorkerKind> workerKinds) {
        this.workerKinds = workerKinds;
    }

    /** The run's workers, in the order the member sees them. */
    public List<RunWorkerKind> workerKinds() {
        return workerKinds;
    }

    /** How many listing actions may run at the same time: one per worker. */
    public int listingActions() {
        return workerKinds.size();
    }
}
