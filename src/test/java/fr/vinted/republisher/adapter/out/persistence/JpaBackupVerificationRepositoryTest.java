package fr.vinted.republisher.adapter.out.persistence;

import fr.vinted.republisher.ApplicationWithDatabaseTest;
import fr.vinted.republisher.domain.backup.BackupValidation;
import fr.vinted.republisher.domain.backup.BackupValidationRepository;
import fr.vinted.republisher.domain.backup.BackupVerificationRepository;
import fr.vinted.republisher.domain.backup.ListingBackup;
import fr.vinted.republisher.domain.backup.ListingBackupRepository;
import fr.vinted.republisher.domain.backup.ListingFieldDifference;
import fr.vinted.republisher.domain.backup.ListingFormField;
import fr.vinted.republisher.domain.backup.VerificationFailure;
import fr.vinted.republisher.domain.backup.VerificationFailure.FieldChanged;
import fr.vinted.republisher.domain.backup.VerificationFailure.ListingAbsent;
import fr.vinted.republisher.domain.backup.VerificationFailure.NoPhoto;
import fr.vinted.republisher.domain.backup.VerificationFailure.PhotoAltered;
import fr.vinted.republisher.domain.backup.VerificationFailure.PhotoMissing;
import fr.vinted.republisher.domain.backup.VerificationFailure.PriceChanged;
import fr.vinted.republisher.domain.backup.VerificationFailure.TitleChanged;
import fr.vinted.republisher.domain.backup.VerificationResult;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.ListingRepository;
import fr.vinted.republisher.domain.wardrobe.Price;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static fr.vinted.republisher.domain.backup.BackupFixtures.aBackup;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.anActiveListing;
import static org.assertj.core.api.Assertions.assertThat;

/** On a disposable PostgreSQL with the real changelog, never the real data. */
class JpaBackupVerificationRepositoryTest extends ApplicationWithDatabaseTest {

    private static final VintedListingId LISTING = new VintedListingId(10208778166L);
    private static final Instant SAVED_AT = Instant.parse("2026-11-10T10:00:00.123456Z");
    private static final Instant VERIFIED_AT = Instant.parse("2026-11-18T10:00:00.654321Z");

    @Autowired
    private BackupVerificationRepository verifications;

    @Autowired
    private BackupValidationRepository validations;

    @Autowired
    private ListingRepository listings;

    @Autowired
    private ListingBackupRepository listingBackups;

    private ListingBackup backup;

    @BeforeEach
    void startWithABackup() {
        listings.saveAll(List.of(Listing.seen(anActiveListing(LISTING.value()), SAVED_AT)));
        backup = aBackup(LISTING.value(), SAVED_AT);
        listingBackups.save(backup);
    }

    @Test
    void nothing_is_found_before_the_first_verification() {
        assertThat(verifications.findAll()).isEmpty();
        assertThat(verifications.find(LISTING)).isEmpty();
    }

    @Test
    void failures_survive_a_restart_as_they_were_saved() {
        // AT-40-11 : rien n'est gardé en mémoire, tout est relu en base
        List<VerificationFailure> failures = List.of(new NoPhoto(), new PhotoMissing(1), new PhotoAltered(2),
                new ListingAbsent(), new TitleChanged("Robe", "Robe bleue"),
                new PriceChanged(new Price(new BigDecimal("12.50"), "EUR"), new Price(new BigDecimal("9"), "EUR")),
                new FieldChanged(new ListingFieldDifference(ListingFormField.SIZE, "M (207)", "L (208)")),
                new FieldChanged(new ListingFieldDifference(ListingFormField.BRAND, "", "Kiabi (60)")));
        VerificationResult failed = new VerificationResult(LISTING, SAVED_AT, VERIFIED_AT, failures);

        verifications.save(failed);

        assertThat(verifications.find(LISTING)).contains(failed);
        assertThat(verifications.findAll()).containsExactly(failed);
    }

    @Test
    void new_result_replaces_the_previous_one_of_the_listing() {
        verifications.save(new VerificationResult(LISTING, SAVED_AT, VERIFIED_AT, List.of(new ListingAbsent())));
        VerificationResult passed = new VerificationResult(LISTING, SAVED_AT, VERIFIED_AT.plusSeconds(60), List.of());

        verifications.save(passed);

        assertThat(verifications.findAll()).containsExactly(passed);
    }

    @Test
    void a_failure_cancels_the_validation_of_the_listing() {
        validations.save(BackupValidation.of(backup, SAVED_AT.plusSeconds(60)));

        verifications.save(new VerificationResult(LISTING, SAVED_AT, VERIFIED_AT, List.of(new ListingAbsent())));

        assertThat(validations.find(LISTING)).isEmpty();
    }

    @Test
    void a_pass_keeps_the_validation_of_the_listing() {
        BackupValidation validation = BackupValidation.of(backup, SAVED_AT.plusSeconds(60));
        validations.save(validation);

        verifications.save(new VerificationResult(LISTING, SAVED_AT, VERIFIED_AT, List.of()));

        assertThat(validations.find(LISTING)).contains(validation);
    }
}
