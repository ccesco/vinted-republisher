package fr.vinted.republisher.adapter.in.web;

import fr.vinted.republisher.adapter.in.web.api.ListingDeletionApi;
import fr.vinted.republisher.adapter.in.web.api.model.DeletionRequestDto;
import fr.vinted.republisher.adapter.in.web.api.model.ListingBackupViewDto;
import fr.vinted.republisher.application.deletion.DeleteListing;
import fr.vinted.republisher.application.deletion.RecheckListingDeletion;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Implements the contract-first API of a listing's deletion on Vinted. */
@RestController
public class ListingDeletionApiController implements ListingDeletionApi {

    private final DeleteListing deleteListing;
    private final RecheckListingDeletion recheckListingDeletion;

    public ListingDeletionApiController(DeleteListing deleteListing, RecheckListingDeletion recheckListingDeletion) {
        this.deleteListing = deleteListing;
        this.recheckListingDeletion = recheckListingDeletion;
    }

    @Override
    public ResponseEntity<ListingBackupViewDto> deleteListing(Long listingId, DeletionRequestDto request) {
        return ResponseEntity.ok(ListingBackupApiController.toDto(
                deleteListing.delete(new VintedListingId(listingId), request.getSavedAt().toInstant())));
    }

    @Override
    public ResponseEntity<ListingBackupViewDto> recheckListingDeletion(Long listingId) {
        return ResponseEntity.ok(ListingBackupApiController.toDto(
                recheckListingDeletion.recheck(new VintedListingId(listingId))));
    }
}
