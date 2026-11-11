package fr.vinted.republisher.domain.backup;

import fr.vinted.republisher.domain.backup.VerificationFailure.ListingAbsent;
import fr.vinted.republisher.domain.backup.VerificationFailure.NoPhoto;
import fr.vinted.republisher.domain.backup.VerificationFailure.PhotoAltered;
import fr.vinted.republisher.domain.backup.VerificationFailure.PhotoMissing;
import fr.vinted.republisher.domain.backup.VerificationFailure.PriceChanged;
import fr.vinted.republisher.domain.backup.VerificationFailure.TitleChanged;
import fr.vinted.republisher.domain.wardrobe.Listing;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Whether a backup can be trusted to republish its listing: photos kept and intact, listing still in the wardrobe,
 * as the last scan read it. Never kept: done again whenever it matters.
 */
public record BackupVerification(List<VerificationFailure> failures) {

    public BackupVerification {
        failures = List.copyOf(failures);
    }

    /** {@code listing} is the listing as the last scan left it, if the inventory knows it. */
    public static BackupVerification of(ListingBackup backup, Optional<Listing> listing, PhotoStorage storage) {
        List<VerificationFailure> failures = new ArrayList<>(photoFailures(backup, storage));
        if (listing.filter(Listing::present).isEmpty()) {
            failures.add(new ListingAbsent());
        } else {
            failures.addAll(changes(backup.content(), listing.get()));
        }
        return new BackupVerification(failures);
    }

    public boolean passed() {
        return failures.isEmpty();
    }

    private static List<VerificationFailure> photoFailures(ListingBackup backup, PhotoStorage storage) {
        if (backup.photos().isEmpty()) {
            return List.of(new NoPhoto());
        }
        List<VerificationFailure> failures = new ArrayList<>();
        for (ListingPhoto photo : backup.photos()) {
            Optional<byte[]> file = storage.read(backup.id(), photo);
            if (file.isEmpty()) {
                failures.add(new PhotoMissing(photo.rank()));
            } else if (!DownloadedPhoto.sha256(file.get()).equals(photo.sha256())) {
                failures.add(new PhotoAltered(photo.rank()));
            }
        }
        return failures;
    }

    private static List<VerificationFailure> changes(ListingContent saved, Listing scanned) {
        List<VerificationFailure> failures = new ArrayList<>();
        if (!saved.title().equals(scanned.title())) {
            failures.add(new TitleChanged(saved.title(), scanned.title()));
        }
        if (!saved.price().equals(scanned.price())) {
            failures.add(new PriceChanged(saved.price(), scanned.price()));
        }
        return failures;
    }
}
