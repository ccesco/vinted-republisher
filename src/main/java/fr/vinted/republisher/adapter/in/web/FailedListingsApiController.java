package fr.vinted.republisher.adapter.in.web;

import fr.vinted.republisher.adapter.in.web.api.FailedListingsApi;
import fr.vinted.republisher.adapter.in.web.api.model.FailedListingDto;
import fr.vinted.republisher.adapter.in.web.api.model.FailedListingsDto;
import fr.vinted.republisher.adapter.in.web.api.model.ListingStageDto;
import fr.vinted.republisher.application.inventory.ShowFailedListings;
import fr.vinted.republisher.domain.inventory.FailedListing;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
public class FailedListingsApiController implements FailedListingsApi {

    private final ShowFailedListings showFailedListings;

    public FailedListingsApiController(ShowFailedListings showFailedListings) {
        this.showFailedListings = showFailedListings;
    }

    @Override
    public ResponseEntity<FailedListingsDto> showFailedListings() {
        return ResponseEntity.ok(new FailedListingsDto()
                .listings(showFailedListings.show().stream().map(FailedListingsApiController::toDto).toList()));
    }

    private static FailedListingDto toDto(FailedListing failed) {
        return new FailedListingDto()
                .listingId(failed.listing().id().value())
                .listingTitle(failed.listing().title())
                .stage(ListingStageDto.valueOf(failed.stage().name()))
                .failure(ActionJournalApiController.toDto(failed.failure(), Optional.of(failed.listing().title())));
    }
}
