package fr.vinted.republisher.adapter.in.web;

import fr.vinted.republisher.application.archive.DownloadDataArchive;
import fr.vinted.republisher.domain.archive.DataArchiveWriter;
import fr.vinted.republisher.domain.session.BrowserAction;
import fr.vinted.republisher.domain.session.VintedSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** US-26: the API answers as described by the OpenAPI contract. */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@WebMvcTest(DataArchiveApiController.class)
class DataArchiveApiControllerTest {

    private static final byte[] ZIP = {'P', 'K', 5, 6};

    @Autowired
    private MockMvcTester mvc;
    @Autowired
    private VintedSession session;
    @Autowired
    private SwitchableWriter writer;

    @Test
    void sends_the_whole_archive_as_an_attachment() {
        MvcTestResult result = mvc.get().uri("/api/data-archive").exchange();

        assertThat(result).hasStatusOk().hasContentType("application/zip")
                .hasHeader("Content-Disposition",
                        "attachment; filename=\"vinted-republisher-archive-2026-11-17T14-05-09.zip\"")
                .hasHeader("Content-Length", "4");
        assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(ZIP);
        assertThat(archivesInTemporaryFolder()).isEmpty();
    }

    @Test
    void refuses_while_browser_busy() {
        session.start(BrowserAction.SCANNING);

        MvcTestResult result = mvc.get().uri("/api/data-archive").exchange();

        assertThat(result).hasStatus(409).bodyJson().isStrictlyEqualTo("{\"reason\":\"BROWSER_BUSY\"}");
        assertThat(archivesInTemporaryFolder()).isEmpty();
    }

    @Test
    void failure_sends_no_partial_zip_and_deletes_the_temporary_file() {
        writer.failing = true;

        MvcTestResult result = mvc.get().uri("/api/data-archive").exchange();

        assertThat(result).hasStatus(500).bodyJson().isStrictlyEqualTo("{\"reason\":\"ARCHIVE_FAILED\"}");
        assertThat(archivesInTemporaryFolder()).isEmpty();
    }

    private static Set<Path> archivesInTemporaryFolder() {
        try (Stream<Path> files = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            return files.filter(file -> file.getFileName().toString().startsWith("vinted-republisher-archive"))
                    .collect(Collectors.toSet());
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    static final class SwitchableWriter implements DataArchiveWriter {

        private boolean failing;

        @Override
        public void write(Instant createdAt, OutputStream archive) {
            try {
                archive.write(ZIP, 0, failing ? 2 : ZIP.length);
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
            if (failing) {
                throw new UncheckedIOException(new IOException("Photo unreadable"));
            }
        }
    }

    @TestConfiguration
    static class Fakes {

        @Bean
        VintedSession session() {
            return new VintedSession();
        }

        @Bean
        SwitchableWriter writer() {
            return new SwitchableWriter();
        }

        @Bean
        DownloadDataArchive downloadDataArchive(VintedSession session, SwitchableWriter writer) {
            return new DownloadDataArchive(session, writer,
                    Clock.fixed(Instant.parse("2026-11-17T13:05:09Z"), ZoneOffset.UTC), ZoneId.of("Europe/Paris"));
        }
    }
}
