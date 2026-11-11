package fr.vinted.republisher.application.backup;

import fr.vinted.republisher.domain.backup.ListingBackup;
import fr.vinted.republisher.domain.backup.ListingReading;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import fr.vinted.republisher.fakes.InMemoryListingBackupRepository;
import fr.vinted.republisher.fakes.InMemoryPhotoStorage;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static fr.vinted.republisher.domain.backup.BackupFixtures.aContent;
import static fr.vinted.republisher.domain.backup.BackupFixtures.aPhoto;
import static org.assertj.core.api.Assertions.assertThat;

class ShowListingBackupPhotoTest {

    private static final VintedListingId LISTING = new VintedListingId(1);

    private final InMemoryListingBackupRepository listingBackups = new InMemoryListingBackupRepository();
    private final InMemoryPhotoStorage photos = new InMemoryPhotoStorage();
    private final ShowListingBackupPhoto showPhoto = new ShowListingBackupPhoto(listingBackups, photos);

    @Test
    void shows_the_kept_file_of_the_photo_at_its_rank() {
        aKeptBackup();

        assertThat(showPhoto.show(LISTING, 1)).contains(aPhoto("back"));
    }

    @Test
    void shows_nothing_for_an_unknown_rank_listing_or_file() {
        ListingBackup backup = aKeptBackup();
        photos.lose(1, backup.photos().get(0));

        assertThat(showPhoto.show(LISTING, 2)).isEmpty();
        assertThat(showPhoto.show(LISTING, -1)).isEmpty();
        assertThat(showPhoto.show(new VintedListingId(2), 0)).isEmpty();
        assertThat(showPhoto.show(LISTING, 0)).isEmpty();
    }

    private ListingBackup aKeptBackup() {
        ListingReading reading = new ListingReading(aContent(1), List.of(aPhoto("front"), aPhoto("back")));
        reading.photos().forEach(photo -> photos.store(LISTING, photo));
        ListingBackup backup = ListingBackup.of(reading, Instant.parse("2026-11-10T10:00:00Z"));
        listingBackups.save(backup);
        return backup;
    }
}
