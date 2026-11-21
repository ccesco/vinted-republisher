package fr.vinted.republisher.adapter.in.web;

import fr.vinted.republisher.adapter.in.web.api.ListingRepublicationApi;
import fr.vinted.republisher.adapter.in.web.api.model.ListingBackupViewDto;
import fr.vinted.republisher.adapter.in.web.api.model.ListingFormFieldDto;
import fr.vinted.republisher.adapter.in.web.api.model.ManualRepublicationReportDto;
import fr.vinted.republisher.adapter.in.web.api.model.RepublicationRequestDto;
import fr.vinted.republisher.adapter.in.web.api.model.RepublicationValidationDto;
import fr.vinted.republisher.adapter.in.web.api.model.UnfilledFieldDto;
import fr.vinted.republisher.application.republication.PrepareManualRepublication;
import fr.vinted.republisher.application.republication.RepublishListing;
import fr.vinted.republisher.application.republication.ValidateListingRepublication;
import fr.vinted.republisher.application.republication.VerifyListingRepublication;
import fr.vinted.republisher.domain.cadence.WriteOrigin;
import fr.vinted.republisher.domain.republication.ManualRepublicationReport;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * Implements the contract-first API of a listing's republication on Vinted, manual or not, its verification and
 * validation.
 */
@RestController
public class ListingRepublicationApiController implements ListingRepublicationApi {

    private final RepublishListing republishListing;
    private final VerifyListingRepublication verifyListingRepublication;
    private final ValidateListingRepublication validateListingRepublication;
    private final PrepareManualRepublication prepareManualRepublication;

    public ListingRepublicationApiController(RepublishListing republishListing,
                                             VerifyListingRepublication verifyListingRepublication,
                                             ValidateListingRepublication validateListingRepublication,
                                             PrepareManualRepublication prepareManualRepublication) {
        this.prepareManualRepublication = prepareManualRepublication;
        this.republishListing = republishListing;
        this.verifyListingRepublication = verifyListingRepublication;
        this.validateListingRepublication = validateListingRepublication;
    }

    @Override
    public ResponseEntity<ListingBackupViewDto> validateListingRepublication(Long listingId,
                                                                             RepublicationValidationDto validation) {
        VintedListingId listing = new VintedListingId(listingId);
        return ResponseEntity.ok(ListingBackupApiController.toDto(Boolean.TRUE.equals(validation.getPublished())
                ? validateListingRepublication.published(listing, Optional.ofNullable(validation.getNewListingUrl()))
                : validateListingRepublication.notPublished(listing)));
    }

    @Override
    public ResponseEntity<ListingBackupViewDto> republishListing(Long listingId, RepublicationRequestDto request) {
        return ResponseEntity.ok(ListingBackupApiController.toDto(
                republishListing.republish(new VintedListingId(listingId), request.getSavedAt().toInstant(),
                        WriteOrigin.MANUAL)));
    }

    @Override
    public ResponseEntity<ManualRepublicationReportDto> prepareManualRepublication(Long listingId,
                                                                                  RepublicationRequestDto request) {
        ManualRepublicationReport report = prepareManualRepublication.prepare(new VintedListingId(listingId),
                request.getSavedAt().toInstant());
        return ResponseEntity.ok(new ManualRepublicationReportDto()
                .listing(ListingBackupApiController.toDto(report.republication()))
                .unfilledFields(report.unfilledFields().stream().map(unfilled -> new UnfilledFieldDto()
                        .field(ListingFormFieldDto.valueOf(unfilled.field().name()))
                        .optionId(unfilled.optionId().orElse(null))).toList()));
    }

    @Override
    public ResponseEntity<ListingBackupViewDto> verifyListingRepublication(Long listingId) {
        return ResponseEntity.ok(ListingBackupApiController.toDto(
                verifyListingRepublication.verify(new VintedListingId(listingId))));
    }
}
