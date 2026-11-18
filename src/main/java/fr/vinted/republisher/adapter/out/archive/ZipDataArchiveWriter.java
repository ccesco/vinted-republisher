package fr.vinted.republisher.adapter.out.archive;

import fr.vinted.republisher.domain.archive.DataArchiveWriter;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * A zip of {@code data.json} (the business tables) and of the photos folder as it is. Only the photos folder is read
 * on disk: the browser profile, which holds the Vinted session, never enters the archive.
 */
public class ZipDataArchiveWriter implements DataArchiveWriter {

    private final BusinessTables tables;
    private final Path photos;

    public ZipDataArchiveWriter(BusinessTables tables, Path photos) {
        this.tables = tables;
        this.photos = photos;
    }

    @Override
    public void write(Instant createdAt, OutputStream archive) {
        try {
            ZipOutputStream zip = new ZipOutputStream(new KeptOpen(archive));
            zip.putNextEntry(new ZipEntry("data.json"));
            tables.writeJson(createdAt, new KeptOpen(zip));
            zip.closeEntry();
            for (Path photo : photoFiles()) {
                zip.putNextEntry(new ZipEntry("photos/" + entryName(photos.relativize(photo))));
                Files.copy(photo, zip);
                zip.closeEntry();
            }
            zip.finish();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private List<Path> photoFiles() throws IOException {
        if (!Files.isDirectory(photos)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(photos)) {
            return files.filter(Files::isRegularFile).sorted().toList();
        }
    }

    private static String entryName(Path relative) {
        return Stream.iterate(0, index -> index < relative.getNameCount(), index -> index + 1)
                .map(index -> relative.getName(index).toString()).collect(Collectors.joining("/"));
    }

    /** The caller owns the stream it gives: writing an archive never closes it. */
    private static final class KeptOpen extends FilterOutputStream {

        KeptOpen(OutputStream out) {
            super(out);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            out.write(bytes, offset, length);
        }

        @Override
        public void close() throws IOException {
            flush();
        }
    }
}
