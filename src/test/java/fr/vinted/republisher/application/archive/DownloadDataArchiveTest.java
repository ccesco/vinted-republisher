package fr.vinted.republisher.application.archive;

import fr.vinted.republisher.domain.archive.DataArchive;
import fr.vinted.republisher.domain.archive.DataArchiveWriter;
import fr.vinted.republisher.domain.session.BrowserAction;
import fr.vinted.republisher.domain.session.BrowserBusy;
import fr.vinted.republisher.domain.session.VintedSession;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class DownloadDataArchiveTest {

    private static final Instant NOW = Instant.parse("2026-11-17T13:05:09Z");

    private final VintedSession session = new VintedSession();
    private final RecordingWriter writer = new RecordingWriter();
    private final DownloadDataArchive download = new DownloadDataArchive(session, writer,
            Clock.fixed(NOW, ZoneOffset.UTC), ZoneId.of("Europe/Paris"));

    @Test
    void writes_the_archive_while_holding_the_browser() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        DataArchive archive = download.download(out);

        assertThat(archive).isEqualTo(new DataArchive(NOW, "vinted-republisher-archive-2026-11-17T14-05-09.zip"));
        assertThat(out.toByteArray()).isEqualTo(new byte[]{1});
        assertThat(writer.actionsWhileWriting).containsExactly(Optional.of(BrowserAction.ARCHIVING));
        assertThat(session.overview().currentAction()).isEmpty();
    }

    @Test
    void is_refused_while_another_browser_action_is_in_progress() {
        session.start(BrowserAction.BACKING_UP);

        Throwable refusal = catchThrowable(() -> download.download(new ByteArrayOutputStream()));

        assertThat(refusal).isInstanceOf(BrowserBusy.class);
        assertThat(writer.actionsWhileWriting).isEmpty();
        assertThat(session.overview().currentAction()).contains(BrowserAction.BACKING_UP);
    }

    @Test
    void frees_the_browser_when_the_archive_fails() {
        writer.failing = true;

        Throwable failure = catchThrowable(() -> download.download(new ByteArrayOutputStream()));

        assertThat(failure).isInstanceOf(UncheckedIOException.class);
        assertThat(session.overview().currentAction()).isEmpty();
    }

    private final class RecordingWriter implements DataArchiveWriter {

        private final List<Optional<BrowserAction>> actionsWhileWriting = new ArrayList<>();
        private boolean failing;

        @Override
        public void write(Instant createdAt, OutputStream archive) {
            actionsWhileWriting.add(session.overview().currentAction());
            if (failing) {
                throw new UncheckedIOException(new IOException("Disk full"));
            }
            try {
                archive.write(1);
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
        }
    }
}
