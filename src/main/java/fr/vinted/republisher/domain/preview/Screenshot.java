package fr.vinted.republisher.domain.preview;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

/** A JPEG capture of the visible part of a page, and when it was taken. */
public record Screenshot(byte[] image, Instant capturedAt) {

    public Screenshot {
        Objects.requireNonNull(image, "image");
        Objects.requireNonNull(capturedAt, "capturedAt");
        image = image.clone();
    }

    @Override
    public byte[] image() {
        return image.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Screenshot screenshot
                && Arrays.equals(image, screenshot.image) && capturedAt.equals(screenshot.capturedAt);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(image) + capturedAt.hashCode();
    }

    @Override
    public String toString() {
        return "Screenshot[" + image.length + " bytes, capturedAt=" + capturedAt + "]";
    }
}
