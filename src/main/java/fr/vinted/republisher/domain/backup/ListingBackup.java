package fr.vinted.republisher.domain.backup;

import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * The one backup of a listing, content and photos together, replaced only by a complete reading.
 * A backup made before photos were kept has none.
 */
public record ListingBackup(ListingContent content, List<ListingPhoto> photos, Instant savedAt) {

    public ListingBackup {
        Objects.requireNonNull(content);
        Objects.requireNonNull(savedAt);
        photos = List.copyOf(photos);
        for (int rank = 0; rank < photos.size(); rank++) {
            if (photos.get(rank).rank() != rank) {
                throw new IllegalArgumentException("Photos not ranked from 0 without gap: " + photos);
            }
        }
    }

    public static ListingBackup of(ListingReading reading, Instant savedAt) {
        List<DownloadedPhoto> downloaded = reading.photos();
        return new ListingBackup(reading.content(), IntStream.range(0, downloaded.size())
                .mapToObj(rank -> ListingPhoto.at(rank, downloaded.get(rank))).toList(), savedAt);
    }

    public VintedListingId id() {
        return content.id();
    }
}
