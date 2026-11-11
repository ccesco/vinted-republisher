package fr.vinted.republisher.acceptance;

import fr.vinted.republisher.application.backup.ShowBackup;
import fr.vinted.republisher.application.backup.ShowListingBackup;
import fr.vinted.republisher.application.backup.ValidateListingBackup;
import fr.vinted.republisher.domain.backup.BackupFixtures;
import fr.vinted.republisher.domain.backup.BackupNotVerified;
import fr.vinted.republisher.domain.backup.BackupReplaced;
import fr.vinted.republisher.domain.backup.BackupVerification;
import fr.vinted.republisher.domain.backup.LastBackupProblem;
import fr.vinted.republisher.domain.backup.ListingBackup;
import fr.vinted.republisher.domain.backup.ListingBackupView;
import fr.vinted.republisher.domain.backup.ListingReading;
import fr.vinted.republisher.domain.backup.VerificationFailure.ListingAbsent;
import fr.vinted.republisher.domain.backup.VerificationFailure.PhotoAltered;
import fr.vinted.republisher.domain.backup.VerificationFailure.PhotoMissing;
import fr.vinted.republisher.domain.backup.VerificationFailure.PriceChanged;
import fr.vinted.republisher.domain.backup.VerificationFailure.TitleChanged;
import fr.vinted.republisher.domain.session.VintedSession;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.Price;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import fr.vinted.republisher.fakes.AdjustableClock;
import fr.vinted.republisher.fakes.InMemoryBackupValidationRepository;
import fr.vinted.republisher.fakes.InMemoryListingBackupRepository;
import fr.vinted.republisher.fakes.InMemoryListingRepository;
import fr.vinted.republisher.fakes.InMemoryPhotoStorage;
import fr.vinted.republisher.fakes.InMemoryWardrobeBackupRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static fr.vinted.republisher.domain.backup.BackupFixtures.aContent;
import static fr.vinted.republisher.domain.backup.BackupFixtures.aPhoto;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.aListing;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.anActiveListing;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class ValidateListingBackupAcceptanceTest {

    private static final VintedListingId LISTING = new VintedListingId(1);
    private static final Instant SCANNED_AT = Instant.parse("2026-11-10T09:00:00Z");
    private static final Instant SAVED_AT = Instant.parse("2026-11-10T10:00:00Z");
    private static final Instant NOW = Instant.parse("2026-11-11T10:00:00Z");

    private final InMemoryListingRepository listings = new InMemoryListingRepository();
    private final InMemoryListingBackupRepository listingBackups = new InMemoryListingBackupRepository();
    private final InMemoryPhotoStorage photos = new InMemoryPhotoStorage();
    private final InMemoryBackupValidationRepository validations = new InMemoryBackupValidationRepository();
    private final AdjustableClock clock = new AdjustableClock(NOW);
    private final ShowListingBackup showListingBackup =
            new ShowListingBackup(listings, listingBackups, photos, validations);
    private final ValidateListingBackup validateListingBackup =
            new ValidateListingBackup(listings, listingBackups, photos, validations, clock);
    private final ShowBackup showBackup = new ShowBackup(new VintedSession(), new LastBackupProblem(),
            new InMemoryWardrobeBackupRepository(), listingBackups, validations);

    @Test
    void preview_shows_a_verified_backup() {
        // Étant donné une annonce présente, sauvegardée avec 3 photos, dont le titre et le prix sont ceux du dernier scan
        aScannedListing();
        ListingBackup backup = aKeptBackup(SAVED_AT, "front", "back", "label");
        // Quand j'ouvre l'aperçu de sa sauvegarde
        ListingBackupView view = showListingBackup.show(LISTING);
        // Alors la vérification est réussie
        // Et je vois ses 3 photos dans l'ordre et tout son contenu par ses libellés
        // Et elle n'est pas validée
        assertThat(view.verification().passed()).isTrue();
        assertThat(view.backup()).isEqualTo(backup);
        assertThat(view.validatedAt()).isEmpty();
    }

    @Test
    void member_validates_a_verified_backup() {
        // Étant donné l'aperçu d'une sauvegarde dont la vérification est réussie
        aScannedListing();
        aKeptBackup(SAVED_AT, "front");
        ListingBackupView preview = showListingBackup.show(LISTING);
        // Quand je clique sur « Valider »
        ListingBackupView validated = validateListingBackup.validate(LISTING, preview.backup().savedAt());
        // Alors la sauvegarde est validée, avec la date de validation
        // Et la page du dressing affiche « validée le … » pour cette annonce
        assertThat(validated.validatedAt()).contains(NOW);
        assertThat(showListingBackup.show(LISTING).validatedAt()).contains(NOW);
        assertThat(showBackup.show().validatedAt()).containsExactlyEntriesOf(Map.of(LISTING, NOW));
    }

    @Test
    void missing_photo_fails_the_verification() {
        // Étant donné une annonce sauvegardée dont le fichier de la photo de rang 1 a disparu
        aScannedListing();
        ListingBackup backup = aKeptBackup(SAVED_AT, "front", "back");
        photos.lose(1, backup.photos().get(1));
        // Quand j'ouvre l'aperçu ou que je demande la validation
        BackupVerification verification = showListingBackup.show(LISTING).verification();
        Throwable refusal = catchThrowable(() -> validateListingBackup.validate(LISTING, SAVED_AT));
        // Alors la vérification échoue avec « photo 1 manquante »
        // Et la validation est refusée, rien n'est enregistré
        assertThat(verification.failures()).containsExactly(new PhotoMissing(1));
        assertThat(refusal).isInstanceOfSatisfying(BackupNotVerified.class,
                notVerified -> assertThat(notVerified.verification()).isEqualTo(verification));
        assertThat(validations.findAll()).isEmpty();
    }

    @Test
    void altered_photo_fails_the_verification() {
        // Étant donné une annonce sauvegardée dont le fichier de la photo de rang 0 a été modifié
        aScannedListing();
        ListingBackup backup = aKeptBackup(SAVED_AT, "front", "back");
        photos.overwrite(1, backup.photos().get(0), "retouchée".getBytes(StandardCharsets.UTF_8));
        // Quand je demande la validation
        Throwable refusal = catchThrowable(() -> validateListingBackup.validate(LISTING, SAVED_AT));
        // Alors la vérification échoue avec « photo 0 modifiée »
        // Et la validation est refusée
        assertThat(refusal).isInstanceOfSatisfying(BackupNotVerified.class,
                notVerified -> assertThat(notVerified.verification().failures()).containsExactly(new PhotoAltered(0)));
        assertThat(validations.findAll()).isEmpty();
    }

    @Test
    void backup_differing_from_the_last_scan_fails_the_verification() {
        // Étant donné une annonce sauvegardée au titre « Robe bleue » et au prix 12,00 €
        // Et le dernier scan l'a lue au titre « Robe bleue M » et au prix 10,00 €
        listings.saveAll(List.of(Listing.seen(aListing(1, "Robe bleue M", "10.00", true), SCANNED_AT)));
        aKeptBackup(new ListingReading(aContent(1, "Robe bleue", "Portée une fois", "12.00"), List.of(aPhoto("front"))));
        // Quand j'ouvre l'aperçu
        BackupVerification verification = showListingBackup.show(LISTING).verification();
        // Alors la vérification échoue sur le titre et sur le prix, avec les deux valeurs de chacun
        assertThat(verification.failures()).containsExactly(
                new TitleChanged("Robe bleue", "Robe bleue M"),
                new PriceChanged(new Price(new BigDecimal("12.00"), "EUR"), new Price(new BigDecimal("10.00"), "EUR")));
    }

    @Test
    void absent_listing_fails_the_verification() {
        // Étant donné une annonce sauvegardée qui n'est plus présente dans le dressing
        listings.saveAll(List.of(Listing.seen(anActiveListing(1), SCANNED_AT).absent()));
        aKeptBackup(SAVED_AT, "front");
        // Quand je demande la validation
        Throwable refusal = catchThrowable(() -> validateListingBackup.validate(LISTING, SAVED_AT));
        // Alors la vérification échoue avec « annonce absente du dressing »
        assertThat(refusal).isInstanceOfSatisfying(BackupNotVerified.class,
                notVerified -> assertThat(notVerified.verification().failures()).containsExactly(new ListingAbsent()));
    }

    @Test
    void replaced_backup_cannot_be_validated_from_a_stale_preview() {
        // Étant donné l'aperçu ouvert sur la sauvegarde du 10/11
        aScannedListing();
        aKeptBackup(SAVED_AT, "front");
        ListingBackupView preview = showListingBackup.show(LISTING);
        // Et une sauvegarde de l'annonce enregistrée le 11/11 depuis
        aKeptBackup(NOW, "front");
        // Quand je clique sur « Valider »
        Throwable refusal = catchThrowable(() -> validateListingBackup.validate(LISTING, preview.backup().savedAt()));
        // Alors la validation est refusée car la sauvegarde a été remplacée
        // Et aucune version n'est validée
        assertThat(refusal).isInstanceOf(BackupReplaced.class);
        assertThat(validations.findAll()).isEmpty();
    }

    @Test
    void new_backup_cancels_the_validation() {
        // Étant donné une annonce dont la sauvegarde est validée
        aScannedListing();
        aKeptBackup(SAVED_AT, "front");
        validateListingBackup.validate(LISTING, SAVED_AT);
        // Quand une nouvelle sauvegarde de l'annonce est enregistrée
        aKeptBackup(NOW, "front");
        // Alors l'annonce n'est plus validée
        assertThat(showListingBackup.show(LISTING).validatedAt()).isEmpty();
        assertThat(showBackup.show().validatedAt()).isEmpty();
    }

    @Test
    void validating_twice_keeps_the_first_validation() {
        // Étant donné une sauvegarde validée le 11/11 à 10 h
        aScannedListing();
        aKeptBackup(SAVED_AT, "front");
        validateListingBackup.validate(LISTING, SAVED_AT);
        clock.advance(Duration.ofHours(2));
        // Quand je la valide de nouveau
        ListingBackupView again = validateListingBackup.validate(LISTING, SAVED_AT);
        // Alors elle reste validée le 11/11 à 10 h
        assertThat(again.validatedAt()).contains(NOW);
        assertThat(showListingBackup.show(LISTING).validatedAt()).contains(NOW);
    }

    private void aScannedListing() {
        listings.saveAll(List.of(Listing.seen(anActiveListing(1), SCANNED_AT)));
    }

    private ListingBackup aKeptBackup(Instant savedAt, String... photoNames) {
        return aKeptBackup(new ListingReading(aContent(1), Stream.of(photoNames).map(BackupFixtures::aPhoto).toList()),
                savedAt);
    }

    private ListingBackup aKeptBackup(ListingReading reading) {
        return aKeptBackup(reading, SAVED_AT);
    }

    private ListingBackup aKeptBackup(ListingReading reading, Instant savedAt) {
        reading.photos().forEach(photo -> photos.store(LISTING, photo));
        ListingBackup backup = ListingBackup.of(reading, savedAt);
        listingBackups.save(backup);
        return backup;
    }
}
