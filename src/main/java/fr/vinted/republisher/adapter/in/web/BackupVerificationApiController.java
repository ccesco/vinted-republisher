package fr.vinted.republisher.adapter.in.web;

import fr.vinted.republisher.adapter.in.web.api.BackupVerificationApi;
import fr.vinted.republisher.adapter.in.web.api.model.BackupVerificationProgressDto;
import fr.vinted.republisher.adapter.in.web.api.model.BackupVerificationStatusDto;
import fr.vinted.republisher.adapter.in.web.api.model.BackupVerificationStopReasonDto;
import fr.vinted.republisher.application.backup.VerifyAndValidateBackups;
import fr.vinted.republisher.domain.backup.BackupVerificationProgress;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Implements the contract-first API: the verification runs in the background, the page follows its progress. */
@RestController
public class BackupVerificationApiController implements BackupVerificationApi {

    private final VerifyAndValidateBackups verifyAndValidateBackups;

    public BackupVerificationApiController(VerifyAndValidateBackups verifyAndValidateBackups) {
        this.verifyAndValidateBackups = verifyAndValidateBackups;
    }

    @Override
    public ResponseEntity<BackupVerificationProgressDto> verifyAndValidateBackups() {
        verifyAndValidateBackups.launch();
        return ResponseEntity.accepted().body(toDto(verifyAndValidateBackups.progress()));
    }

    @Override
    public ResponseEntity<BackupVerificationProgressDto> showBackupVerification() {
        return ResponseEntity.ok(toDto(verifyAndValidateBackups.progress()));
    }

    private static BackupVerificationProgressDto toDto(BackupVerificationProgress progress) {
        return new BackupVerificationProgressDto()
                .status(BackupVerificationStatusDto.valueOf(progress.status().name()))
                .verifiedCount(progress.verifiedCount())
                .totalCount(progress.totalCount())
                .validatedCount(progress.validatedCount())
                .failedCount(progress.failedCount())
                .stopReason(progress.stopReason()
                        .map(reason -> BackupVerificationStopReasonDto.valueOf(reason.name())).orElse(null));
    }
}
