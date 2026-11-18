package fr.vinted.republisher.adapter.in.web;

import fr.vinted.republisher.adapter.in.web.api.model.BackupVerificationRefusalDto;
import fr.vinted.republisher.adapter.in.web.api.model.BackupVerificationRefusalReasonDto;
import fr.vinted.republisher.domain.session.BrowserBusy;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Turns the refusal of a batch verification into the status and body the contract declares. */
@RestControllerAdvice(assignableTypes = BackupVerificationApiController.class)
public class BackupVerificationRefusals {

    @ExceptionHandler
    ResponseEntity<BackupVerificationRefusalDto> busy(BrowserBusy refusal) {
        return ResponseEntity.status(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                .body(new BackupVerificationRefusalDto().reason(BackupVerificationRefusalReasonDto.BROWSER_BUSY));
    }
}
