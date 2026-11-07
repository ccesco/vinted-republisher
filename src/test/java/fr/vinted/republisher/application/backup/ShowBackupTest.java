package fr.vinted.republisher.application.backup;

import fr.vinted.republisher.domain.backup.BackupOverview;
import fr.vinted.republisher.domain.backup.BackupProblem;
import fr.vinted.republisher.domain.backup.LastBackupProblem;
import fr.vinted.republisher.domain.backup.ListingBackup;
import fr.vinted.republisher.domain.backup.WardrobeBackup;
import fr.vinted.republisher.domain.session.BrowserAction;
import fr.vinted.republisher.domain.session.VintedSession;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import fr.vinted.republisher.fakes.InMemoryListingBackupRepository;
import fr.vinted.republisher.fakes.InMemoryWardrobeBackupRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static fr.vinted.republisher.domain.backup.BackupFixtures.aContent;
import static org.assertj.core.api.Assertions.assertThat;

class ShowBackupTest {

    private static final Instant STARTED_AT = Instant.parse("2026-11-07T09:30:00Z");
    private static final Instant FINISHED_AT = Instant.parse("2026-11-07T10:00:00Z");

    private final VintedSession session = new VintedSession();
    private final LastBackupProblem lastProblem = new LastBackupProblem();
    private final InMemoryWardrobeBackupRepository backups = new InMemoryWardrobeBackupRepository();
    private final InMemoryListingBackupRepository listingBackups = new InMemoryListingBackupRepository();
    private final ShowBackup showBackup = new ShowBackup(session, lastProblem, backups, listingBackups);

    @Test
    void nothing_is_known_before_the_first_backup() {
        assertThat(showBackup.show()).isEqualTo(new BackupOverview(false, Optional.empty(), Optional.empty(), Map.of()));
    }

    @Test
    void date_of_the_last_backup_is_the_end_of_the_last_finished_one() {
        backups.save(WardrobeBackup.startedAt(STARTED_AT).finished(FINISHED_AT));
        backups.save(WardrobeBackup.startedAt(FINISHED_AT));

        assertThat(showBackup.show().lastBackupAt()).contains(FINISHED_AT);
    }

    @Test
    void tells_when_each_listing_was_saved() {
        listingBackups.save(new ListingBackup(aContent(1), STARTED_AT));

        assertThat(showBackup.show().savedAt()).isEqualTo(Map.of(new VintedListingId(1), STARTED_AT));
    }

    @Test
    void tells_a_backup_is_in_progress_and_not_a_scan() {
        session.start(BrowserAction.BACKING_UP);
        assertThat(showBackup.show().backupInProgress()).isTrue();
        session.finish();

        session.start(BrowserAction.SCANNING);
        assertThat(showBackup.show().backupInProgress()).isFalse();
    }

    @Test
    void shows_the_problem_of_the_last_backup() {
        lastProblem.report(BackupProblem.SESSION_DISCONNECTED);

        assertThat(showBackup.show().problem()).contains(BackupProblem.SESSION_DISCONNECTED);
    }
}
