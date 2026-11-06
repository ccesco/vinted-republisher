package fr.vinted.republisher.adapter.in.web;

import fr.vinted.republisher.adapter.in.web.api.WardrobeApi;
import fr.vinted.republisher.adapter.in.web.api.model.ListingDto;
import fr.vinted.republisher.adapter.in.web.api.model.PriceDto;
import fr.vinted.republisher.adapter.in.web.api.model.ScanProblemDto;
import fr.vinted.republisher.adapter.in.web.api.model.WardrobeOverviewDto;
import fr.vinted.republisher.application.wardrobe.ScanWardrobe;
import fr.vinted.republisher.application.wardrobe.ShowWardrobe;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.WardrobeOverview;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneOffset;

/** Implements the contract-first API: the scan runs in the background, the page follows the overview. */
@RestController
public class WardrobeApiController implements WardrobeApi {

    private final ShowWardrobe showWardrobe;
    private final ScanWardrobe scanWardrobe;

    public WardrobeApiController(ShowWardrobe showWardrobe, ScanWardrobe scanWardrobe) {
        this.showWardrobe = showWardrobe;
        this.scanWardrobe = scanWardrobe;
    }

    @Override
    public ResponseEntity<WardrobeOverviewDto> showWardrobe() {
        return ResponseEntity.ok(toDto(showWardrobe.show()));
    }

    @Override
    public ResponseEntity<WardrobeOverviewDto> scanWardrobe() {
        scanWardrobe.scan();
        return ResponseEntity.accepted().body(toDto(showWardrobe.show()));
    }

    private static WardrobeOverviewDto toDto(WardrobeOverview overview) {
        return new WardrobeOverviewDto()
                .scanInProgress(overview.scanInProgress())
                .lastScanAt(overview.lastScanAt().map(date -> date.atOffset(ZoneOffset.UTC)).orElse(null))
                .problem(overview.problem().map(problem -> ScanProblemDto.valueOf(problem.name())).orElse(null))
                .listings(overview.listings().stream().map(WardrobeApiController::toDto).toList());
    }

    private static ListingDto toDto(Listing listing) {
        return new ListingDto()
                .vintedId(listing.id().value())
                .title(listing.title())
                .price(new PriceDto().amount(listing.price().amount()).currency(listing.price().currency()))
                .url(listing.url());
    }
}
