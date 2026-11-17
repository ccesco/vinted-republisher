package fr.vinted.republisher.adapter.in.web;

import fr.vinted.republisher.adapter.in.web.api.ListingRepublicationApi;
import fr.vinted.republisher.adapter.in.web.api.model.ListingBackupViewDto;
import fr.vinted.republisher.adapter.in.web.api.model.RepublicationRequestDto;
import fr.vinted.republisher.adapter.in.web.api.model.RepublicationValidationDto;
import fr.vinted.republisher.application.republication.RepublishListing;
import fr.vinted.republisher.application.republication.ValidateListingRepublication;
import fr.vinted.republisher.application.republication.VerifyListingRepublication;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/** Implements the contract-first API of a listing's republication on Vinted, its verification and validation. */
@RestController
public class ListingRepublicationApiController implements ListingRepublicationApi {

    private final RepublishListing republishListing;
    private final VerifyListingRepublication verifyListingRepublication;
    private final ValidateListingRepublication validateListingRepublication;

    public ListingRepublicationApiController(RepublishListing republishListing,
                                             VerifyListingRepublication verifyListingRepublication,
                                             ValidateListingRepublication validateListingRepublication) {
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
                republishListing.republish(new VintedListingId(listingId), request.getSavedAt().toInstant())));
    }

    @Override
    public ResponseEntity<ListingBackupViewDto> verifyListingRepublication(Long listingId) {
        return ResponseEntity.ok(ListingBackupApiController.toDto(
                verifyListingRepublication.verify(new VintedListingId(listingId))));
    }
}
