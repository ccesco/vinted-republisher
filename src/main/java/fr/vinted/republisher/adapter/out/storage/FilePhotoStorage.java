package fr.vinted.republisher.adapter.out.storage;

import fr.vinted.republisher.domain.backup.DownloadedPhoto;
import fr.vinted.republisher.domain.backup.ListingPhoto;
import fr.vinted.republisher.domain.backup.PhotoStorage;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Keeps the photos of each listing in {@code <root>/<listing identifier>/<SHA-256>.<extension>}. A file is written
 * whole to a temporary file first, then renamed: under its final name, it is always complete, and never written again.
 */
public class FilePhotoStorage implements PhotoStorage {

    private final Path root;

    public FilePhotoStorage(Path root) {
        this.root = root;
    }

    @Override
    public void store(VintedListingId listing, DownloadedPhoto photo) {
        Path folder = folderOf(listing);
        Path target = folder.resolve(photo.fileName());
        if (Files.isRegularFile(target)) {
            return;
        }
        try {
            Files.createDirectories(folder);
            Path temporary = Files.createTempFile(folder, photo.sha256(), ".tmp");
            try {
                Files.write(temporary, photo.content());
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException failure) {
            throw new UncheckedIOException("Photo " + target + " not written", failure);
        }
    }

    @Override
    public void removeAllBut(VintedListingId listing, List<ListingPhoto> photos) {
        Path folder = folderOf(listing);
        if (!Files.isDirectory(folder)) {
            return;
        }
        Set<String> kept = photos.stream().map(ListingPhoto::fileName).collect(Collectors.toSet());
        try (Stream<Path> files = Files.list(folder)) {
            for (Path file : files.filter(file -> !kept.contains(file.getFileName().toString())).toList()) {
                Files.delete(file);
            }
        } catch (IOException failure) {
            throw new UncheckedIOException("Photos of " + folder + " not cleaned up", failure);
        }
    }

    private Path folderOf(VintedListingId listing) {
        return root.resolve(Long.toString(listing.value()));
    }
}
