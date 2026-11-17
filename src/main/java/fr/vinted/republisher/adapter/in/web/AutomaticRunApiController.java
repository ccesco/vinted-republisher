package fr.vinted.republisher.adapter.in.web;

import fr.vinted.republisher.adapter.in.web.api.AutomaticRunApi;
import fr.vinted.republisher.adapter.in.web.api.model.AutomaticRunOverviewDto;
import fr.vinted.republisher.adapter.in.web.api.model.RunStatusDto;
import fr.vinted.republisher.adapter.in.web.api.model.RunStopReasonDto;
import fr.vinted.republisher.application.run.LaunchAutomaticRun;
import fr.vinted.republisher.domain.run.AutomaticRun;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@RestController
public class AutomaticRunApiController implements AutomaticRunApi {

    private final LaunchAutomaticRun launchAutomaticRun;

    public AutomaticRunApiController(LaunchAutomaticRun launchAutomaticRun) {
        this.launchAutomaticRun = launchAutomaticRun;
    }

    @Override
    public ResponseEntity<AutomaticRunOverviewDto> showAutomaticRun() {
        return ResponseEntity.ok(toDto(launchAutomaticRun.overview()));
    }

    @Override
    public ResponseEntity<AutomaticRunOverviewDto> launchAutomaticRun() {
        launchAutomaticRun.launch();
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(toDto(launchAutomaticRun.overview()));
    }

    @Override
    public ResponseEntity<AutomaticRunOverviewDto> pauseAutomaticRun() {
        launchAutomaticRun.pause();
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(toDto(launchAutomaticRun.overview()));
    }

    @Override
    public ResponseEntity<AutomaticRunOverviewDto> resumeAutomaticRun() {
        launchAutomaticRun.resume();
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(toDto(launchAutomaticRun.overview()));
    }

    @Override
    public ResponseEntity<AutomaticRunOverviewDto> stopAutomaticRun() {
        launchAutomaticRun.stop();
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(toDto(launchAutomaticRun.overview()));
    }

    static AutomaticRunOverviewDto toDto(AutomaticRun run) {
        return new AutomaticRunOverviewDto()
                .status(RunStatusDto.valueOf(run.status().name()))
                .startedAt(run.startedAt().map(AutomaticRunApiController::utc).orElse(null))
                .endedAt(run.endedAt().map(AutomaticRunApiController::utc).orElse(null))
                .waitingUntil(run.waitingUntil().map(AutomaticRunApiController::utc).orElse(null))
                .currentListingId(run.currentListing().map(VintedListingId::value).orElse(null))
                .relaunchCount(run.relaunchCount())
                .skippedListingIds(run.skippedListings().stream().map(VintedListingId::value).toList())
                .stopReason(run.stopReason().map(reason -> RunStopReasonDto.valueOf(reason.name())).orElse(null))
                .pauseRequested(run.pauseRequested())
                .stopRequested(run.stopRequested());
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
