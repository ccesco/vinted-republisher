package fr.vinted.republisher.adapter.in.web;

import fr.vinted.republisher.adapter.in.web.api.ListingRepublicationApi;
import fr.vinted.republisher.adapter.in.web.api.model.ListingBackupViewDto;
import fr.vinted.republisher.adapter.in.web.api.model.RepublicationRequestDto;
import fr.vinted.republisher.application.republication.RepublishListing;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Implements the contract-first API of a listing's republication on Vinted. */
@RestController
public class ListingRepublicationApiController implements ListingRepublicationApi {

    private final RepublishListing republishListing;

    public ListingRepublicationApiController(RepublishListing republishListing) {
        this.republishListing = republishListing;
    }

    @Override
    public ResponseEntity<ListingBackupViewDto> republishListing(Long listingId, RepublicationRequestDto request) {
        return ResponseEntity.ok(ListingBackupApiController.toDto(
                republishListing.republish(new VintedListingId(listingId), request.getSavedAt().toInstant())));
    }
}
