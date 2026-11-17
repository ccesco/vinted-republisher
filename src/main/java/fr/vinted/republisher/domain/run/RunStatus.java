package fr.vinted.republisher.domain.run;

/** Where the automatic run stands: none since startup, relaunching, waiting for a date, finished or stopped. */
public enum RunStatus {
    IDLE,
    RUNNING,
    WAITING,
    FINISHED,
    STOPPED
}
