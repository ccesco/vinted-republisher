package fr.vinted.republisher.domain.run;

/** Where the automatic run stands: none since startup, relaunching, waiting for a date, paused, finished or stopped. */
public enum RunStatus {
    IDLE,
    RUNNING,
    WAITING,
    PAUSED,
    FINISHED,
    STOPPED
}
