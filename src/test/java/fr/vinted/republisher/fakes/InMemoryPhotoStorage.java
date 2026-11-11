package fr.vinted.republisher.fakes;

import fr.vinted.republisher.domain.backup.DownloadedPhoto;
import fr.vinted.republisher.domain.backup.ListingPhoto;
import fr.vinted.republisher.domain.backup.PhotoStorage;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.io.UncheckedIOException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class InMemoryPhotoStorage implements PhotoStorage {

    private final Map<VintedListingId, Map<String, byte[]>> files = new HashMap<>();
    private final List<String> written = new ArrayList<>();
    private boolean diskFull;

    public InMemoryPhotoStorage whereWritingFails() {
        diskFull = true;
        return this;
    }

    /** File name to content, for the given listing. */
    public Map<String, byte[]> files(long listing) {
        return new TreeMap<>(files.getOrDefault(new VintedListingId(listing), Map.of()));
    }

    /** The file of the photo disappears, as if deleted by hand. */
    public void lose(long listing, ListingPhoto photo) {
        files.getOrDefault(new VintedListingId(listing), new HashMap<>()).remove(photo.fileName());
    }

    /** The file of the photo now holds other bytes, as if edited by hand. */
    public void overwrite(long listing, ListingPhoto photo, byte[] content) {
        files.computeIfAbsent(new VintedListingId(listing), id -> new HashMap<>()).put(photo.fileName(), content);
    }

    @Override
    public Optional<byte[]> read(VintedListingId listing, ListingPhoto photo) {
        return Optional.ofNullable(files.getOrDefault(listing, Map.of()).get(photo.fileName())).map(byte[]::clone);
    }

    /** Names of the files written so far, in order. */
    public List<String> written() {
        return written;
    }

    @Override
    public void store(VintedListingId listing, DownloadedPhoto photo) {
        if (diskFull) {
            throw new UncheckedIOException(new IOException("No space left on device"));
        }
        Map<String, byte[]> ofListing = files.computeIfAbsent(listing, id -> new HashMap<>());
        if (!ofListing.containsKey(photo.fileName())) {
            ofListing.put(photo.fileName(), photo.content());
            written.add(photo.fileName());
        }
    }

    @Override
    public void removeAllBut(VintedListingId listing, List<ListingPhoto> photos) {
        Set<String> kept = photos.stream().map(ListingPhoto::fileName).collect(Collectors.toSet());
        files.getOrDefault(listing, new HashMap<>()).keySet().retainAll(kept);
    }
}
