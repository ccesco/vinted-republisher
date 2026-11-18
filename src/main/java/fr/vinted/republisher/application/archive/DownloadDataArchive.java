package fr.vinted.republisher.application.archive;

import fr.vinted.republisher.domain.archive.DataArchive;
import fr.vinted.republisher.domain.archive.DataArchiveWriter;
import fr.vinted.republisher.domain.session.BrowserAction;
import fr.vinted.republisher.domain.session.BrowserBusy;
import fr.vinted.republisher.domain.session.VintedSession;

import java.io.OutputStream;
import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Writes a complete copy of the tool's data for the member to keep elsewhere. Holds the browser while writing:
 * only browser actions write photos and backups, so they wait and the copy stays consistent.
 */
public class DownloadDataArchive {

    private final VintedSession session;
    private final DataArchiveWriter writer;
    private final Clock clock;
    private final ZoneId memberZone;

    public DownloadDataArchive(VintedSession session, DataArchiveWriter writer, Clock clock, ZoneId memberZone) {
        this.session = session;
        this.writer = writer;
        this.clock = clock;
        this.memberZone = memberZone;
    }

    public DataArchive download(OutputStream archive) {
        if (!session.start(BrowserAction.ARCHIVING)) {
            throw new BrowserBusy();
        }
        try {
            DataArchive dataArchive = DataArchive.createdAt(ZonedDateTime.now(clock).withZoneSameInstant(memberZone));
            writer.write(dataArchive.createdAt(), archive);
            return dataArchive;
        } finally {
            session.finish();
        }
    }
}
