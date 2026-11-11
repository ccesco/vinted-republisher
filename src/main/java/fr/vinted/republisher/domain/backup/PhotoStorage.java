package fr.vinted.republisher.domain.backup;

import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.util.List;
import java.util.Optional;

/** Where the photos of each listing are kept, as files never modified once written. */
public interface PhotoStorage {

    /** Writes the photo whole under its name, or leaves it as it is if already kept. */
    void store(VintedListingId listing, DownloadedPhoto photo);

    /** Deletes the files of the listing that none of the given photos is kept in. */
    void removeAllBut(VintedListingId listing, List<ListingPhoto> photos);

    /** The bytes of the file the photo is kept in, as they are now: empty if it is missing or cannot be read. */
    Optional<byte[]> read(VintedListingId listing, ListingPhoto photo);
}
