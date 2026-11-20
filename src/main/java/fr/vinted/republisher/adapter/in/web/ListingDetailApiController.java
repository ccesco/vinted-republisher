package fr.vinted.republisher.adapter.in.web;

import fr.vinted.republisher.adapter.in.web.api.ListingDetailApi;
import fr.vinted.republisher.adapter.in.web.api.model.LifecycleEventDto;
import fr.vinted.republisher.adapter.in.web.api.model.LifecycleEventTypeDto;
import fr.vinted.republisher.adapter.in.web.api.model.ListingDetailDto;
import fr.vinted.republisher.adapter.in.web.api.model.ListingSaleDto;
import fr.vinted.republisher.adapter.in.web.api.model.SaleOriginDto;
import fr.vinted.republisher.adapter.in.web.api.model.PriceChangeDto;
import fr.vinted.republisher.adapter.in.web.api.model.StatisticsComparisonDto;
import fr.vinted.republisher.adapter.in.web.api.model.VerificationResultDto;
import fr.vinted.republisher.application.duplicate.DuplicatesView;
import fr.vinted.republisher.application.duplicate.ShowDuplicates;
import fr.vinted.republisher.application.inventory.ShowListingDetail;
import fr.vinted.republisher.domain.backup.VerificationResult;
import fr.vinted.republisher.domain.inventory.LifecycleEvent;
import fr.vinted.republisher.domain.inventory.ListingDetail;
import fr.vinted.republisher.domain.inventory.StatisticsComparison;
import fr.vinted.republisher.domain.wardrobe.PriceChange;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneOffset;
import java.util.Optional;

/** Implements the contract-first API: everything the tool keeps about one listing. */
@RestController
public class ListingDetailApiController implements ListingDetailApi {

    private final ShowListingDetail showListingDetail;
    private final ShowDuplicates showDuplicates;

    public ListingDetailApiController(ShowListingDetail showListingDetail, ShowDuplicates showDuplicates) {
        this.showListingDetail = showListingDetail;
        this.showDuplicates = showDuplicates;
    }

    @Override
    public ResponseEntity<ListingDetailDto> showListingDetail(Long listingId) {
        VintedListingId id = new VintedListingId(listingId);
        ListingDetail detail = showListingDetail.show(id);
        DuplicatesView duplicates = showDuplicates.show();
        return ResponseEntity.ok(new ListingDetailDto()
                .listing(ListingInventoryApiController.toDto(detail.entry()))
                .firstSeenAt(detail.entry().listing().firstSeenAt().atOffset(ZoneOffset.UTC))
                .lastSeenAt(detail.entry().listing().lastSeenAt().atOffset(ZoneOffset.UTC))
                .statisticsHistory(detail.history().stream().map(ListingInventoryApiController::statisticsOf).toList())
                .journal(detail.journal().stream()
                        .map(entry -> ActionJournalApiController.toDto(entry, Optional.of(detail.entry().listing().title())))
                        .toList())
                .statisticsComparison(detail.comparison().map(ListingDetailApiController::toDto).orElse(null))
                .priceHistory(detail.priceHistory().stream().map(ListingDetailApiController::toDto).toList())
                .lifecycle(detail.lifecycle().events().stream().map(ListingDetailApiController::toDto).toList())
                .lastVerification(detail.lastVerification().map(ListingDetailApiController::toDto).orElse(null))
                .duplicateTwinIds(duplicates.twinsOf(id).stream().map(twin -> twin.id().value()).toList())
                .deletableAsDuplicate(duplicates.deletable(id))
                .deletionDryRun(duplicates.dryRun())
                .sale(detail.sale().map(sale -> new ListingSaleDto()
                        .origin(SaleOriginDto.valueOf(sale.origin().name()))
                        .noticedAt(sale.noticedAt().atOffset(ZoneOffset.UTC))).orElse(null)));
    }

    private static VerificationResultDto toDto(VerificationResult result) {
        return new VerificationResultDto()
                .verifiedAt(result.verifiedAt().atOffset(ZoneOffset.UTC))
                .passed(result.passed())
                .failures(result.failures().stream().map(ListingBackupApiController::toDto).toList());
    }

    private static PriceChangeDto toDto(PriceChange change) {
        return new PriceChangeDto()
                .price(ListingBackupApiController.toDto(change.price()))
                .changedAt(change.changedAt().atOffset(ZoneOffset.UTC));
    }

    private static StatisticsComparisonDto toDto(StatisticsComparison comparison) {
        return new StatisticsComparisonDto()
                .previousListingId(comparison.previousListing().value())
                .republishedAt(comparison.republishedAt().atOffset(ZoneOffset.UTC))
                .before(comparison.before().map(ListingInventoryApiController::statisticsOf).orElse(null))
                .after(comparison.after().map(ListingInventoryApiController::statisticsOf).orElse(null))
                .cumulatedViewCount(comparison.cumulated().viewCount())
                .cumulatedFavouriteCount(comparison.cumulated().favouriteCount())
                .previousListingCount(comparison.previousListingCount());
    }

    private static LifecycleEventDto toDto(LifecycleEvent event) {
        return new LifecycleEventDto()
                .type(LifecycleEventTypeDto.valueOf(event.type().name()))
                .listingId(event.listing().value())
                .at(event.at().atOffset(ZoneOffset.UTC))
                .newListingId(event.newListing().map(VintedListingId::value).orElse(null));
    }
}
