package fr.vinted.republisher.adapter.in.web;

import fr.vinted.republisher.adapter.in.web.api.ListingDetailApi;
import fr.vinted.republisher.adapter.in.web.api.model.ListingDetailDto;
import fr.vinted.republisher.application.inventory.ShowListingDetail;
import fr.vinted.republisher.domain.inventory.ListingDetail;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneOffset;
import java.util.Optional;

/** Implements the contract-first API: everything the tool keeps about one listing. */
@RestController
public class ListingDetailApiController implements ListingDetailApi {

    private final ShowListingDetail showListingDetail;

    public ListingDetailApiController(ShowListingDetail showListingDetail) {
        this.showListingDetail = showListingDetail;
    }

    @Override
    public ResponseEntity<ListingDetailDto> showListingDetail(Long listingId) {
        ListingDetail detail = showListingDetail.show(new VintedListingId(listingId));
        return ResponseEntity.ok(new ListingDetailDto()
                .listing(ListingInventoryApiController.toDto(detail.entry()))
                .lastSeenAt(detail.entry().listing().lastSeenAt().atOffset(ZoneOffset.UTC))
                .statisticsHistory(detail.history().stream().map(ListingInventoryApiController::statisticsOf).toList())
                .journal(detail.journal().stream()
                        .map(entry -> ActionJournalApiController.toDto(entry, Optional.of(detail.entry().listing().title())))
                        .toList()));
    }
}
