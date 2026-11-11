package fr.vinted.republisher.application.backup;

import fr.vinted.republisher.domain.backup.BackupPace;
import fr.vinted.republisher.domain.backup.BackupProblem;
import fr.vinted.republisher.domain.backup.LastBackupProblem;
import fr.vinted.republisher.domain.backup.ListingBackup;
import fr.vinted.republisher.domain.backup.ListingBackupRepository;
import fr.vinted.republisher.domain.backup.ListingContentReader;
import fr.vinted.republisher.domain.backup.ListingReading;
import fr.vinted.republisher.domain.backup.PhotoStorage;
import fr.vinted.republisher.domain.backup.ListingUnreadable;
import fr.vinted.republisher.domain.backup.WardrobeBackup;
import fr.vinted.republisher.domain.backup.WardrobeBackupRepository;
import fr.vinted.republisher.domain.session.BrowserAction;
import fr.vinted.republisher.domain.session.BrowserUnavailable;
import fr.vinted.republisher.domain.session.SessionDisconnected;
import fr.vinted.republisher.domain.session.SessionStatus;
import fr.vinted.republisher.domain.session.VintedSession;
import fr.vinted.republisher.domain.session.VintedUnreachable;
import fr.vinted.republisher.domain.wardrobe.ListingRepository;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Saves the content and the photos of each present listing as soon as they are read, so that a failure loses nothing
 * and the next backup resumes. A listing's previous backup is only replaced by a complete reading.
 */
public class BackUpWardrobe {

    private static final Logger LOG = LoggerFactory.getLogger(BackUpWardrobe.class);

    private final VintedSession session;
    private final LastBackupProblem lastProblem;
    private final WardrobeBackupRepository backups;
    private final ListingContentReader reader;
    private final ListingRepository listings;
    private final ListingBackupRepository listingBackups;
    private final PhotoStorage photos;
    private final BackupPace pace;
    private final Clock clock;
    private final Executor background;

    public BackUpWardrobe(VintedSession session, LastBackupProblem lastProblem, WardrobeBackupRepository backups,
                          ListingContentReader reader, ListingRepository listings,
                          ListingBackupRepository listingBackups, PhotoStorage photos, BackupPace pace, Clock clock,
                          Executor background) {
        this.session = session;
        this.lastProblem = lastProblem;
        this.backups = backups;
        this.reader = reader;
        this.listings = listings;
        this.listingBackups = listingBackups;
        this.photos = photos;
        this.pace = pace;
        this.clock = clock;
        this.background = background;
    }

    /** Starts right away, so that the backup shows as in progress, then visits Vinted in the background. */
    public void backUp() {
        if (!session.start(BrowserAction.BACKING_UP)) {
            return;
        }
        lastProblem.clear();
        background.execute(this::backUpUntilDone);
    }

    private void backUpUntilDone() {
        try {
            backUpToTheEnd();
            session.finish();
        } catch (SessionDisconnected disconnected) {
            fail(BackupProblem.SESSION_DISCONNECTED, disconnected);
            session.complete(SessionStatus.DISCONNECTED);
        } catch (BrowserUnavailable unavailable) {
            failAndFinish(BackupProblem.BROWSER_UNAVAILABLE, unavailable);
        } catch (VintedUnreachable unreachable) {
            failAndFinish(BackupProblem.VINTED_UNREACHABLE, unreachable);
        } catch (ListingUnreadable unreadable) {
            failAndFinish(BackupProblem.LISTING_UNREADABLE, unreadable);
        } catch (RuntimeException unexpected) {
            // The backup must end whatever happens; the failure is rethrown for the executor to log it.
            lastProblem.report(BackupProblem.BACKUP_FAILED);
            session.finish();
            throw unexpected;
        }
    }

    private void failAndFinish(BackupProblem problem, RuntimeException cause) {
        fail(problem, cause);
        session.finish();
    }

    /** The page only gives the problem: the details are in the logs. */
    private void fail(BackupProblem problem, RuntimeException cause) {
        LOG.warn("Wardrobe backup failed", cause);
        lastProblem.report(problem);
    }

    private void backUpToTheEnd() {
        WardrobeBackup backup = backups.findUnfinished().orElseGet(this::startBackup);
        List<VintedListingId> toOpen = backup.listingsToOpen(listings.findAll(), listingBackups.findAll());
        if (!toOpen.isEmpty()) {
            reader.read(toOpen, pace, this::save);
        }
        backups.save(backup.finished(clock.instant()));
    }

    /**
     * Files first, then the backup that points at them, then the files nothing points at any more: an interruption
     * never leaves a backup without its photos.
     */
    private void save(ListingReading reading) {
        reading.photos().forEach(photo -> photos.store(reading.id(), photo));
        ListingBackup backup = ListingBackup.of(reading, clock.instant());
        listingBackups.save(backup);
        photos.removeAllBut(backup.id(), backup.photos());
    }

    private WardrobeBackup startBackup() {
        WardrobeBackup backup = WardrobeBackup.startedAt(clock.instant());
        backups.save(backup);
        return backup;
    }
}
