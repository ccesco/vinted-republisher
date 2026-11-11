package fr.vinted.republisher.domain.backup;

import java.util.regex.Pattern;

/** A photo kept for a listing: its rank in the listing (0 is the cover) and the file it is kept in. */
public record ListingPhoto(int rank, String sha256, String extension) {

    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    public ListingPhoto {
        if (rank < 0) {
            throw new IllegalArgumentException("Negative photo rank " + rank);
        }
        if (!SHA256.matcher(sha256).matches()) {
            throw new IllegalArgumentException("Not a SHA-256: " + sha256);
        }
        if (!DownloadedPhoto.EXTENSIONS.containsValue(extension)) {
            throw new IllegalArgumentException("Not an image extension: " + extension);
        }
    }

    public static ListingPhoto at(int rank, DownloadedPhoto photo) {
        return new ListingPhoto(rank, photo.sha256(), photo.extension());
    }

    /** The type Vinted served the file with. */
    public String mediaType() {
        return DownloadedPhoto.mediaTypeOf(extension);
    }

    public String fileName() {
        return sha256 + "." + extension;
    }
}
