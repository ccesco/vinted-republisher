package fr.vinted.republisher.domain.run;

/** Relaunching a listing, waiting for a date, with nothing to do, or out of the run (paused or ended). */
public enum RunWorkerState {
    WORKING,
    WAITING,
    IDLE,
    STOPPED
}
