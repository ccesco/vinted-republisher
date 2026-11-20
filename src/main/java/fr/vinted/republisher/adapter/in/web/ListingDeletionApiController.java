package fr.vinted.republisher.adapter.in.web;

import fr.vinted.republisher.adapter.in.web.api.ListingDeletionApi;
import fr.vinted.republisher.adapter.in.web.api.model.DeletionRequestDto;
import fr.vinted.republisher.adapter.in.web.api.model.ListingBackupViewDto;
import fr.vinted.republisher.application.deletion.DeleteListing;
import fr.vinted.republisher.application.deletion.RecheckListingDeletion;
import fr.vinted.republisher.application.republication.ShowListingRepublication;
import fr.vinted.republisher.domain.cadence.WriteOrigin;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Implements the contract-first API of a listing's deletion on Vinted. */
@RestController
public class ListingDeletionApiController implements ListingDeletionApi {

    private final DeleteListing deleteListing;
    private final RecheckListingDeletion recheckListingDeletion;
    private final ShowListingRepublication showListingRepublication;

    public ListingDeletionApiController(DeleteListing deleteListing, RecheckListingDeletion recheckListingDeletion,
                                        ShowListingRepublication showListingRepublication) {
        this.deleteListing = deleteListing;
        this.recheckListingDeletion = recheckListingDeletion;
        this.showListingRepublication = showListingRepublication;
    }

    @Override
    public ResponseEntity<ListingBackupViewDto> deleteListing(Long listingId, DeletionRequestDto request) {
        return ResponseEntity.ok(ListingBackupApiController.toDto(showListingRepublication.around(
                deleteListing.delete(new VintedListingId(listingId), request.getSavedAt().toInstant(),
                        WriteOrigin.MANUAL))));
    }

    @Override
    public ResponseEntity<ListingBackupViewDto> recheckListingDeletion(Long listingId) {
        return ResponseEntity.ok(ListingBackupApiController.toDto(showListingRepublication.around(
                recheckListingDeletion.recheck(new VintedListingId(listingId)))));
    }
}
