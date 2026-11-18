package fr.vinted.republisher.domain.backup;

import fr.vinted.republisher.domain.backup.VerificationFailure.FieldChanged;
import fr.vinted.republisher.domain.backup.VerificationFailure.ListingAbsent;
import fr.vinted.republisher.domain.backup.VerificationFailure.NoPhoto;
import fr.vinted.republisher.domain.backup.VerificationFailure.PhotoAltered;
import fr.vinted.republisher.domain.backup.VerificationFailure.PhotoMissing;
import fr.vinted.republisher.domain.backup.VerificationFailure.PriceChanged;
import fr.vinted.republisher.domain.backup.VerificationFailure.TitleChanged;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.Price;
import fr.vinted.republisher.fakes.InMemoryPhotoStorage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static fr.vinted.republisher.domain.backup.BackupFixtures.aContent;
import static fr.vinted.republisher.domain.backup.BackupFixtures.aPhoto;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.aListing;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.anActiveListing;
import static org.assertj.core.api.Assertions.assertThat;

class BackupVerificationTest {

    private static final Instant SCANNED_AT = Instant.parse("2026-11-10T09:00:00Z");
    private static final Instant SAVED_AT = Instant.parse("2026-11-10T10:00:00Z");

    private final InMemoryPhotoStorage photos = new InMemoryPhotoStorage();

    @Test
    void intact_and_up_to_date_backup_passes() {
        ListingBackup backup = aKeptBackup(aPhoto("front"), aPhoto("back"));

        BackupVerification verification = BackupVerification.of(backup, Optional.of(scannedAsSaved()), photos);

        assertThat(verification.passed()).isTrue();
        assertThat(verification.failures()).isEmpty();
    }

    @Test
    void photos_alone_pass_without_the_listing_in_the_wardrobe() {
        ListingBackup backup = aKeptBackup(aPhoto("front"));

        BackupVerification verification = BackupVerification.ofPhotos(backup, photos);

        assertThat(verification.failures()).isEmpty();
    }

    @Test
    void photos_alone_fail_on_a_missing_file() {
        ListingBackup backup = aKeptBackup(aPhoto("front"), aPhoto("back"));
        photos.lose(1, backup.photos().get(1));

        BackupVerification verification = BackupVerification.ofPhotos(backup, photos);

        assertThat(verification.failures()).containsExactly(new PhotoMissing(1));
    }

    @Test
    void backup_without_photo_fails() {
        ListingBackup backup = new ListingBackup(aContent(1), List.of(), SAVED_AT);

        BackupVerification verification = BackupVerification.of(backup, Optional.of(scannedAsSaved()), photos);

        assertThat(verification.passed()).isFalse();
        assertThat(verification.failures()).containsExactly(new NoPhoto());
    }

    @Test
    void missing_file_fails_with_its_rank() {
        ListingBackup backup = aKeptBackup(aPhoto("front"), aPhoto("back"));
        photos.lose(1, backup.photos().get(1));

        BackupVerification verification = BackupVerification.of(backup, Optional.of(scannedAsSaved()), photos);

        assertThat(verification.failures()).containsExactly(new PhotoMissing(1));
    }

    @Test
    void file_whose_fingerprint_changed_fails_with_its_rank() {
        ListingBackup backup = aKeptBackup(aPhoto("front"), aPhoto("back"));
        photos.overwrite(1, backup.photos().get(0), "retouchée".getBytes(StandardCharsets.UTF_8));

        BackupVerification verification = BackupVerification.of(backup, Optional.of(scannedAsSaved()), photos);

        assertThat(verification.failures()).containsExactly(new PhotoAltered(0));
    }

    @Test
    void listing_no_longer_in_the_wardrobe_fails() {
        ListingBackup backup = aKeptBackup(aPhoto("front"));

        assertThat(BackupVerification.of(backup, Optional.of(scannedAsSaved().absent()), photos).failures())
                .containsExactly(new ListingAbsent());
        assertThat(BackupVerification.of(backup, Optional.empty(), photos).failures())
                .containsExactly(new ListingAbsent());
    }

