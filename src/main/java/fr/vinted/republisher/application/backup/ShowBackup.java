package fr.vinted.republisher.application.backup;

import fr.vinted.republisher.domain.backup.BackupOverview;
import fr.vinted.republisher.domain.backup.LastBackupProblem;
import fr.vinted.republisher.domain.backup.ListingBackup;
import fr.vinted.republisher.domain.backup.ListingBackupRepository;
import fr.vinted.republisher.domain.backup.WardrobeBackup;
import fr.vinted.republisher.domain.backup.WardrobeBackupRepository;
import fr.vinted.republisher.domain.session.BrowserAction;
import fr.vinted.republisher.domain.session.VintedSession;

import java.util.stream.Collectors;

public class ShowBackup {

    private final VintedSession session;
    private final LastBackupProblem lastProblem;
    private final WardrobeBackupRepository backups;
    private final ListingBackupRepository listingBackups;

    public ShowBackup(VintedSession session, LastBackupProblem lastProblem, WardrobeBackupRepository backups,
                      ListingBackupRepository listingBackups) {
        this.session = session;
        this.lastProblem = lastProblem;
        this.backups = backups;
        this.listingBackups = listingBackups;
    }

    /** The backup is looked at first: a page told it is over must find its result in the dates. */
    public BackupOverview show() {
        boolean backupInProgress = session.overview().currentAction()
                .filter(BrowserAction.BACKING_UP::equals).isPresent();
        return new BackupOverview(
                backupInProgress,
                backups.findLastFinished().flatMap(WardrobeBackup::finishedAt),
                lastProblem.current(),
                listingBackups.findAll().stream().collect(Collectors.toMap(ListingBackup::id, ListingBackup::savedAt)));
    }
}
