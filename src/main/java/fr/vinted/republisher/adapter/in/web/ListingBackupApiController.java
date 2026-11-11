package fr.vinted.republisher.adapter.in.web;

import fr.vinted.republisher.adapter.in.web.api.ListingBackupApi;
import fr.vinted.republisher.adapter.in.web.api.model.BackupValidationRequestDto;
import fr.vinted.republisher.adapter.in.web.api.model.BackupVerificationDto;
import fr.vinted.republisher.adapter.in.web.api.model.KeptPhotoDto;
import fr.vinted.republisher.adapter.in.web.api.model.ListingBackupContentDto;
import fr.vinted.republisher.adapter.in.web.api.model.ListingBackupViewDto;
import fr.vinted.republisher.adapter.in.web.api.model.PriceDto;
import fr.vinted.republisher.adapter.in.web.api.model.VerificationFailureDto;
import fr.vinted.republisher.adapter.in.web.api.model.VerificationFailureKindDto;
import fr.vinted.republisher.application.backup.ShowListingBackup;
import fr.vinted.republisher.application.backup.ShowListingBackupPhoto;
import fr.vinted.republisher.application.backup.ValidateListingBackup;
import fr.vinted.republisher.domain.backup.BackupVerification;
import fr.vinted.republisher.domain.backup.CategoryDetails;
import fr.vinted.republisher.domain.backup.FormOption;
import fr.vinted.republisher.domain.backup.FormOptions;
import fr.vinted.republisher.domain.backup.ListingBackupView;
import fr.vinted.republisher.domain.backup.ListingContent;
import fr.vinted.republisher.domain.backup.ListingPhoto;
import fr.vinted.republisher.domain.backup.VerificationFailure;
import fr.vinted.republisher.domain.wardrobe.Price;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneOffset;

/** Implements the contract-first API of a listing's backup: it reads the database and the kept files only. */
@RestController
public class ListingBackupApiController implements ListingBackupApi {

    private final ShowListingBackup showListingBackup;
    private final ValidateListingBackup validateListingBackup;
    private final ShowListingBackupPhoto showListingBackupPhoto;

    public ListingBackupApiController(ShowListingBackup showListingBackup, ValidateListingBackup validateListingBackup,
                                      ShowListingBackupPhoto showListingBackupPhoto) {
        this.showListingBackup = showListingBackup;
        this.validateListingBackup = validateListingBackup;
        this.showListingBackupPhoto = showListingBackupPhoto;
    }

    @Override
    public ResponseEntity<ListingBackupViewDto> showListingBackup(Long listingId) {
        return ResponseEntity.ok(toDto(showListingBackup.show(new VintedListingId(listingId))));
    }

    @Override
    public ResponseEntity<Resource> showListingBackupPhoto(Long listingId, Integer rank) {
        return showListingBackupPhoto.show(new VintedListingId(listingId), rank)
                .map(photo -> ResponseEntity.ok().contentType(MediaType.parseMediaType(photo.mediaType()))
                        .body((Resource) new ByteArrayResource(photo.content())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Override
    public ResponseEntity<ListingBackupViewDto> validateListingBackup(Long listingId,
                                                                      BackupValidationRequestDto request) {
        return ResponseEntity.ok(toDto(validateListingBackup.validate(new VintedListingId(listingId),
                request.getSavedAt().toInstant())));
    }

    private static ListingBackupViewDto toDto(ListingBackupView view) {
        long id = view.backup().id().value();
        return new ListingBackupViewDto()
                .vintedId(id)
                .savedAt(view.backup().savedAt().atOffset(ZoneOffset.UTC))
                .validatedAt(view.validatedAt().map(date -> date.atOffset(ZoneOffset.UTC)).orElse(null))
                .verification(toDto(view.verification()))
                .content(toDto(view.backup().content()))
                .photos(view.backup().photos().stream().map(photo -> toDto(id, photo)).toList());
    }

    static BackupVerificationDto toDto(BackupVerification verification) {
        return new BackupVerificationDto()
                .passed(verification.passed())
                .failures(verification.failures().stream().map(ListingBackupApiController::toDto).toList());
    }

    private static VerificationFailureDto toDto(VerificationFailure failure) {
        return switch (failure) {
            case VerificationFailure.NoPhoto noPhoto -> failureOf(VerificationFailureKindDto.NO_PHOTO);
            case VerificationFailure.PhotoMissing missing ->
                    failureOf(VerificationFailureKindDto.PHOTO_MISSING).rank(missing.rank());
            case VerificationFailure.PhotoAltered altered ->
                    failureOf(VerificationFailureKindDto.PHOTO_ALTERED).rank(altered.rank());
            case VerificationFailure.ListingAbsent absent -> failureOf(VerificationFailureKindDto.LISTING_ABSENT);
            case VerificationFailure.TitleChanged changed -> failureOf(VerificationFailureKindDto.TITLE_CHANGED)
                    .savedTitle(changed.saved()).scannedTitle(changed.scanned());
            case VerificationFailure.PriceChanged changed -> failureOf(VerificationFailureKindDto.PRICE_CHANGED)
                    .savedPrice(toDto(changed.saved())).scannedPrice(toDto(changed.scanned()));
        };
    }

    private static VerificationFailureDto failureOf(VerificationFailureKindDto kind) {
        return new VerificationFailureDto().kind(kind);
    }

    /** Labels only: Vinted's identifiers mean nothing to the member. */
    private static ListingBackupContentDto toDto(ListingContent content) {
        CategoryDetails details = content.details();
        return new ListingBackupContentDto()
                .title(content.title())
                .description(content.description())
                .price(toDto(content.price()))
                .category(content.category().label())
                .brand(content.brand().map(FormOption::label).orElse(null))
                .condition(content.condition().label())
                .size(content.size().map(FormOption::label).orElse(null))
                .colors(content.colors().map(FormOptions::label).orElse(null))
                .materials(content.materials().map(FormOptions::label).orElse(null))
                .packageSize(content.packageSize().label())
                .isbn(details.isbn().orElse(null))
                .bookTitle(details.bookTitle().orElse(null))
                .author(details.author().orElse(null))
                .manufacturer(details.manufacturer().orElse(null))
                .manufacturerLabel(details.manufacturerLabel().orElse(null))
                .model(details.model().orElse(null))
                .measurementWidth(details.measurementWidth().orElse(null))
                .measurementLength(details.measurementLength().orElse(null))
                .measurementUnit(details.measurementUnit().orElse(null));
    }

    private static KeptPhotoDto toDto(long listingId, ListingPhoto photo) {
        return new KeptPhotoDto().rank(photo.rank())
                .url("/api/listings/" + listingId + "/backup/photos/" + photo.rank());
    }

    private static PriceDto toDto(Price price) {
        return new PriceDto().amount(price.amount()).currency(price.currency());
    }
}