    @Test
    void title_and_price_differing_from_the_last_scan_fail_with_both_values() {
        ListingBackup backup = aKeptBackup(aPhoto("front"));
        Listing scanned = Listing.seen(aListing(1, "Pull rayé 1 M", "10.00", true), SCANNED_AT);

        BackupVerification verification = BackupVerification.of(backup, Optional.of(scanned), photos);

        assertThat(verification.failures()).containsExactly(
                new TitleChanged("Pull rayé 1", "Pull rayé 1 M"),
                new PriceChanged(new Price(new BigDecimal("12.50"), "EUR"), new Price(new BigDecimal("10.00"), "EUR")));
    }

    @Test
    void every_failure_is_reported() {
        ListingBackup backup = aKeptBackup(aPhoto("front"), aPhoto("back"));
        photos.lose(1, backup.photos().get(0));
        Listing scanned = Listing.seen(aListing(1, "Pull rayé 1 M", "12.50", true), SCANNED_AT);

        BackupVerification verification = BackupVerification.of(backup, Optional.of(scanned), photos);

        assertThat(verification.failures()).containsExactly(
                new PhotoMissing(0), new TitleChanged("Pull rayé 1", "Pull rayé 1 M"));
    }

    @Test
    void backup_identical_to_the_form_read_again_on_vinted_passes() {
        ListingBackup backup = aKeptBackup(aPhoto("front"));

        BackupVerification verification = BackupVerification.online(backup, Optional.of(aContent(1)), photos);

        assertThat(verification.passed()).isTrue();
    }

    @Test
    void labels_are_not_compared_online() {
        ListingBackup backup = aKeptBackup(aPhoto("front"));
        ListingContent saved = aContent(1);
        ListingContent relabelled = new ListingContent(saved.id(), saved.title(), saved.description(), saved.price(),
                new FormOption(saved.category().id(), "Autre libellé"), saved.brand(), saved.condition(),
                saved.size(), Optional.of(new FormOptions(List.of(12L), "")), saved.materials(),
                saved.packageSize(), saved.details());

        assertThat(BackupVerification.online(backup, Optional.of(relabelled), photos).passed()).isTrue();
    }

    @Test
    void every_field_changed_online_fails_with_both_values() {
        ListingBackup backup = aKeptBackup(aPhoto("front"));
        ListingContent read = aContent(1, "Pull rayé 1", "Porté une fois", "10.00");

        BackupVerification verification = BackupVerification.online(backup, Optional.of(read), photos);

        assertThat(verification.failures()).containsExactly(
                new FieldChanged(new ListingFieldDifference(ListingFormField.DESCRIPTION, "Porté deux fois",
                        "Porté une fois")),
                new FieldChanged(new ListingFieldDifference(ListingFormField.PRICE, "12.50 EUR", "10.00 EUR")));
    }

    @Test
    void listing_gone_from_vinted_fails_online() {
        ListingBackup backup = aKeptBackup(aPhoto("front"));

        assertThat(BackupVerification.online(backup, Optional.empty(), photos).failures())
                .containsExactly(new ListingAbsent());
    }

    @Test
    void photos_are_checked_online_too() {
        ListingBackup backup = aKeptBackup(aPhoto("front"), aPhoto("back"));
        photos.lose(1, backup.photos().get(1));

        assertThat(BackupVerification.online(backup, Optional.of(aContent(1)), photos).failures())
                .containsExactly(new PhotoMissing(1));
    }

    private ListingBackup aKeptBackup(DownloadedPhoto... downloaded) {
        ListingReading reading = new ListingReading(aContent(1), List.of(downloaded));
        reading.photos().forEach(photo -> photos.store(reading.content().id(), photo));
        return ListingBackup.of(reading, SAVED_AT);
    }

    private static Listing scannedAsSaved() {
        return Listing.seen(anActiveListing(1), SCANNED_AT);
    }
}
