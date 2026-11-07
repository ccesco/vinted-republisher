package fr.vinted.republisher.domain.backup;

import java.util.Optional;

/**
 * What went wrong with the last backup, if anything: shown until the next backup, not kept across restarts.
 * Synchronized because a backup runs in the background while the web page reads it.
 */
public final class LastBackupProblem {

    private Optional<BackupProblem> problem = Optional.empty();

    public synchronized void clear() {
        problem = Optional.empty();
    }

    public synchronized void report(BackupProblem reason) {
        problem = Optional.of(reason);
    }

    public synchronized Optional<BackupProblem> current() {
        return problem;
    }
}
