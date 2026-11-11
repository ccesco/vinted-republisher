package fr.vinted.republisher.application.backup;

import fr.vinted.republisher.domain.backup.DownloadedPhoto;
import fr.vinted.republisher.domain.backup.ListingBackupRepository;
import fr.vinted.republisher.domain.backup.ListingPhoto;
import fr.vinted.republisher.domain.backup.PhotoStorage;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.util.Optional;

/** A kept photo as its file holds it now, so that the member sees exactly what would be republished. */
public class ShowListingBackupPhoto {

    private final ListingBackupRepository listingBackups;
    private final PhotoStorage photos;

    public ShowListingBackupPhoto(ListingBackupRepository listingBackups, PhotoStorage photos) {
        this.listingBackups = listingBackups;
        this.photos = photos;
    }

    /** Empty if the listing has no backup, no photo at that rank, or its file is missing. */
    public Optional<DownloadedPhoto> show(VintedListingId listing, int rank) {
        return listingBackups.find(listing)
                .flatMap(backup -> backup.photos().stream().filter(photo -> photo.rank() == rank).findFirst())
                .flatMap(photo -> photos.read(listing, photo)
                        .filter(content -> content.length > 0)
                        .map(content -> new DownloadedPhoto(content, photo.mediaType())));
    }
}
