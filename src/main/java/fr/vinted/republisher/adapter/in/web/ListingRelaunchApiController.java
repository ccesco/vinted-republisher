package fr.vinted.republisher.adapter.in.web;

import fr.vinted.republisher.adapter.in.web.api.ListingRelaunchApi;
import fr.vinted.republisher.adapter.in.web.api.model.RelaunchActionDto;
import fr.vinted.republisher.adapter.in.web.api.model.RelaunchReportDto;
import fr.vinted.republisher.adapter.in.web.api.model.RelaunchRequestDto;
import fr.vinted.republisher.application.relaunch.RelaunchListing;
import fr.vinted.republisher.domain.inventory.ListingStage;
import fr.vinted.republisher.domain.relaunch.RelaunchReport;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ListingRelaunchApiController implements ListingRelaunchApi {

    private final RelaunchListing relaunchListing;

    public ListingRelaunchApiController(RelaunchListing relaunchListing) {
        this.relaunchListing = relaunchListing;
    }

    @Override
    public ResponseEntity<RelaunchReportDto> relaunchListing(Long listingId, RelaunchRequestDto request) {
        RelaunchReport report = relaunchListing.relaunch(new VintedListingId(listingId),
                ListingStage.valueOf(request.getStage().name()));
        return ResponseEntity.ok(new RelaunchReportDto()
                .action(RelaunchActionDto.valueOf(report.action().name()))
                .dryRun(report.dryRun())
                .listing(ListingInventoryApiController.toDto(report.entry())));
    }
}
