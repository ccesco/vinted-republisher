package fr.vinted.republisher.adapter.in.web;

import fr.vinted.republisher.adapter.in.web.api.ListingInventoryApi;
import fr.vinted.republisher.adapter.in.web.api.model.BackupFilterDto;
import fr.vinted.republisher.adapter.in.web.api.model.InventorySortDto;
import fr.vinted.republisher.adapter.in.web.api.model.ListingDto;
import fr.vinted.republisher.adapter.in.web.api.model.ListingInventoryDto;
import fr.vinted.republisher.adapter.in.web.api.model.ListingStatisticsDto;
import fr.vinted.republisher.adapter.in.web.api.model.PresenceFilterDto;
import fr.vinted.republisher.adapter.in.web.api.model.PriceDto;
import fr.vinted.republisher.adapter.in.web.api.model.SortDirectionDto;
import fr.vinted.republisher.application.inventory.ShowListingInventory;
import fr.vinted.republisher.domain.backup.ListingBackup;
import fr.vinted.republisher.domain.inventory.BackupFilter;
import fr.vinted.republisher.domain.inventory.InventoryCriteria;
import fr.vinted.republisher.domain.inventory.InventoryEntry;
import fr.vinted.republisher.domain.inventory.InventorySort;
import fr.vinted.republisher.domain.inventory.PresenceFilter;
import fr.vinted.republisher.domain.inventory.SortDirection;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.StatisticsReading;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneOffset;
import java.util.List;

/** Implements the contract-first API: the inventory as the member filters, sorts and searches it. */
@RestController
public class ListingInventoryApiController implements ListingInventoryApi {

    private final ShowListingInventory showListingInventory;

    public ListingInventoryApiController(ShowListingInventory showListingInventory) {
        this.showListingInventory = showListingInventory;
    }

    @Override
    public ResponseEntity<ListingInventoryDto> showListingInventory(PresenceFilterDto presence, BackupFilterDto backup,
                                                                    String search, InventorySortDto sort,
                                                                    SortDirectionDto direction) {
        InventoryCriteria criteria = new InventoryCriteria(PresenceFilter.valueOf(presence.name()),
                BackupFilter.valueOf(backup.name()), search, InventorySort.valueOf(sort.name()),
                SortDirection.valueOf(direction.name()));
        return ResponseEntity.ok(new ListingInventoryDto().listings(
                showListingInventory.show(criteria).stream().map(ListingInventoryApiController::toDto).toList()));
    }

    private static ListingDto toDto(InventoryEntry entry) {
        Listing listing = entry.listing();
        return new ListingDto()
                .vintedId(listing.id().value())
                .title(listing.title())
                .price(new PriceDto().amount(listing.price().amount()).currency(listing.price().currency()))
                .url(listing.url())
                .present(listing.present())
                .savedAt(entry.backup().map(backup -> backup.savedAt().atOffset(ZoneOffset.UTC)).orElse(null))
                .photoCount(entry.backup().map(ListingBackup::photos).filter(photos -> !photos.isEmpty())
                        .map(List::size).orElse(null))
                .validatedAt(entry.validatedAt().map(date -> date.atOffset(ZoneOffset.UTC)).orElse(null))
                .statistics(entry.statistics().map(ListingInventoryApiController::statisticsOf).orElse(null));
    }

    private static ListingStatisticsDto statisticsOf(StatisticsReading reading) {
        return new ListingStatisticsDto()
                .viewCount(reading.statistics().viewCount())
                .favouriteCount(reading.statistics().favouriteCount())
                .readAt(reading.readAt().atOffset(ZoneOffset.UTC));
    }
}
