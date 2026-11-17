package fr.vinted.republisher.adapter.in.web;

import fr.vinted.republisher.adapter.in.web.api.WardrobeApi;
import fr.vinted.republisher.adapter.in.web.api.model.BackupProblemDto;
import fr.vinted.republisher.adapter.in.web.api.model.ScanProblemDto;
import fr.vinted.republisher.adapter.in.web.api.model.StatisticsRefreshProblemDto;
import fr.vinted.republisher.adapter.in.web.api.model.WardrobeOverviewDto;
import fr.vinted.republisher.application.backup.BackUpWardrobe;
import fr.vinted.republisher.application.backup.ShowBackup;
import fr.vinted.republisher.application.wardrobe.RefreshStatistics;
import fr.vinted.republisher.application.wardrobe.ScanWardrobe;
import fr.vinted.republisher.application.wardrobe.ShowStatisticsRefresh;
import fr.vinted.republisher.application.wardrobe.ShowWardrobe;
import fr.vinted.republisher.domain.backup.BackupOverview;
import fr.vinted.republisher.domain.wardrobe.StatisticsRefreshOverview;
import fr.vinted.republisher.domain.wardrobe.WardrobeOverview;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * Implements the contract-first API: scans, backups and refreshes of the statistics run in the background, the page
 * follows the overview.
 */
@RestController
public class WardrobeApiController implements WardrobeApi {

    private final ShowWardrobe showWardrobe;
    private final ScanWardrobe scanWardrobe;
    private final ShowBackup showBackup;
    private final BackUpWardrobe backUpWardrobe;
    private final ShowStatisticsRefresh showStatisticsRefresh;
    private final RefreshStatistics refreshStatistics;

    public WardrobeApiController(ShowWardrobe showWardrobe, ScanWardrobe scanWardrobe, ShowBackup showBackup,
                                 BackUpWardrobe backUpWardrobe, ShowStatisticsRefresh showStatisticsRefresh,
                                 RefreshStatistics refreshStatistics) {
        this.showWardrobe = showWardrobe;
        this.scanWardrobe = scanWardrobe;
        this.showBackup = showBackup;
        this.backUpWardrobe = backUpWardrobe;
        this.showStatisticsRefresh = showStatisticsRefresh;
        this.refreshStatistics = refreshStatistics;
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

    @Override
    public ResponseEntity<WardrobeOverviewDto> refreshStatistics() {
        refreshStatistics.refresh();
        return ResponseEntity.accepted().body(overview());
    }

    private WardrobeOverviewDto overview() {
        BackupOverview backup = showBackup.show();
        WardrobeOverview wardrobe = showWardrobe.show();
        StatisticsRefreshOverview refresh = showStatisticsRefresh.show();
        return new WardrobeOverviewDto()
                .scanInProgress(wardrobe.scanInProgress())
                .lastScanAt(dateOf(wardrobe.lastScanAt()))
                .problem(wardrobe.problem().map(problem -> ScanProblemDto.valueOf(problem.name())).orElse(null))
                .backupInProgress(backup.backupInProgress())
                .lastBackupAt(dateOf(backup.lastBackupAt()))
                .backupProblem(backup.problem().map(problem -> BackupProblemDto.valueOf(problem.name())).orElse(null))
                .statisticsRefreshInProgress(refresh.inProgress())
                .lastStatisticsReadAt(dateOf(refresh.lastReadAt()))
                .statisticsRefreshProblem(refresh.problem()
                        .map(problem -> StatisticsRefreshProblemDto.valueOf(problem.name())).orElse(null));
    }

    private static OffsetDateTime dateOf(Optional<Instant> instant) {
        return instant.map(date -> date.atOffset(ZoneOffset.UTC)).orElse(null);
    }
}
