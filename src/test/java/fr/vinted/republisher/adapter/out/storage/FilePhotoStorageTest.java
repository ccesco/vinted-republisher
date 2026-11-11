package fr.vinted.republisher.adapter.out.storage;

import fr.vinted.republisher.domain.backup.ListingPhoto;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static fr.vinted.republisher.domain.backup.BackupFixtures.aPhoto;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FilePhotoStorageTest {

    private static final VintedListingId LISTING = new VintedListingId(10208778166L);

    @TempDir
    Path dataDirectory;

    private FilePhotoStorage storage() {
        return new FilePhotoStorage(dataDirectory.resolve("photos"));
    }

    private Path fileOf(String name) {
        return dataDirectory.resolve("photos").resolve("10208778166").resolve(name);
    }

    @Test
    void keeps_the_photo_byte_for_byte_under_its_checksum_in_the_folder_of_its_listing() throws IOException {
        storage().store(LISTING, aPhoto("front"));

        assertThat(fileOf(aPhoto("front").fileName())).hasBinaryContent(aPhoto("front").content());
        assertThat(filesIn(fileOf(""))).containsExactly(fileOf(aPhoto("front").fileName()));
    }

    @Test
    void same_photo_is_not_written_again() throws IOException {
        storage().store(LISTING, aPhoto("front"));
        FileTime longAgo = FileTime.from(Instant.parse("2026-01-01T00:00:00Z"));
        Files.setLastModifiedTime(fileOf(aPhoto("front").fileName()), longAgo);

        storage().store(LISTING, aPhoto("front"));

        assertThat(Files.getLastModifiedTime(fileOf(aPhoto("front").fileName()))).isEqualTo(longAgo);
        assertThat(fileOf(aPhoto("front").fileName())).hasBinaryContent(aPhoto("front").content());
    }

    @Test
    void failed_write_leaves_no_temporary_file() throws IOException {
        Path taken = Files.createDirectories(fileOf(aPhoto("front").fileName()));
        Files.writeString(taken.resolve("in the way"), "x");

        assertThatThrownBy(() -> storage().store(LISTING, aPhoto("front"))).isInstanceOf(UncheckedIOException.class);

        assertThat(filesIn(fileOf(""))).containsExactly(taken);
    }

    @Test
    void removes_the_files_no_photo_is_kept_in_temporary_ones_included() throws IOException {
        storage().store(LISTING, aPhoto("A"));
        storage().store(LISTING, aPhoto("B"));
        Files.writeString(fileOf("left by a crash.tmp"), "half");

        storage().removeAllBut(LISTING, List.of(ListingPhoto.at(0, aPhoto("B"))));

        assertThat(filesIn(fileOf(""))).containsExactly(fileOf(aPhoto("B").fileName()));
    }

    @Test
    void listing_never_stored_has_nothing_to_remove() {
        storage().removeAllBut(LISTING, List.of());

        assertThat(fileOf("")).doesNotExist();
    }

    @Test
    void reads_a_kept_photo_byte_for_byte() {
        storage().store(LISTING, aPhoto("front"));

        assertThat(storage().read(LISTING, ListingPhoto.at(0, aPhoto("front")))).hasValueSatisfying(
                content -> assertThat(content).isEqualTo(aPhoto("front").content()));
    }

    @Test
    void missing_photo_reads_as_nothing() {
        storage().store(LISTING, aPhoto("front"));

        assertThat(storage().read(LISTING, ListingPhoto.at(0, aPhoto("back")))).isEmpty();
        assertThat(storage().read(new VintedListingId(1), ListingPhoto.at(0, aPhoto("front")))).isEmpty();
    }

    /** Closed at once: an open listing would keep the folder locked on Windows. */
    private static List<Path> filesIn(Path folder) throws IOException {
        try (Stream<Path> files = Files.list(folder)) {
            return files.toList();
        }
    }
}
