package fr.vinted.republisher.domain.backup;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

/** A photo as Vinted served it, byte for byte: what the tool keeps, never re-encoded. */
public final class DownloadedPhoto {

    /** Extension of the file kept, by type served. */
    static final Map<String, String> EXTENSIONS =
            Map.of("image/webp", "webp", "image/jpeg", "jpg", "image/png", "png");

    private final byte[] content;
    private final String extension;
    private final String sha256;

    /** Throws {@link ListingUnreadable} on an empty file or a type that is not an image Vinted serves. */
    public DownloadedPhoto(byte[] content, String mediaType) {
        if (content.length == 0) {
            throw new ListingUnreadable("empty photo");
        }
        String type = mediaType.split(";")[0].strip().toLowerCase(Locale.ROOT);
        if (!EXTENSIONS.containsKey(type)) {
            throw new ListingUnreadable("not an image: " + mediaType);
        }
        this.content = content.clone();
        this.extension = EXTENSIONS.get(type);
        this.sha256 = sha256(this.content);
    }

    public byte[] content() {
        return content.clone();
    }

    public String extension() {
        return extension;
    }

    /** Proves the kept file has not changed since it was downloaded. */
    public String sha256() {
        return sha256;
    }

    public String fileName() {
        return sha256 + "." + extension;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof DownloadedPhoto photo && Arrays.equals(content, photo.content)
                && extension.equals(photo.extension);
    }

    @Override
    public int hashCode() {
        return sha256.hashCode();
    }

    @Override
    public String toString() {
        return "DownloadedPhoto[" + fileName() + ", " + content.length + " bytes]";
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException alwaysThere) {
            throw new IllegalStateException("Every Java platform has SHA-256", alwaysThere);
        }
    }
}
