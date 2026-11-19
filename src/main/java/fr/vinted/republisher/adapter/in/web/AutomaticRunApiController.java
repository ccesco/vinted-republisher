package fr.vinted.republisher.adapter.in.web;

import fr.vinted.republisher.adapter.in.web.api.AutomaticRunApi;
import fr.vinted.republisher.adapter.in.web.api.model.AutomaticRunOverviewDto;
import fr.vinted.republisher.adapter.in.web.api.model.QueuedActionDto;
import fr.vinted.republisher.adapter.in.web.api.model.RelaunchActionDto;
import fr.vinted.republisher.adapter.in.web.api.model.RunQueueDto;
import fr.vinted.republisher.adapter.in.web.api.model.RunStatusDto;
import fr.vinted.republisher.adapter.in.web.api.model.RunStopReasonDto;
import fr.vinted.republisher.application.run.LaunchAutomaticRun;
import fr.vinted.republisher.application.run.ShowRunQueue;
import fr.vinted.republisher.domain.run.AutomaticRun;
import fr.vinted.republisher.domain.run.QueuedAction;
import fr.vinted.republisher.domain.run.RunQueue;
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
    private final ShowRunQueue showRunQueue;

    public AutomaticRunApiController(LaunchAutomaticRun launchAutomaticRun, ShowRunQueue showRunQueue) {
        this.launchAutomaticRun = launchAutomaticRun;
        this.showRunQueue = showRunQueue;
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

    @Override
    public ResponseEntity<RunQueueDto> showRunQueue() {
        RunQueue queue = showRunQueue.show();
        return ResponseEntity.ok(new RunQueueDto()
                .computedAt(utc(queue.computedAt()))
                .actions(queue.actions().stream().map(AutomaticRunApiController::toDto).toList()));
    }

    private static QueuedActionDto toDto(QueuedAction queued) {
        return new QueuedActionDto()
                .listingId(queued.listingId().value())
                .listingTitle(queued.title())
                .action(RelaunchActionDto.valueOf(queued.action().name()))
                .earliestAt(utc(queued.earliestAt()))
                .dryRun(queued.dryRun());
    }

    static AutomaticRunOverviewDto toDto(AutomaticRun run) {
        return new AutomaticRunOverviewDto()
                .status(RunStatusDto.valueOf(run.status().name()))
                .startedAt(run.startedAt().map(AutomaticRunApiController::utc).orElse(null))
                .endedAt(run.endedAt().map(AutomaticRunApiController::utc).orElse(null))
                .waitingUntil(run.waitingUntil().map(AutomaticRunApiController::utc).orElse(null))
                .currentListingIds(run.currentListings().stream().map(VintedListingId::value).toList())
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
