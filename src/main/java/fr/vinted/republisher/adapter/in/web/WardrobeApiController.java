package fr.vinted.republisher.adapter.in.web;

import fr.vinted.republisher.adapter.in.web.api.WardrobeApi;
import fr.vinted.republisher.adapter.in.web.api.model.BackupProblemDto;
import fr.vinted.republisher.adapter.in.web.api.model.ListingDto;
import fr.vinted.republisher.adapter.in.web.api.model.ListingStatisticsDto;
import fr.vinted.republisher.adapter.in.web.api.model.PriceDto;
import fr.vinted.republisher.adapter.in.web.api.model.ScanProblemDto;
import fr.vinted.republisher.adapter.in.web.api.model.WardrobeOverviewDto;
import fr.vinted.republisher.application.backup.BackUpWardrobe;
import fr.vinted.republisher.application.backup.ShowBackup;
import fr.vinted.republisher.application.wardrobe.ScanWardrobe;
import fr.vinted.republisher.application.wardrobe.ShowWardrobe;
import fr.vinted.republisher.domain.backup.BackupOverview;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.StatisticsReading;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import fr.vinted.republisher.domain.wardrobe.WardrobeOverview;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Implements the contract-first API: scans and backups run in the background, the page follows the overview. */
@RestController
public class WardrobeApiController implements WardrobeApi {

    private final ShowWardrobe showWardrobe;
    private final ScanWardrobe scanWardrobe;
    private final ShowBackup showBackup;
    private final BackUpWardrobe backUpWardrobe;

    public WardrobeApiController(ShowWardrobe showWardrobe, ScanWardrobe scanWardrobe, ShowBackup showBackup,
                                 BackUpWardrobe backUpWardrobe) {
        this.showWardrobe = showWardrobe;
        this.scanWardrobe = scanWardrobe;
        this.showBackup = showBackup;
        this.backUpWardrobe = backUpWardrobe;
    }

    @Override
    public ResponseEntity<WardrobeOverviewDto> showWardrobe() {
        return ResponseEntity.ok(overview());
    }

    @Override
    public ResponseEntity<WardrobeOverviewDto> scanWardrobe() {
        scanWardrobe.scan();
        return ResponseEntity.accepted().body(overview());
    }

    @Override
    public ResponseEntity<WardrobeOverviewDto> backUpWardrobe() {
        backUpWardrobe.backUp();
        return ResponseEntity.accepted().body(overview());
    }

    /** The backup is read first, as each use case looks at its own action before its results. */
    private WardrobeOverviewDto overview() {
        BackupOverview backup = showBackup.show();
        WardrobeOverview wardrobe = showWardrobe.show();
        Map<VintedListingId, StatisticsReading> statistics = wardrobe.latestStatistics().stream()
                .collect(Collectors.toMap(StatisticsReading::listingId, Function.identity()));
        return new WardrobeOverviewDto()
                .scanInProgress(wardrobe.scanInProgress())
                .lastScanAt(dateOf(wardrobe.lastScanAt()))
                .problem(wardrobe.problem().map(problem -> ScanProblemDto.valueOf(problem.name())).orElse(null))
                .backupInProgress(backup.backupInProgress())
                .lastBackupAt(dateOf(backup.lastBackupAt()))
                .backupProblem(backup.problem().map(problem -> BackupProblemDto.valueOf(problem.name())).orElse(null))
                .listings(wardrobe.listings().stream().map(listing -> toDto(listing, backup, statistics)).toList());
    }

    private static ListingDto toDto(Listing listing, BackupOverview backup,
                                    Map<VintedListingId, StatisticsReading> statistics) {
        return new ListingDto()
                .vintedId(listing.id().value())
                .title(listing.title())
                .price(new PriceDto().amount(listing.price().amount()).currency(listing.price().currency()))
                .url(listing.url())
                .savedAt(dateOf(Optional.ofNullable(backup.savedAt().get(listing.id()))))
                .photoCount(backup.photoCount().get(listing.id()))
                .validatedAt(dateOf(Optional.ofNullable(backup.validatedAt().get(listing.id()))))
                .statistics(Optional.ofNullable(statistics.get(listing.id())).map(WardrobeApiController::statisticsOf)
                        .orElse(null));
    }

    private static ListingStatisticsDto statisticsOf(StatisticsReading reading) {
        return new ListingStatisticsDto()
                .viewCount(reading.statistics().viewCount())
                .favouriteCount(reading.statistics().favouriteCount())
                .readAt(reading.readAt().atOffset(ZoneOffset.UTC));
    }

    private static OffsetDateTime dateOf(Optional<Instant> instant) {
        return instant.map(date -> date.atOffset(ZoneOffset.UTC)).orElse(null);
    }
}
