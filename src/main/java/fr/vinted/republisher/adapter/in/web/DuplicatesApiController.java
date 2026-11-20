package fr.vinted.republisher.adapter.in.web;

import fr.vinted.republisher.adapter.in.web.api.DuplicatesApi;
import fr.vinted.republisher.adapter.in.web.api.model.DuplicateDeletionOutcomeDto;
import fr.vinted.republisher.adapter.in.web.api.model.DuplicateDeletionReportDto;
import fr.vinted.republisher.adapter.in.web.api.model.DuplicateGroupDto;
import fr.vinted.republisher.adapter.in.web.api.model.DuplicateGroupsDto;
import fr.vinted.republisher.adapter.in.web.api.model.DuplicateListingDto;
import fr.vinted.republisher.application.duplicate.DeleteDuplicate;
import fr.vinted.republisher.application.duplicate.DuplicateDeletionReport;
import fr.vinted.republisher.application.duplicate.DuplicatesView;
import fr.vinted.republisher.application.duplicate.ShowDuplicates;
import fr.vinted.republisher.domain.duplicate.DuplicateGroup;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneOffset;

/** Implements the contract-first API of the duplicates (US-47). */
@RestController
public class DuplicatesApiController implements DuplicatesApi {

    private final ShowDuplicates showDuplicates;
    private final DeleteDuplicate deleteDuplicate;

    public DuplicatesApiController(ShowDuplicates showDuplicates, DeleteDuplicate deleteDuplicate) {
        this.showDuplicates = showDuplicates;
        this.deleteDuplicate = deleteDuplicate;
    }

    @Override
    public ResponseEntity<DuplicateGroupsDto> showDuplicates() {
        DuplicatesView view = showDuplicates.show();
        return ResponseEntity.ok(new DuplicateGroupsDto()
                .groups(view.groups().groups().stream().map(group -> toDto(group, view)).toList())
                .deletionDryRun(view.dryRun()));
    }

    @Override
    public ResponseEntity<DuplicateDeletionReportDto> deleteDuplicate(Long listingId) {
        DuplicateDeletionReport report = deleteDuplicate.delete(new VintedListingId(listingId));
        return ResponseEntity.ok(new DuplicateDeletionReportDto()
                .outcome(DuplicateDeletionOutcomeDto.valueOf(report.outcome().name()))
                .twinListingId(report.twin().value()));
    }

    private static DuplicateGroupDto toDto(DuplicateGroup group, DuplicatesView view) {
        return new DuplicateGroupDto()
                .title(group.title())
                .price(ListingBackupApiController.toDto(group.price()))
                .listings(group.listings().stream().map(listing -> toDto(listing, view)).toList());
    }

    private static DuplicateListingDto toDto(Listing listing, DuplicatesView view) {
        return new DuplicateListingDto()
                .listingId(listing.id().value())
                .url(listing.url())
                .firstSeenAt(listing.firstSeenAt().atOffset(ZoneOffset.UTC))
                .backedUp(view.backedUp(listing.id()))
                .deletable(view.deletable(listing.id()));
    }
}
