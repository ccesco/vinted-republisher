package fr.vinted.republisher.adapter.out.archive;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class ZipDataArchiveWriterTest {

    private static final Instant CREATED_AT = Instant.parse("2026-11-17T13:05:09Z");

    @TempDir
    private Path data;

    private final BusinessTables tables = (createdAt, json) -> {
        try {
            json.write(("{\"createdAt\":\"" + createdAt + "\"}").getBytes(StandardCharsets.UTF_8));
            json.close();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    };

    @Test
    void holds_data_json_then_every_photo_with_the_same_bytes() throws IOException {
        write(data.resolve("photos/10208778166/0.webp"), new byte[]{1, 2, 3});
        write(data.resolve("photos/10208778166/1.jpg"), new byte[]{4});
        write(data.resolve("photos/10208778167/0.png"), new byte[]{5, 6});

        Map<String, byte[]> entries = archive();

        assertThat(entries.keySet()).containsExactly("data.json", "photos/10208778166/0.webp",
                "photos/10208778166/1.jpg", "photos/10208778167/0.png");
        assertThat(new String(entries.get("data.json"), StandardCharsets.UTF_8))
                .isEqualTo("{\"createdAt\":\"2026-11-17T13:05:09Z\"}");
        assertThat(entries.get("photos/10208778166/0.webp")).isEqualTo(new byte[]{1, 2, 3});
        assertThat(entries.get("photos/10208778167/0.png")).isEqualTo(new byte[]{5, 6});
    }

    @Test
    void never_holds_the_browser_profile() throws IOException {
        write(data.resolve("browser-profile/Default/Cookies"), new byte[]{9});

        assertThat(archive().keySet()).containsExactly("data.json");
    }

    @Test
    void holds_only_data_json_without_photos_folder() throws IOException {
        assertThat(archive().keySet()).containsExactly("data.json");
    }

    @Test
    void leaves_the_given_stream_open() throws IOException {
        ClosingWatcher out = new ClosingWatcher();

        new ZipDataArchiveWriter(tables, data.resolve("photos")).write(CREATED_AT, out);

        assertThat(out.closed).isFalse();
    }

    private Map<String, byte[]> archive() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new ZipDataArchiveWriter(tables, data.resolve("photos")).write(CREATED_AT, out);
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(out.toByteArray()))) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                entries.put(entry.getName(), in.readAllBytes());
            }
        }
        return entries;
    }

    private static void write(Path file, byte[] content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, content);
    }

    private static final class ClosingWatcher extends ByteArrayOutputStream {

        private boolean closed;

        @Override
        public void close() {
            closed = true;
        }
    }
}
