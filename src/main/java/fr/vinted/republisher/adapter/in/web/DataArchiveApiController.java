package fr.vinted.republisher.adapter.in.web;

import fr.vinted.republisher.adapter.in.web.api.DataArchiveApi;
import fr.vinted.republisher.application.archive.DownloadDataArchive;
import fr.vinted.republisher.domain.archive.DataArchive;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Builds the whole archive in a temporary file before sending anything, so that a failure never sends a truncated
 * zip. The file is deleted once sent, or right away when the archive fails.
 */
@RestController
public class DataArchiveApiController implements DataArchiveApi {

    private static final MediaType ZIP = MediaType.parseMediaType("application/zip");

    private final DownloadDataArchive downloadDataArchive;

    public DataArchiveApiController(DownloadDataArchive downloadDataArchive) {
        this.downloadDataArchive = downloadDataArchive;
    }

    @Override
    public ResponseEntity<Resource> downloadDataArchive() {
        Path file = temporaryFile();
        try {
            DataArchive archive;
            try (OutputStream out = Files.newOutputStream(file)) {
                archive = downloadDataArchive.download(out);
            }
            long size = Files.size(file);
            InputStream sent = Files.newInputStream(file, StandardOpenOption.DELETE_ON_CLOSE);
            return ResponseEntity.ok().contentType(ZIP).contentLength(size)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition.attachment().filename(archive.fileName()).build().toString())
                    .body(new InputStreamResource(sent));
        } catch (IOException failure) {
            delete(file);
            throw new UncheckedIOException(failure);
        } catch (RuntimeException failure) {
            delete(file);
            throw failure;
        }
    }

    private static Path temporaryFile() {
        try {
            return Files.createTempFile("vinted-republisher-archive-", ".zip");
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static void delete(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
