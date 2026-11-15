package fr.vinted.republisher.application.backup;

import fr.vinted.republisher.domain.backup.BackupOverview;
import fr.vinted.republisher.domain.backup.LastBackupProblem;
import fr.vinted.republisher.domain.backup.WardrobeBackup;
import fr.vinted.republisher.domain.backup.WardrobeBackupRepository;
import fr.vinted.republisher.domain.session.BrowserAction;
import fr.vinted.republisher.domain.session.VintedSession;

public class ShowBackup {
    private final VintedSession session;
    private final LastBackupProblem lastProblem;
    private final WardrobeBackupRepository backups;

    public ShowBackup(VintedSession session, LastBackupProblem lastProblem, WardrobeBackupRepository backups) {
        this.session = session;
        this.lastProblem = lastProblem;
        this.backups = backups;
    }

    public BackupOverview show() {
        boolean backupInProgress = session.overview().currentAction()
                .filter(BrowserAction.BACKING_UP::equals).isPresent();
        return new BackupOverview(
                backupInProgress,
                backups.findLastFinished().flatMap(WardrobeBackup::finishedAt),
                lastProblem.current());
    }
}
